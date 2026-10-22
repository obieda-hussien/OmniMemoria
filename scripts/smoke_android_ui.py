#!/usr/bin/env python3
"""Exercise the optimized APK and native OCR on an API-30 AOSP emulator.

Only a synthetic fixture is pushed to the emulator; no user photos are used.
"""
import re
import hashlib
import struct
import subprocess
import time
from pathlib import Path
from xml.etree import ElementTree as ET

PACKAGE = 'com.omnimemoria'
OUTPUT = Path('device-smoke')
OUTPUT.mkdir(exist_ok=True)


def adb(*args, check=True):
    return subprocess.run(['adb', *args], check=check, capture_output=True, text=True, timeout=45).stdout


def alive():
    if not adb('shell', 'pidof', PACKAGE, check=False).strip():
        raise AssertionError('Application process exited; see logcat-crash.txt')


def animations(enabled):
    for name in ('window_animation_scale', 'transition_animation_scale', 'animator_duration_scale'):
        adb('shell', 'settings', 'put', 'global', name, '1' if enabled else '0')


def tree():
    alive()
    adb('shell', 'uiautomator', 'dump', '/sdcard/omni-ui.xml')
    raw = adb('shell', 'cat', '/sdcard/omni-ui.xml')
    (OUTPUT / 'last-window.xml').write_text(raw)
    return ET.fromstring(raw)


def find(label, partial=False):
    nodes = list(tree().iter('node'))
    for attribute in ('content-desc', 'text'):
        for node in reversed(nodes):
            value = node.get(attribute, '')
            if (label in value if partial else value == label):
                bounds = list(map(int, re.findall(r'\d+', node.get('bounds', ''))))
                if len(bounds) == 4 and bounds[2] > bounds[0] and bounds[3] > bounds[1]:
                    return node, bounds
    return None


def wait(label, seconds=30, partial=False):
    end = time.monotonic() + seconds
    while time.monotonic() < end:
        result = find(label, partial)
        if result:
            return result
        time.sleep(0.6)
    raise AssertionError(f'Missing UI: {label}')


def tap(label, partial=False):
    _, (left, top, right, bottom) = wait(label, partial=partial)
    # Exercise the real animated transition, then disable animations only so
    # UIAutomator can become idle even on screens with an infinite pulse.
    animations(True)
    adb('shell', 'input', 'tap', str((left + right)//2), str((top + bottom)//2))
    time.sleep(1.2)
    alive()
    animations(False)


def swipe(up=True):
    width, height = map(int, re.findall(r'(\d+)x(\d+)', adb('shell', 'wm', 'size'))[-1])
    start, end = (0.78, 0.28) if up else (0.28, 0.78)
    adb('shell', 'input', 'swipe', str(width//2), str(int(height*start)), str(width//2), str(int(height*end)), '350')
    time.sleep(0.3)


def scroll_to(label, partial=False, limit=10):
    for _ in range(limit):
        if find(label, partial):
            return
        swipe()
    raise AssertionError(f'Missing scroll target: {label}')


def feature_checked(label):
    for node in tree().iter('node'):
        if node.get('checkable') == 'true' and any(
                child.get('text') == label for child in node.iter('node')):
            return node.get('checked') == 'true'
    return False


def wait_checked(label, seconds=15):
    end = time.monotonic() + seconds
    while time.monotonic() < end:
        if feature_checked(label):
            return
        time.sleep(0.3)
    raise AssertionError('Preference did not persist: '+label)


def capture(name):
    alive()
    result = subprocess.run(['adb', 'exec-out', 'screencap', '-p'], check=True, capture_output=True)
    (OUTPUT / f'{name}.png').write_bytes(result.stdout)
    (OUTPUT / f'{name}.xml').write_text(ET.tostring(tree(), encoding='unicode'))


def back():
    animations(True)
    adb('shell', 'input', 'keyevent', '4')
    time.sleep(1.2)
    alive()
    animations(False)


def home():
    adb('shell', 'am', 'force-stop', PACKAGE)
    animations(True)
    adb('shell', 'am', 'start', '-W', '-n', PACKAGE+'/.MainActivity')
    time.sleep(1.2)
    animations(False)
    wait('All Media', seconds=45)


try:
    apk = next(Path('preview-apks').rglob('*x86_64*preview.apk'))
    adb('install', '-r', str(apk))
    adb('logcat', '-c')
    adb('shell', 'appops', 'set', '--uid', PACKAGE, 'MANAGE_EXTERNAL_STORAGE', 'allow')
    # Match the app's actual Android-11 All files access flow. Do not mask
    # permission bugs by granting an extra runtime permission behind its UI.
    (OUTPUT / 'device.txt').write_text(adb('shell', 'getprop', 'ro.build.fingerprint') +
        '\nGoogle Play Services packages: '+adb('shell', 'pm', 'list', 'packages', 'com.google.android.gms'))
    assert not adb('shell', 'pm', 'list', 'packages', 'com.google.android.gms').strip(), 'Expected AOSP without GMS'
    # MediaProvider's SELinux domain cannot follow /sdcard on this AOSP image.
    # Scan the actual file synchronously through the canonical volume path;
    # inserting a second row after pushing creates an empty duplicate instead.
    fixture = '/storage/emulated/0/Pictures/omni-ocr-check.png'
    adb('shell', 'mkdir', '-p', '/storage/emulated/0/Pictures')
    adb('push', 'scripts/fixtures/omni-ocr-check.png', fixture)
    local_fixture = Path('scripts/fixtures/omni-ocr-check.png').read_bytes()
    assert struct.unpack('>II', local_fixture[16:24]) == (1080, 1440)
    checksum = adb('shell', 'sha256sum', fixture)
    (OUTPUT / 'fixture-checksum.txt').write_text(checksum)
    assert checksum.split()[0] == hashlib.sha256(local_fixture).hexdigest(), checksum
    scan = adb('shell', 'content', 'call', '--uri', 'content://media',
        '--method', 'scan_file', '--arg', fixture)
    (OUTPUT / 'fixture-scan.txt').write_text(scan)
    media = adb('shell', 'content', 'query', '--uri', 'content://media/external/images/media',
        '--projection', '_id:_display_name:_data:_size:width:height:is_pending')
    (OUTPUT / 'fixture-media.txt').write_text(media)
    fixture_rows = [line for line in media.splitlines() if '_display_name=omni-ocr-check.png,' in line]
    assert len(fixture_rows) == 1, media
    # Android 11's scanner reads dimensions from EXIF; a valid PNG without
    # EXIF can have NULL width/height. Verify actual bytes and the scanned row,
    # then let image rendering/native OCR verify decoding below.
    assert all(value in fixture_rows[0] for value in
        ('_size='+str(len(local_fixture)), '_data='+fixture, 'is_pending=0')), media

    # Regression for the reported preferences_ reflection crash. A fresh
    # install alone never parses a nonempty protobuf file. Write through the
    # real Settings UI, then kill the process and read it on a cold launch.
    home(); tap('Settings'); scroll_to('Hidden Photo Notes')
    tap('Hidden Photo Notes'); wait_checked('Hidden Photo Notes')
    capture('preferences-written')
    home(); tap('Settings'); scroll_to('Hidden Photo Notes')
    wait_checked('Hidden Photo Notes'); capture('preferences-read-after-restart')
    tap('Back')

    for mode in ('no', 'yes'):
        adb('shell', 'cmd', 'uimode', 'night', mode)
        home()
        tap('Albums'); wait('Albums'); tap('Gallery')
        tap('Library Tools'); tap('Favorites'); wait('No favorites yet'); back()
        tap('Library Tools'); tap('Recycle Bin'); wait('Recycle Bin is empty'); back()
        tap('Search'); wait('Quick Filters'); capture(f'search-{mode}')
        tap('Vault'); wait('Vault is disabled'); capture(f'vault-{mode}')
        tap('Settings'); wait('Scan library now'); capture(f'settings-{mode}')
        tap('Back')
        tap('Gallery'); tap('omni-ocr-check.png')
        tap('Info'); wait('Media details'); capture(f'details-{mode}')
        back()
        back(); wait('All Media')

    tap('Settings')
    scroll_to('Arabic + English OCR')
    if not feature_checked('Arabic + English OCR'):
        tap('Arabic + English OCR')
        wait_checked('Arabic + English OCR')
    scroll_to('Image Classification'); 
    if not feature_checked('Image Classification'):
        tap('Image Classification')
        wait_checked('Image Classification')
    scroll_to('Face Detection')
    if not feature_checked('Face Detection'):
        tap('Face Detection')
        wait_checked('Face Detection')
    # Arabic enablement queues the verified model automatically; there is no
    # separate confirmation dialog. Wait on the visible status, not stale button text.
    scroll_to('Arabic + English OCR (PaddleOCRv5)')
    wait('PaddleOCRv5 Arabic + English model installed', seconds=240, partial=True)
    capture('model-installed')
    # Reopening Settings starts at the scan controls, preserving installed flags.
    home(); tap('Settings'); tap('Scan library now')
    completed, _ = wait('Batch complete', seconds=240, partial=True)
    assert '0 need attention' in completed.get('text', ''), 'An analysis stage failed'
    capture('scan-complete')
    tap('Back'); tap('Gallery')
    tap('omni-ocr-check.png')
    tap('Info'); wait('Media details'); capture('details-open')
    scroll_to('Call 01012345678')
    capture('ocr-phone')
    phone_text = '\n'.join(node.get('text', '') for node in tree().iter('node'))
    scroll_to('Detected amounts', partial=True)
    capture('ocr-amounts')
    text = '\n'.join(node.get('text', '') for node in tree().iter('node'))
    assert '1234.50 EGP' in text, 'Expected exact detected amount'
    assert any(word in text + phone_text for word in ('فاتورة', 'الهاتف')), 'Expected native Arabic OCR text'
    scroll_to('No faces detected'); capture('ocr-face-result')
    (OUTPUT / 'ocr-output.txt').write_text(phone_text + '\n' + text)
    # No Java fatal exception is allowed in any tested route or OCR operation.
    crash = adb('logcat', '-d', '-b', 'crash')
    assert not crash.strip() or PACKAGE not in crash, crash
    print('PASS: optimized APK; light/dark Search, Vault, Settings, photo details; native Arabic OCR, phone, amount, labels and faces; AOSP API 30 without GMS.')
except Exception:
    try:
        capture('failure-current')
        for _ in range(6):
            swipe(up=False)
        capture('failure-top')
    except Exception:
        pass
    raise
finally:
    adb('shell', 'am', 'broadcast', '-a', 'androidx.work.diagnostics.REQUEST_DIAGNOSTICS', '-p', PACKAGE, check=False)
    time.sleep(1)
    (OUTPUT / 'logcat-crash.txt').write_text(adb('logcat', '-d', '-b', 'crash', check=False))
    (OUTPUT / 'logcat.txt').write_text(adb('logcat', '-d', '-t', '3000', check=False))
