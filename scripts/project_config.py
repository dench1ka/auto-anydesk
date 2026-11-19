"""Shared, explicit app version for SDK packaging and publishing tools."""
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
VERSION = dict(line.strip().split('=', 1) for line in (ROOT / 'version.properties').read_text().splitlines()
               if line.strip() and not line.lstrip().startswith('#'))
VERSION_NAME = VERSION['versionName']
VERSION_CODE = int(VERSION['versionCode'])
APK_NAME = f'AnyDeskAutoConfirm-{VERSION_NAME}-debug.apk'
SOURCE_NAME = f'AnyDeskAutoConfirm-{VERSION_NAME}-source.zip'
