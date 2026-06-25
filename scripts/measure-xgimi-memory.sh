#!/usr/bin/env bash
set -euo pipefail

PKG="${1:-org.amnezia.awg}"
ADB="${ADB:-adb}"

section() {
  printf '\n== %s ==\n' "$1"
}

section "package"
"$ADB" shell "pm list packages | grep -F \"$PKG\" || true"

section "process"
"$ADB" shell "ps -A -o USER,PID,PPID,VSZ,RSS,NAME 2>/dev/null | grep -i amnezia || ps | grep -i amnezia || true"

section "meminfo"
"$ADB" shell "dumpsys meminfo \"$PKG\" | head -120 || true"

section "status"
"$ADB" shell "pid=\$(pidof \"$PKG\"); if [ -n \"\$pid\" ]; then cat /proc/\$pid/status | grep -E 'VmRSS|VmHWM|Threads'; else echo no-process; fi"

section "oom"
"$ADB" shell "pid=\$(pidof \"$PKG\"); if [ -n \"\$pid\" ]; then cat /proc/\$pid/oom_score_adj; else echo no-process; fi"

section "services"
"$ADB" shell "dumpsys activity services \"$PKG\" | head -160 || true"

section "jobs"
"$ADB" shell "dumpsys jobscheduler | grep -i -A8 -B4 \"$PKG\\|xgimi\\|amnezia\" | head -160 || true"

section "alarms"
"$ADB" shell "dumpsys alarm | grep -i -A8 -B4 \"$PKG\\|xgimi\\|amnezia\" | head -160 || true"

section "vpn"
"$ADB" shell "dumpsys connectivity | grep -i -A4 -B4 vpn | head -120 || true"
