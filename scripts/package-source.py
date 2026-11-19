#!/usr/bin/env python3
"""Package current sources, wrapper, APK and validation reports; exclude local tools and keys."""
from pathlib import Path
import zipfile
from project_config import ROOT, APK_NAME, SOURCE_NAME

files = [ROOT / name for name in ['README.md', '.gitignore', 'settings.gradle.kts', 'build.gradle.kts',
         'gradle.properties', 'gradlew', 'gradlew.bat', 'app/build.gradle.kts', 'version.properties']]
for directory in ['app/src', 'gradle', 'docs', 'scripts']:
    files.extend(p for p in (ROOT / directory).rglob('*') if p.is_file() and '__pycache__' not in p.parts)
for name in [APK_NAME, APK_NAME + '.sha256', 'direct-build.log', 'apk-verification.txt', 'lint.txt', 'lint.xml',
             'build.log', 'device-test-blocker.txt', 'audit-build.log', 'audit-apk-verification.txt', 'audit-adb-status.txt',
             'audit-api25.txt', 'audit-api25-negative-control.txt', 'audit-lint.log']:
    path = ROOT / 'artifacts' / name
    if path.is_file():
        files.append(path)
for name in ['ui-audit-build.log', 'ui-audit-lint.log', 'ui-apk-verification.txt', 'ui-api25.txt', 'ui-device-test-status.txt', 'ui-lint.txt', 'ui-lint.xml']:
    path = ROOT / 'artifacts' / name
    if path.is_file():
        files.append(path)
for pattern in ['before-*.png', 'current-*.png']:
    files.extend((ROOT / 'artifacts/ui').glob(pattern))
archive = ROOT / 'artifacts' / SOURCE_NAME
with zipfile.ZipFile(archive, 'w', zipfile.ZIP_DEFLATED) as output:
    for path in sorted(set(files)):
        relative = path.relative_to(ROOT)
        assert not any(part in {'.tools', '.git', '.gradle-user-home', 'local.properties'} for part in relative.parts)
        assert path.suffix not in {'.keystore', '.jks'}
        output.write(path, Path('AnyDeskAutoConfirm') / relative)
with zipfile.ZipFile(archive) as output:
    assert output.testzip() is None
print(archive)
