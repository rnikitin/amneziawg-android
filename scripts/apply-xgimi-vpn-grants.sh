#!/usr/bin/env bash
set -euo pipefail

PKG="${1:-org.amnezia.awg.debug}"
ADB="${ADB:-adb}"
START_UI="${START_UI:-1}"
ALWAYS_ON_MODE="${ALWAYS_ON_MODE:-clear}"

section() {
  printf '\n== %s ==\n' "$1"
}

adb_shell() {
  "$ADB" shell "$@"
}

section "package"
if ! "$ADB" shell pm list packages -U | tr -d '\r' | grep -F "$PKG"; then
  echo "Package $PKG not found"
  exit 1
fi

APP_UID="$("$ADB" shell dumpsys package "$PKG" | tr -d '\r' | sed -n 's/.*userId=\([0-9][0-9]*\).*/\1/p' | head -n 1)"
echo "APP_UID=${APP_UID:-unknown}"

section "grant requested runtime permissions"
"$ADB" shell dumpsys package "$PKG" | tr -d '\r' |
  sed -n '/requested permissions:/,/install permissions:/p' |
  sed -n 's/^[[:space:]]*\(android\.permission\.[^: ]*\).*/\1/p' |
  sort -u |
  while read -r perm; do
    [ -n "$perm" ] || continue
    echo "pm grant $perm"
    adb_shell pm grant "$PKG" "$perm" >/dev/null 2>&1 || true
  done

section "grant useful extra permissions"
for perm in \
  android.permission.INTERNET \
  android.permission.ACCESS_NETWORK_STATE \
  android.permission.CHANGE_NETWORK_STATE \
  android.permission.ACCESS_WIFI_STATE \
  android.permission.CHANGE_WIFI_STATE \
  android.permission.WAKE_LOCK \
  android.permission.FOREGROUND_SERVICE \
  android.permission.FOREGROUND_SERVICE_SPECIAL_USE \
  android.permission.RECEIVE_BOOT_COMPLETED \
  android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS \
  android.permission.POST_NOTIFICATIONS
do
  echo "pm grant $perm"
  adb_shell pm grant "$PKG" "$perm" >/dev/null 2>&1 || true
done

section "appops allow"
for op in \
  ACTIVATE_VPN \
  RUN_IN_BACKGROUND \
  RUN_ANY_IN_BACKGROUND \
  START_FOREGROUND \
  WAKE_LOCK \
  POST_NOTIFICATION \
  GET_USAGE_STATS \
  SYSTEM_ALERT_WINDOW
do
  echo "appops allow $op"
  adb_shell appops set --uid "$PKG" "$op" allow >/dev/null 2>&1 ||
    adb_shell appops set "$PKG" "$op" allow >/dev/null 2>&1 ||
    true
done

section "background restriction whitelists"
adb_shell cmd netpolicy set restrict-background false >/dev/null 2>&1 || true
adb_shell cmd deviceidle whitelist +"$PKG" >/dev/null 2>&1 || true
adb_shell am set-inactive "$PKG" false >/dev/null 2>&1 || true
adb_shell cmd package set-standby-bucket "$PKG" active >/dev/null 2>&1 || true

if [ -n "${APP_UID:-}" ]; then
  adb_shell cmd netpolicy add restrict-background-whitelist "$APP_UID" >/dev/null 2>&1 || true
  adb_shell cmd netpolicy add app-idle-whitelist "$APP_UID" >/dev/null 2>&1 || true
fi

section "always-on vpn"
case "$ALWAYS_ON_MODE" in
  clear)
    echo "clear stale always-on before manual VPN consent"
    adb_shell settings delete secure always_on_vpn_app >/dev/null 2>&1 || true
    adb_shell settings put secure always_on_vpn_lockdown 0 >/dev/null 2>&1 || true
    adb_shell settings delete secure always_on_vpn_lockdown_whitelist >/dev/null 2>&1 || true
    ;;
  set)
    echo "set always-on vpn to $PKG without lockdown"
    adb_shell settings put secure always_on_vpn_app "$PKG" >/dev/null 2>&1 || true
    adb_shell settings put secure always_on_vpn_lockdown 0 >/dev/null 2>&1 || true
    adb_shell settings delete secure always_on_vpn_lockdown_whitelist >/dev/null 2>&1 || true
    ;;
  skip)
    echo "leave always-on vpn settings unchanged"
    ;;
  *)
    echo "Invalid ALWAYS_ON_MODE=$ALWAYS_ON_MODE (use clear, set, or skip)"
    exit 2
    ;;
esac

if [ "$START_UI" = "1" ]; then
  section "start ui"
  adb_shell monkey -p "$PKG" -c android.intent.category.LEANBACK_LAUNCHER 1 >/dev/null 2>&1 ||
    adb_shell monkey -p "$PKG" -c android.intent.category.LAUNCHER 1 >/dev/null 2>&1 ||
    true
fi

section "verification"
printf 'always_on_mode=%s\n' "$ALWAYS_ON_MODE"
printf 'always_on_vpn_app='
adb_shell settings get secure always_on_vpn_app | tr -d '\r'
printf 'always_on_vpn_lockdown='
adb_shell settings get secure always_on_vpn_lockdown | tr -d '\r'
printf 'always_on_vpn_lockdown_whitelist='
adb_shell settings get secure always_on_vpn_lockdown_whitelist | tr -d '\r'

echo
echo "deviceidle whitelist:"
adb_shell cmd deviceidle whitelist | tr -d '\r' | grep -F "$PKG" || true

echo
echo "netpolicy:"
if [ -n "${APP_UID:-}" ]; then
  adb_shell dumpsys netpolicy | tr -d '\r' | grep -i -A 8 -B 8 "$APP_UID\\|$PKG" || true
fi

echo
echo "appops:"
adb_shell appops get "$PKG" | tr -d '\r' | grep -i -E 'VPN|BACKGROUND|FOREGROUND|WAKE|NOTIFICATION|USAGE|ALERT' || true
