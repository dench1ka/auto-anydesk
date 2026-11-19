#!/usr/bin/env python3
"""Check source configuration, wrapper checksum setting, and the supplied binary APK."""
import hashlib
import os
from pathlib import Path
import re
import subprocess
import xml.etree.ElementTree as ET
import zipfile
import struct
import zlib
from project_config import VERSION_NAME, VERSION_CODE, APK_NAME

root = Path(__file__).resolve().parents[1]
ns = '{http://schemas.android.com/apk/res/android}'
manifest = ET.parse(root / 'app/src/main/AndroidManifest.xml').getroot()
assert not manifest.findall('uses-permission'), 'Unexpected requested permission'
assert not manifest.findall('./application/receiver'), 'Unexpected receiver'
services = manifest.findall('./application/service')
assert len(services) == 1
service = services[0]
assert service.get(ns + 'permission') == 'android.permission.BIND_ACCESSIBILITY_SERVICE'
assert service.get(ns + 'exported') == 'true'
assert service.find('meta-data').get(ns + 'resource') == '@xml/accessibility_service'
config = ET.parse(root / 'app/src/main/res/xml/accessibility_service.xml').getroot()
assert config.get(ns + 'canRetrieveWindowContent') == 'true'
assert set(config.get(ns + 'packageNames').split(',')) == {'com.android.systemui', 'com.anydesk.anydeskandroid'}
assert 'flagReportViewIds' in config.get(ns + 'accessibilityFlags')
assert {'typeWindowStateChanged', 'typeWindowContentChanged'} <= set(config.get(ns + 'accessibilityEventTypes').split('|'))
wrapper = (root / 'gradle/wrapper/gradle-wrapper.properties').read_text()
assert 'gradle-8.11.1-bin.zip' in wrapper
assert re.search(r'distributionSha256Sum=[0-9a-f]{64}', wrapper)
with zipfile.ZipFile(root / 'gradle/wrapper/gradle-wrapper.jar') as archive:
    assert 'org/gradle/wrapper/GradleWrapperMain.class' in archive.namelist()
apk = root / 'artifacts' / APK_NAME
bt = Path(os.environ['ANDROID_HOME']) / 'build-tools/35.0.0'
badging = subprocess.check_output([str(bt / 'aapt'), 'dump', 'badging', str(apk)], text=True)
assert "name='ru.denis.anydeskconfirm'" in badging
assert "sdkVersion:'25'" in badging
assert f"versionCode='{VERSION_CODE}'" in badging
assert f"versionName='{VERSION_NAME}'" in badging
assert 'uses-permission:' not in badging
xml = subprocess.check_output([str(bt / 'aapt'), 'dump', 'xmltree', str(apk), 'AndroidManifest.xml'], text=True)
assert 'android.permission.BIND_ACCESSIBILITY_SERVICE' in xml
assert 'android.accessibilityservice.AccessibilityService' in xml
assert 'AnyDeskAccessibilityService' in xml
with zipfile.ZipFile(apk) as archive:
    assert archive.testzip() is None, 'ZIP CRC error'
    assert len(archive.namelist()) == len(set(archive.namelist())), 'Duplicate ZIP entries'
    assert any(name.startswith('META-INF/') and name.endswith('.RSA') for name in archive.namelist()), 'v1 signature missing'
    assert 'classes.dex' in archive.namelist()
    for name in archive.namelist():
        if name.endswith('.dex'):
            dex = archive.read(name)
            assert dex[:8] in {b'dex\n035\0', b'dex\n037\0'}, 'DEX version requires Android newer than 7'
            assert struct.unpack_from('<I', dex, 32)[0] == len(dex), 'Wrong DEX file size'
            assert dex[12:32] == hashlib.sha1(dex[32:]).digest(), 'DEX SHA-1 mismatch'
            assert struct.unpack_from('<I', dex, 8)[0] == zlib.adler32(dex[12:]) & 0xffffffff, 'DEX checksum mismatch'
    assert 'res/xml/accessibility_service.xml' in archive.namelist()
    # No arbitrary native code, network assets, cloud config or bundled external apps.
    assert not any(name.startswith('lib/') for name in archive.namelist())
subprocess.run([str(bt / 'apksigner'), 'verify', '--verbose', '--min-sdk-version', '25', str(apk)], check=True)
subprocess.run([str(bt / 'apksigner'), 'verify', '--verbose', '--min-sdk-version', '25', '--max-sdk-version', '25', str(apk)], check=True)
# API 23 forces JAR/v1 verification only. This checks the signature, not installability on API 23.
subprocess.run([str(bt / 'apksigner'), 'verify', '--verbose', '--min-sdk-version', '23', '--max-sdk-version', '23', str(apk)], check=True)
subprocess.run([str(bt / 'zipalign'), '-c', '4', str(apk)], check=True)
digest = hashlib.sha256(apk.read_bytes()).hexdigest()
apk.with_suffix('.apk.sha256').write_text(f'{digest}  {apk.name}\n')
print(badging)
print('Configuration and APK checks passed. SHA-256:', digest)
