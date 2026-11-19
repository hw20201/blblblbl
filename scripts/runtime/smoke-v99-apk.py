#!/usr/bin/env python3
"""Smoke the exact signed ARM64 APK on this task's disposable Android instance."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import shlex
import subprocess
import time
import uuid
import xml.etree.ElementTree as ET

ROOT = Path(os.environ.get('ARCA_SMOKE_ROOT', Path.home() / 'arca-v99-nowhere-20261119'))
EVIDENCE = ROOT / 'evidence/release-smoke'
SDK = Path(os.environ.get('ANDROID_HOME', Path.home() / 'android-sdk'))
ADB = [str(SDK / 'platform-tools/adb'), '-P', os.environ.get('ARCA_ADB_PORT', '5041'),
       '-s', os.environ.get('ARCA_ADB_SERIAL', 'emulator-5586')]
PACKAGE = 'com.github.arca.android'
CORE_VERSION = 'v1.19.33_nw3_5d965f6_native_v99'
LOG_BUFFERS = ('-b', 'main', '-b', 'system', '-b', 'crash')


def run(*args, timeout=90, check=True):
    result = subprocess.run(ADB + list(args), capture_output=True, timeout=timeout)
    text = (result.stdout + result.stderr).decode(errors='replace')
    with (EVIDENCE / 'commands.log').open('a') as log:
        log.write(f'adb {shlex.join(args)} [exit {result.returncode}]\n{text}\n')
    if check and result.returncode:
        raise RuntimeError(text)
    return text


def app_pid():
    pid = run('shell', 'pidof', PACKAGE, check=False).strip()
    return pid if re.fullmatch(r'[0-9]+', pid) else None


def dump_ui(name, compressed=True):
    target = '/sdcard/arca-v99-smoke-window.xml'
    flags = ('--compressed',) if compressed else ()
    output = run('shell', 'uiautomator', 'dump', *flags, target, timeout=45, check=False)
    if 'dumped to' not in output:
        return None
    path = EVIDENCE / name
    run('pull', target, str(path))
    return ET.parse(path).getroot()


def screenshot(name):
    with (EVIDENCE / name).open('wb') as output:
        subprocess.run(ADB + ['exec-out', 'screencap', '-p'], stdout=output, check=True, timeout=60)


def bounds(node):
    values = tuple(map(int, re.findall(r'-?\d+', node.get('bounds', ''))))
    return values if len(values) == 4 and values[0] < values[2] and values[1] < values[3] else None


def find_node(tree, *labels):
    candidates = []
    for node in tree.iter('node'):
        if node.get('package') != PACKAGE or bounds(node) is None:
            continue
        values = [part.strip() for field in ('text', 'content-desc')
                  for part in re.split(r'[,\n]', node.get(field, ''))]
        tag = node.get('resource-id', '').rsplit('/', 1)[-1]
        if any(label in values or label == tag for label in labels):
            candidates.append(node)
    # The bottom-bar Settings item can share its label with the page title.
    return max(candidates, key=lambda node: bounds(node)[1], default=None)


def tap(node):
    left, top, right, bottom = bounds(node)
    run('shell', 'input', 'tap', str((left + right) // 2), str((top + bottom) // 2))


def swipe_up(tree):
    rects = [bounds(node) for node in tree.iter('node') if node.get('package') == PACKAGE]
    rects = [rect for rect in rects if rect is not None]
    if not rects:
        return
    left, top, right, bottom = max(rects, key=lambda rect: (rect[2] - rect[0]) * (rect[3] - rect[1]))
    x = str((left + right) // 2)
    height = bottom - top
    run('shell', 'input', 'swipe', x, str(top + height * 72 // 100), x, str(top + height * 30 // 100), '450')


def wait_ui(name, predicate, failure, scroll_from=None):
    for attempt in range(8):
        # Sometimes compression omits a useful Compose semantics node.
        tree = dump_ui(name, compressed=attempt % 2 == 0)
        if tree is not None:
            found = predicate(tree)
            if found is not None:
                return found
            if scroll_from is not None and attempt % 2 and scroll_from(tree):
                swipe_up(tree)
        time.sleep(2)
    raise RuntimeError(failure)


def save_logs(pid):
    raw = run('shell', 'logcat', *LOG_BUFFERS, '-d', timeout=60)
    (EVIDENCE / 'logcat.txt').write_text(raw, encoding='utf-8')
    app = run('shell', 'logcat', *LOG_BUFFERS, '--pid=' + pid, '-d', timeout=60) if pid else ''
    (EVIDENCE / 'logcat-app.txt').write_text(app, encoding='utf-8')
    return raw, app


def check_crashes(raw, app, pid):
    if any(marker in app for marker in ('FATAL EXCEPTION', 'Fatal signal', 'ClashCore initial setup deferred:')):
        raise RuntimeError(f'Arca PID {pid} logged a Java/native/core initialization failure')
    # A crash reporter may log under its own PID. Match Arca explicitly in the fresh buffers.
    identity = re.escape(PACKAGE) + r'(?:[:][\w.-]+)?'
    if re.search(r'\bProcess:\s*' + identity + r',\s*PID:\s*[0-9]+\b', raw) or re.search(
        r'>>>\s*' + identity + r'\s*<<<', raw
    ):
        raise RuntimeError('A fresh crash report names Arca')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('apk', type=Path)
    parser.add_argument('github_apk_sha256')
    parser.add_argument('source_commit')
    args = parser.parse_args()
    if not re.fullmatch(r'[0-9a-fA-F]{40}', args.source_commit):
        parser.error('source_commit must be a full 40-hex Git commit')
    if not re.fullmatch(r'[0-9a-fA-F]{64}', args.github_apk_sha256):
        parser.error('github_apk_sha256 must contain 64 hexadecimal digits')
    apk = args.apk.resolve()
    sha256 = hashlib.sha256()
    with apk.open('rb') as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b''):
            sha256.update(chunk)
    actual = sha256.hexdigest()
    if actual != args.github_apk_sha256.lower():
        raise SystemExit('APK hash does not match GitHub artifact')

    EVIDENCE.mkdir(parents=True, exist_ok=True)
    profile_name = 'NW3Smoke-' + uuid.uuid4().hex[:8]
    summary = {
        'apk_sha256': actual,
        'source_commit': args.source_commit.lower(),
        'package': PACKAGE,
        'device': os.environ.get('ARCA_SMOKE_DEVICE', 'task-only Android 35 x86_64 with ARM64 translation'),
        'profile_name': profile_name,
        'status': 'running',
    }
    summary_path = EVIDENCE / 'summary.json'
    summary_path.write_text(json.dumps(summary, indent=2) + '\n', encoding='utf-8')
    launched = False
    pid = None
    try:
        for attempt in range(120):
            if run('shell', 'getprop', 'sys.boot_completed', timeout=15, check=False).strip() == '1':
                break
            if attempt % 12 == 0:
                print('Waiting for task-only software emulator boot', flush=True)
            time.sleep(5)
        else:
            raise RuntimeError('Software emulator did not finish booting')

        run('shell', 'input', 'keyevent', 'KEYCODE_WAKEUP')
        run('shell', 'wm', 'dismiss-keyguard', check=False)
        for key in ('window_animation_scale', 'transition_animation_scale', 'animator_duration_scale'):
            run('shell', 'settings', 'put', 'global', key, '0')
        if 'Success' not in run('install', '--no-streaming', '-r', str(apk), timeout=240):
            raise RuntimeError('APK install did not report success')
        run('shell', 'pm', 'grant', PACKAGE, 'android.permission.POST_NOTIFICATIONS')
        run('shell', 'am', 'force-stop', PACKAGE)
        run('shell', 'logcat', *LOG_BUFFERS, '-c')
        launched = True
        launch = run('shell', 'am', 'start', '-W', '-n', PACKAGE + '/.ui.MainActivity', timeout=180)
        if 'Error:' in launch or 'Exception' in launch:
            raise RuntimeError(launch)

        for _ in range(30):
            current = app_pid()
            if pid is not None and pid != current:
                raise RuntimeError(f'Arca PID changed or disappeared after launch: {pid} -> {current}')
            pid = current
            if pid is not None:
                app = run('shell', 'logcat', *LOG_BUFFERS, '--pid=' + pid, '-d', timeout=40)
                check_crashes('', app, pid)
                if f'Mihomo Core initialized. Version: {CORE_VERSION}' in app:
                    break
            time.sleep(3)
        else:
            raise RuntimeError('Expected v99 JNI core version was not observed in Arca after release launch')
        summary.update(app_pid=pid, r8_release_launch='pass', core_version_observed=CORE_VERSION)

        # Generated fixture only. Import without starting the VPN or dialing a node.
        link = f'nowhere://0123456789abcdef0123456789abcdef@127.0.0.1:443?net=tcp#{profile_name}'
        result = run('shell', 'am', 'start', '-W', '-a', 'android.intent.action.VIEW', '-d',
                     shlex.quote(link), PACKAGE, timeout=180)
        if 'Error:' in result or 'Exception' in result:
            raise RuntimeError(result)
        button = wait_ui(
            'import-confirmation.xml',
            lambda tree: find_node(tree, '导入') if find_node(tree, '导入 Nowhere 节点', 'nowhere-import-dialog')
            is not None else None,
            'Nowhere import confirmation was not rendered by the signed R8 APK',
        )
        screenshot('import-confirmation.png')
        summary['nowhere_view_confirmation'] = 'pass'
        tap(button)

        def ready_for_profiles(tree):
            if any('导入失败' in node.get('text', '') for node in tree.iter('node') if node.get('package') == PACKAGE):
                raise RuntimeError('Nowhere import failed in the R8 app')
            if find_node(tree, '导入 Nowhere 节点', 'nowhere-import-dialog') is not None:
                return None
            return find_node(tree, 'Profiles', '配置文件', 'Конфигурация', 'dashboard-shortcut-profiles')

        # The dashboard exposes the saved profile through its normal Profiles shortcut.
        # Use that visible entry directly instead of scrolling the separate Settings page.
        tap(wait_ui('after-import.xml', ready_for_profiles, 'Dashboard did not expose its Profiles shortcut'))
        summary['profile_navigation'] = 'dashboard-shortcut'

        def saved_profile(tree):
            if (find_node(tree, '添加配置：本地文件 / 订阅') is None
                    or find_node(tree, '关闭', 'shortcut-panel-close') is None):
                return None
            # Require a real clickable row containing the active-state labels.
            # The panel subtitle repeats the fixture name but is not a saved row.
            for row in tree.iter('node'):
                if row.get('package') != PACKAGE or row.get('clickable') != 'true':
                    continue
                labels = {node.get('text') for node in row.iter('node')}
                if {profile_name, '当前使用', '当前'} <= labels:
                    return find_node(row, profile_name)
            return None

        wait_ui('imported-profile.xml', saved_profile, f'No active saved profile row for {profile_name}')
        summary['nowhere_import_action'] = 'pass'

        # A process restart proves the fixture was saved, beyond in-memory state.
        summary['initial_app_pid'] = pid
        run('shell', 'am', 'force-stop', PACKAGE)
        launch = run('shell', 'am', 'start', '-W', '-n', PACKAGE + '/.ui.MainActivity', timeout=180)
        if 'Error:' in launch or 'Exception' in launch:
            raise RuntimeError(launch)
        restarted_pid = None
        for _ in range(30):
            current = app_pid()
            if restarted_pid is not None and current != restarted_pid:
                raise RuntimeError('Arca process changed during persistence relaunch')
            restarted_pid = current
            if current:
                app = run('shell', 'logcat', *LOG_BUFFERS, '--pid=' + current, '-d', timeout=40)
                check_crashes('', app, current)
                if f'Mihomo Core initialized. Version: {CORE_VERSION}' in app:
                    break
            time.sleep(2)
        else:
            raise RuntimeError('The v99 core did not initialize on the persistence relaunch')
        if restarted_pid == pid:
            raise RuntimeError('The persistence relaunch did not start a new app process')
        pid = restarted_pid
        summary['app_pid'] = pid
        tap(wait_ui('relaunched-dashboard.xml', ready_for_profiles, 'Relaunched dashboard lacked Profiles shortcut'))
        profile = wait_ui('saved-profile.xml', saved_profile, f'Saved profile missing after restart: {profile_name}')
        screenshot('saved-profile.png')
        summary.update(nowhere_profile_saved='pass', nowhere_profile_persisted_after_restart='pass',
                       profile_row_text=profile.get('text') or profile.get('content-desc'))

        if app_pid() != pid:
            raise RuntimeError(f'Arca original PID {pid} is no longer running')
        check_crashes(*save_logs(pid), pid)
        summary['status'] = 'pass'
    except Exception as exc:
        summary.update(status='fail', error=str(exc))
        if launched:
            try:
                save_logs(pid)
                screenshot('failure.png')
            except Exception as evidence_error:
                summary['failure_evidence_error'] = str(evidence_error)
        raise
    finally:
        summary_path.write_text(json.dumps(summary, indent=2, ensure_ascii=False) + '\n', encoding='utf-8')
    print(json.dumps(summary, ensure_ascii=False), flush=True)


if __name__ == '__main__':
    main()
