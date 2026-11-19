#!/usr/bin/env python3
"""Exercise the APK's guarded QuickJS through its normal profile script UI."""
import importlib.util
import json
from pathlib import Path
import time

spec = importlib.util.spec_from_file_location('v99_smoke', Path(__file__).with_name('smoke-v99-apk.py'))
s = importlib.util.module_from_spec(spec)
spec.loader.exec_module(s)
ROOT = s.ROOT
previous = json.loads((ROOT / 'evidence/release-smoke/summary.json').read_text())
assert previous['status'] == 'pass', 'Run the exact signed-APK smoke first'
s.EVIDENCE = ROOT / 'evidence/quickjs-runtime'
s.EVIDENCE.mkdir(parents=True, exist_ok=True)
summary = {'source_commit': previous['source_commit'], 'apk_sha256': previous['apk_sha256'],
           'profile_name': previous['profile_name'], 'status': 'running'}
script_name = 'NW3QuickJS-' + previous['profile_name'].rsplit('-', 1)[-1]
pid = s.app_pid()
assert pid == previous['app_pid'], 'The smoke APK process changed'


def locate(label, output):
    return s.wait_ui(output, lambda tree: s.find_node(tree, label), f'Missing UI entry: {label}')


try:
    s.tap(locate('关闭', 'shortcut-before-close.xml'))
    s.tap(locate('Settings', 'dashboard-before-settings.xml'))
    s.tap(s.wait_ui(
        'settings.xml', lambda tree: s.find_node(tree, '配置中心', 'settings-configuration-center-entry'),
        'Settings did not show Configuration Center',
        scroll_from=lambda tree: s.find_node(tree, 'Settings') is not None,
    ))
    s.tap(locate('配置文件与订阅', 'configuration-center.xml'))

    def fixture_action(tree):
        candidates = []
        for row in tree.iter('node'):
            rect = s.bounds(row)
            if row.get('package') != s.PACKAGE or rect is None:
                continue
            if s.find_node(row, previous['profile_name']) is None:
                continue
            buttons = [node for node in row.iter('node') if node.get('content-desc') == '更多' and s.bounds(node)]
            if len(buttons) == 1:
                candidates.append(((rect[2] - rect[0]) * (rect[3] - rect[1]), buttons[0]))
        return min(candidates, key=lambda item: item[0])[1] if candidates else None

    s.tap(s.wait_ui('profiles-before.xml', fixture_action, 'No action on the saved fixture row in Profile Manager'))
    s.tap(locate('JS 脚本覆写', 'profile-actions.xml'))
    s.tap(locate('管理脚本中心', 'profile-override.xml'))
    s.tap(locate('新建脚本', 'scripts-empty.xml'))
    tree = s.wait_ui('script-name.xml', lambda tree: tree if any(
        node.get('class') == 'android.widget.EditText' for node in tree.iter('node')) else None,
        'New script name input was not shown')
    edit = next(node for node in tree.iter('node') if node.get('class') == 'android.widget.EditText')
    s.tap(edit)
    s.run('shell', 'input', 'text', script_name)
    s.run('shell', 'input', 'keyevent', 'KEYCODE_BACK')
    s.tap(locate('创建并保存', 'script-create.xml'))
    locate(script_name, 'script-created.xml')
    s.run('shell', 'input', 'keyevent', 'KEYCODE_BACK')
    s.tap(locate(script_name, 'script-bind.xml'))
    # Clear only the dedicated emulator logs immediately before the engine call.
    s.run('shell', 'logcat', *s.LOG_BUFFERS, '-c')
    s.tap(locate('保存', 'script-save.xml'))
    expected = f'QuickJS executed script: {script_name}'
    applied = f'Successfully applied script: {script_name}'
    for _ in range(30):
        if s.app_pid() != pid:
            raise RuntimeError('Arca exited while invoking QuickJS')
        raw, app = s.save_logs(pid)
        s.check_crashes(raw, app, pid)
        if 'QuickJS execution failed, falling back to Rhino' in app:
            raise RuntimeError('QuickJS invocation fell back to Rhino')
        if expected in app and applied in app:
            summary.update(status='pass', quickjs_guarded_script='pass', script_name=script_name,
                           evidence_lines=[line for line in app.splitlines() if expected in line or applied in line])
            break
        time.sleep(2)
    else:
        raise RuntimeError('The signed APK did not report native QuickJS script execution')
    s.screenshot('quickjs-script-applied.png')
except Exception as exc:
    summary.update(status='fail', error=str(exc))
    try:
        s.save_logs(pid)
        s.screenshot('failure.png')
    except Exception as extra:
        summary['evidence_error'] = str(extra)
    raise
finally:
    (s.EVIDENCE / 'summary.json').write_text(json.dumps(summary, indent=2, ensure_ascii=False) + '\n')
print(json.dumps(summary, ensure_ascii=False), flush=True)
