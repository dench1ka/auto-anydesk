#!/usr/bin/env python3
"""Offline fallback using real SDK tools when Gradle cannot open local sockets.

Needs JDK 17, SDK 35/build-tools 35.0.0 and an already populated Gradle cache.
No downloads, daemon, code generation substitutes or changes to third-party tools.
The normal supported build remains ./gradlew testDebugUnitTest lintDebug assembleDebug.
"""
import argparse
import hashlib
import os
from pathlib import Path
import shutil
import subprocess
import sys
import xml.etree.ElementTree as ET
import zipfile
from project_config import VERSION_NAME, VERSION_CODE, APK_NAME

ROOT = Path(__file__).resolve().parents[1]
os.chdir(ROOT)
parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--tests', action='store_true')
parser.add_argument('--test-filter', help='Run matching test classes (for focused UI diagnostics)')
parser.add_argument('--capture-prefix', default='current', help='Prefix for UI rendering artifacts')
args = parser.parse_args()
sdk = Path(os.environ['ANDROID_HOME'])
jdk = Path(os.environ['JAVA_HOME'])
cache = Path(os.environ.get('GRADLE_USER_HOME', ROOT / '.gradle-user-home')) / 'caches/modules-2/files-2.1'
out = ROOT / 'app/build/direct'
out.mkdir(parents=True, exist_ok=True)
bt = sdk / 'build-tools/35.0.0'
android = sdk / 'platforms/android-35/android.jar'
java = jdk / 'bin/java'

def run(command):
    preview = ' '.join(str(x) for x in command[1:4])
    print('+', Path(str(command[0])).name, preview[:180], flush=True)
    try:
        subprocess.run([str(x) for x in command], check=True)
    except subprocess.CalledProcessError as error:
        raise SystemExit(f'{Path(str(command[0])).name} failed with exit code {error.returncode}; see output above.') from None

def jar(group, artifact, version):
    matches = list((cache / group / artifact / version).glob('*/*.jar'))
    if len(matches) != 1:
        raise RuntimeError(f'Expected one cached jar: {group}:{artifact}:{version}, got {matches}')
    return matches[0]

def cp(paths):
    return os.pathsep.join(str(p) for p in paths)

stdlib = jar('org.jetbrains.kotlin', 'kotlin-stdlib', '2.1.20')
annotations = jar('org.jetbrains', 'annotations', '13.0')
compiler = [jar('org.jetbrains.kotlin', x, '2.1.20') for x in
            ('kotlin-compiler-embeddable', 'kotlin-script-runtime', 'kotlin-daemon-embeddable')]
compiler += [stdlib, annotations, jar('org.jetbrains.kotlin', 'kotlin-reflect', '1.6.10'),
             jar('org.jetbrains.intellij.deps', 'trove4j', '1.0.20200330'),
             jar('org.jetbrains.kotlinx', 'kotlinx-coroutines-core-jvm', '1.8.0')]

def kotlin(sources, target, dependencies):
    target.mkdir(parents=True, exist_ok=True)
    run([java, '-Xmx2g', '-cp', cp(compiler), 'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler',
         '-no-stdlib', '-no-reflect', '-jvm-target', '17', '-classpath', cp(dependencies),
         '-d', target, *sources])

ET.register_namespace('android', 'http://schemas.android.com/apk/res/android')
manifest = ET.parse(ROOT / 'app/src/main/AndroidManifest.xml')
manifest.getroot().set('package', 'ru.denis.anydeskconfirm')
ns = '{http://schemas.android.com/apk/res/android}'
manifest.getroot().set(ns + 'versionCode', str(VERSION_CODE))
manifest.getroot().set(ns + 'versionName', VERSION_NAME)
manifest.getroot().insert(0, ET.Element('uses-sdk', {ns + 'minSdkVersion': '25', ns + 'targetSdkVersion': '35'}))
manifest.getroot().find('application').set(ns + 'debuggable', 'true')
manifest_path = out / 'AndroidManifest.xml'
manifest.write(manifest_path, encoding='utf-8', xml_declaration=True)
resources = out / 'resources.zip'
run([bt / 'aapt2', 'compile', '--dir', ROOT / 'app/src/main/res', '-o', resources])
rjava = out / 'generated'
rjava.mkdir(exist_ok=True)
resource_apk = out / 'resources.ap_'
run([bt / 'aapt2', 'link', '-I', android, '--manifest', manifest_path, '--java', rjava,
     '-o', resource_apk, resources])
classes = out / 'classes'
if classes.exists():
    shutil.rmtree(classes)
classes.mkdir()
run([jdk / 'bin/javac', '-source', '17', '-target', '17', '-d', classes, *rjava.rglob('*.java')])
kotlin(sorted((ROOT / 'app/src/main/java').rglob('*.kt')), classes, [android, classes, stdlib, annotations])
classes_jar = out / 'classes.jar'
with zipfile.ZipFile(classes_jar, 'w', zipfile.ZIP_DEFLATED) as archive:
    for file in sorted(classes.rglob('*')):
        if file.is_file():
            archive.write(file, file.relative_to(classes))
dex = out / 'dex'
if dex.exists():
    shutil.rmtree(dex)
dex.mkdir()
# Use AGP's D8, which supports Kotlin 2.1 metadata. SDK 35's bundled D8 is older.
run([java, '-cp', jar('com.android.tools.build', 'builder', '8.9.2'), 'com.android.tools.r8.D8',
     '--debug', '--min-api', '25', '--lib', android, '--output', dex,
     classes_jar, stdlib, annotations])
unsigned = out / 'unsigned.apk'
shutil.copyfile(resource_apk, unsigned)
with zipfile.ZipFile(unsigned, 'a', zipfile.ZIP_DEFLATED) as archive:
    for file in sorted(dex.glob('*.dex')):
        archive.write(file, file.name)
aligned = out / 'aligned.apk'
run([bt / 'zipalign', '-f', '-p', '4', unsigned, aligned])
# Separate local key: no reading or modifying keys belonging to other projects.
key = ROOT / '.tools/debug.keystore'
key.parent.mkdir(exist_ok=True)
if not key.exists():
    run([jdk / 'bin/keytool', '-genkeypair', '-keystore', key, '-storepass', 'android',
         '-keypass', 'android', '-alias', 'androiddebugkey', '-dname', 'CN=Android Debug,O=Android,C=US',
         '-keyalg', 'RSA', '-keysize', '2048', '-validity', '10000'])
apk = ROOT / 'artifacts' / APK_NAME
apk.parent.mkdir(exist_ok=True)
run([bt / 'apksigner', 'sign', '--ks', key, '--ks-pass', 'pass:android', '--key-pass', 'pass:android',
     '--v1-signing-enabled', 'true', '--v2-signing-enabled', 'true', '--v3-signing-enabled', 'true',
     '--v4-signing-enabled', 'false',
     '--out', apk, aligned])
run([bt / 'apksigner', 'verify', '--verbose', '--min-sdk-version', '25', apk])
run([bt / 'zipalign', '-c', '4', apk])
print('APK:', apk)
checksum = hashlib.sha256(apk.read_bytes()).hexdigest()
apk.with_suffix('.apk.sha256').write_text(f'{checksum}  {apk.name}\n')

if args.tests:
    # This fallback intentionally uses only cached runtime dependencies, never AGP/compiler jars.
    groups = {'org.robolectric', 'org.ow2.asm', 'junit', 'org.hamcrest', 'javax.inject', 'javax.annotation',
              'com.google.guava', 'com.google.errorprone', 'com.google.j2objc', 'com.google.code.findbugs',
              'org.checkerframework', 'org.bouncycastle', 'org.conscrypt', 'org.yaml',
              'com.almworks.sqlite4java', 'com.ibm.icu', 'com.google.auto.value', 'com.google.testparameterinjector'}
    dependencies = [p for p in sorted(cache.glob('*/*/*/*/*.jar')) if p.relative_to(cache).parts[0] in groups]
    dependencies += [stdlib, annotations]
    aar_dir = out / 'test-aar'
    aar_dir.mkdir(exist_ok=True)
    for group, name in [('androidx.test', 'monitor'), ('androidx.test.espresso', 'espresso-idling-resource')]:
        for aar in (cache / group / name).glob('*/*/*.aar'):
            target = aar_dir / f'{name}.jar'
            with zipfile.ZipFile(aar) as archive:
                target.write_bytes(archive.read('classes.jar'))
            dependencies.append(target)
    test_classes = out / 'test-classes'
    if test_classes.exists():
        shutil.rmtree(test_classes)
    kotlin(sorted((ROOT / 'app/src/test/java').rglob('*.kt')), test_classes, [classes, android, *dependencies])
    device_dependencies = []
    for group, name in [('androidx.test', 'core'), ('androidx.test', 'runner'), ('androidx.test.ext', 'junit')]:
        for aar in (cache / group / name).glob('*/*/*.aar'):
            target = aar_dir / f'{name}-device.jar'
            with zipfile.ZipFile(aar) as archive:
                target.write_bytes(archive.read('classes.jar'))
            device_dependencies.append(target)
    kotlin(sorted((ROOT / 'app/src/androidTest/java').rglob('*.kt')), out / 'device-test-classes',
           [classes, android, *dependencies, *device_dependencies])
    print('Instrumentation test sources compiled (not executed).', flush=True)
    config = test_classes / 'com/android/tools/test_config.properties'
    config.parent.mkdir(parents=True, exist_ok=True)
    assets = out / 'assets'
    assets.mkdir(exist_ok=True)
    config.write_text(f'android_merged_assets={assets}\nandroid_resource_apk={resource_apk}\n'
                      f'android_custom_package=ru.denis.anydeskconfirm\nandroid_merged_manifest={manifest_path}\n')
    tests = ['ru.denis.anydeskconfirm.' + p.stem for p in sorted((ROOT / 'app/src/test/java').rglob('*Test.kt'))]
    if args.test_filter:
        tests = [name for name in tests if args.test_filter in name]
        if not tests:
            raise SystemExit('No test classes matched --test-filter')
    sdk_jars = out / 'robolectric-sdks'
    sdk_jars.mkdir(exist_ok=True)
    for file in (ROOT / '.tools/robolectric-m2').rglob('*.jar'):
        link = sdk_jars / file.name
        if not link.exists():
            link.symlink_to(file)
    run([java, '-Xmx2g', '-Djava.awt.headless=true', '-Drobolectric.offline=true',
         f'-Dui.capturePrefix={args.capture_prefix}',
         f'-Dui.outputDir={ROOT}/artifacts/ui',
         f'-Drobolectric.dependency.dir={sdk_jars}',
         '-Drobolectric.enabledSdks=25,35', '-cp', cp([test_classes, classes, *dependencies, android]),
         'org.junit.runner.JUnitCore', *tests])
