#!/system/bin/sh
# Pushed to /data/local/tmp for UiAutomation: avoid executeShellCommand quote parsing.
set -eu
port=${1:?fixture port required}
printf 'GET /probe HTTP/1.1\r\nHost: 10.0.2.2\r\nConnection: close\r\n\r\n' | toybox nc -w 5 -W 5 10.0.2.2 "$port"
