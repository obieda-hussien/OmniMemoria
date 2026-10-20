#!/usr/bin/env python3
"""Exercise the optimized APK and native OCR on an API-30 AOSP emulator.

Only a synthetic fixture is pushed to the emulator; no user photos are used.
"""
import re
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
    adb('shell', 'mkdir', '-p', '/sdcard/Pictures')
    adb('push', 'scripts/fixtures/omni-ocr-check.png', '/sdcard/Pictures/omni-ocr-check.png')
    adb('shell', 'content', 'insert', '--uri', 'content://media/external/images/media',
        '--bind', '_data:s:/sdcard/Pictures/omni-ocr-check.png', '--bind', 'mime_type:s:image/png',
        '--bind', '_display_name:s:omni-ocr-check.png',
        '--bind', '_size:l:'+str(Path('scripts/fixtures/omni-ocr-check.png').stat().st_size),
        '--bind', 'width:i:1080', '--bind', 'height:i:1440', '--bind', 'media_type:i:1',
        '--bind', 'datetaken:l:'+str(int(time.time()*1000)))
    # Shell inserts do not populate image dimensions on every MediaProvider.
    # The app deliberately excludes incomplete metadata, so prepare a valid row
    # and request a real media scan rather than weakening its query policy.
    adb('shell', 'am', 'broadcast', '-a', 'android.intent.action.MEDIA_SCANNER_SCAN_FILE',
        '-d', 'file:///sdcard/Pictures/omni-ocr-check.png')
    media = adb('shell', 'content', 'query', '--uri', 'content://media/external/images/media',
        '--projection', '_id:_display_name:_size:width:height')
    (OUTPUT / 'fixture-media.txt').write_text(media)
    assert 'omni-ocr-check.png' in media and 'width=1080' in media and 'height=1440' in media, media


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
    scroll_to('Arabic OCR (Tesseract)')
    tap('Arabic OCR (Tesseract)')
    tap('Later')
    scroll_to('Image Classification'); tap('Image Classification')
    scroll_to('Face Detection'); tap('Face Detection')
    scroll_to('Download 12.6 MB')
    tap('Download 12.6 MB'); capture('model-dialog')
    tap('Download'); capture('model-requested')
    wait('High accuracy model installed', seconds=150, partial=True)
    capture('model-installed')
    # Reopening Settings starts at the scan controls, preserving installed flags.
    home(); tap('Settings'); tap('Scan library now')
    completed, _ = wait('Scan complete', seconds=180, partial=True)
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
    assert 'FATAL EXCEPTION' not in crash, crash
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
