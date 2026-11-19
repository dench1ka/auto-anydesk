#!/usr/bin/env python3
"""Publish prepared sources and the built APK using an authenticated GitHub CLI.

Run outside restricted environments: python3 scripts/publish-github.py
Use --prepare-only to inspect exactly what will be uploaded, without network calls.
"""
import argparse
import hashlib
from pathlib import Path
import shutil
import subprocess
import tempfile
from project_config import APK_NAME, SOURCE_NAME, VERSION_NAME

root = Path(__file__).resolve().parents[1]
parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--prepare-only', action='store_true')
parser.add_argument('--repo', help='New repository owner/name; defaults to signed-in-user/auto-anydesk')
args = parser.parse_args()
apk = root / 'artifacts' / APK_NAME
source_zip = root / 'artifacts' / SOURCE_NAME
expected = (apk.with_suffix('.apk.sha256')).read_text().split()[0]
if hashlib.sha256(apk.read_bytes()).hexdigest() != expected:
    raise SystemExit('APK checksum mismatch; publication cancelled.')

files = [root / name for name in ['.gitignore', 'README.md', 'settings.gradle.kts', 'build.gradle.kts',
         'gradle.properties', 'gradlew', 'gradlew.bat', 'app/build.gradle.kts', 'version.properties']]
for name in ['app/src', 'gradle', 'docs', 'scripts']:
    files.extend(p for p in (root / name).rglob('*') if p.is_file() and '__pycache__' not in p.parts)
files.extend([apk, apk.with_suffix('.apk.sha256'), source_zip])
for name in ['direct-build.log', 'apk-verification.txt', 'lint.txt', 'lint.xml', 'build.log', 'device-test-blocker.txt']:
    files.append(root / 'artifacts' / name)
for name in ['audit-build.log', 'audit-apk-verification.txt', 'audit-adb-status.txt',
             'audit-api25.txt', 'audit-api25-negative-control.txt', 'audit-lint.log']:
    if (root / 'artifacts' / name).exists():
        files.append(root / 'artifacts' / name)

def prepare(directory):
    for source in sorted(set(files)):
        relative = source.relative_to(root)
        assert not any(part in {'.tools', '.git', '.gradle-user-home', 'local.properties', '__pycache__'} for part in relative.parts)
        assert source.suffix not in {'.keystore', '.jks'}, 'Signing key must never be uploaded'
        target = directory / relative
        target.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(source, target)

if args.prepare_only:
    # The payload lives outside source directories and is never selected recursively.
    target = root / 'artifacts/github-upload'
    if target.exists():
        shutil.rmtree(target)
    target.mkdir()
    prepare(target)
    print(f'Prepared {len(set(files))} files in {target}')
    print('APK SHA-256:', expected)
    print('No network requests made; nothing uploaded.')
    raise SystemExit(0)

def capture(command, cwd=None):
    return subprocess.check_output(command, cwd=cwd, text=True).strip()

def run(command, cwd=None):
    subprocess.run(command, cwd=cwd, check=True)

if not shutil.which('gh') or not shutil.which('git'):
    raise SystemExit('Install GitHub CLI and git, then run gh auth login first.')
login = capture(['gh', 'api', 'user', '--jq', '.login'])
user_id = capture(['gh', 'api', 'user', '--jq', '.id'])
repo = args.repo or f'{login}/auto-anydesk'
if repo.count('/') != 1 or repo.startswith('-'):
    raise SystemExit('Expected repository owner/name')
existing = subprocess.run(['gh', 'repo', 'view', repo], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
if existing.returncode == 0:
    raise SystemExit(f'{repo} already exists. Its history was left unchanged; choose a new name with --repo.')

notes = f'''AnyDesk Auto Confirm {VERSION_NAME} — personal debug build.

Android minSdk 25. Includes APK, SHA-256 and a source archive.
Validation results are documented in docs/AUDIT-1.0.1.md and docs/VALIDATION.md.
Built directly with Android SDK tools because Gradle/ADB sockets were blocked in the build environment.
Physical Xiaomi / MIUI 10 testing, screen-off operation and reboot recovery remain unverified.
'''
with tempfile.TemporaryDirectory(prefix='anydesk-github-') as temporary:
    stage = Path(temporary)
    prepare(stage)
    run(['git', 'init', '-b', 'main'], stage)
    run(['git', 'add', '.'], stage)
    run(['git', '-c', f'user.name={login}', '-c', f'user.email={user_id}+{login}@users.noreply.github.com',
         'commit', '-m', 'Implement AnyDesk Auto Confirm for Android API 25'], stage)
    run(['gh', 'repo', 'create', repo, '--private', '--description',
         'AnyDesk screen capture confirmation helper for personal Android API 25 devices',
         '--source', str(stage), '--remote', 'origin', '--push'], stage)
    notes_file = stage / 'release-notes.md'
    notes_file.write_text(notes)
    run(['gh', 'release', 'create', f'v{VERSION_NAME}', '--repo', repo, '--target', 'main',
         '--title', f'AnyDesk Auto Confirm {VERSION_NAME} — Debug', '--notes-file', str(notes_file),
         str(apk), str(apk.with_suffix('.apk.sha256')), str(source_zip)], stage)
    print(capture(['gh', 'release', 'view', f'v{VERSION_NAME}', '--repo', repo, '--json', 'url', '--jq', '.url']))
