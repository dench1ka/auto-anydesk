#!/usr/bin/env python3
"""Standalone Android Lint for the offline SDK fallback (run after build-with-sdk.py)."""
import os
from pathlib import Path
import shutil
import subprocess
import xml.etree.ElementTree as ET

root = Path(__file__).resolve().parents[1]
cache = Path(os.environ.get('GRADLE_USER_HOME', root / '.gradle-user-home')) / 'caches/modules-2/files-2.1'
sdk = Path(os.environ['ANDROID_HOME'])
jdk = Path(os.environ['JAVA_HOME'])
project = root / 'app/build/direct/lint-project'
project.mkdir(exist_ok=True)
ET.register_namespace('android', 'http://schemas.android.com/apk/res/android')
manifest = ET.parse(root / 'app/build/direct/AndroidManifest.xml')
# debuggable is added by the build script, never hardcoded in application sources.
manifest.getroot().find('application').attrib.pop('{http://schemas.android.com/apk/res/android}debuggable', None)
manifest.write(project / 'AndroidManifest.xml', encoding='utf-8', xml_declaration=True)
(project / 'project.properties').write_text('target=android-35\n')
jars = sorted(cache.glob('*/*/*/*/*.jar'))
stdlib = next((cache / 'org.jetbrains.kotlin/kotlin-stdlib/2.1.20').glob('*/*.jar'))
cmd = [jdk / 'bin/java', '-Xmx2g', '-Djava.awt.headless=true', '-cp', os.pathsep.join(str(x) for x in jars),
       'com.android.tools.lint.Main', '--offline', '--exitcode', '--sdk-home', sdk, '--jdk-home', jdk,
       '--compile-sdk-version', '35', '--sources', root / 'app/src/main/java', '--resources', root / 'app/src/main/res',
       '--classpath', root / 'app/build/direct/classes', '--libraries', stdlib,
       '--text', root / 'artifacts/lint.txt', '--xml', root / 'artifacts/lint.xml', project]
raise SystemExit(subprocess.run([str(x) for x in cmd], cwd=root).returncode)
