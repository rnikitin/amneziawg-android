# Android GUI for [AmneziaWG](https://amnezia.org/learn-more/31_amneziawg)

**[Download from the Play Store](https://play.google.com/store/apps/details?id=org.amnezia.awg)**

This is an Android GUI for [AmneziaWG](https://amnezia.org/learn-more/31_amneziawg).

## Building

```
$ git clone --recurse-submodules https://github.com/amnezia-vpn/amneziawg-android
$ cd amneziawg-android
$ ./gradlew assembleRelease
```

macOS users may need [flock(1)](https://github.com/discoteq/flock).

## XGIMI / Android TV watchdog build

This branch contains an Android TV-oriented watchdog flow for XGIMI projectors:

- the app records the user-selected desired VPN tunnel;
- a low-residency foreground watchdog checks that the desired tunnel and system VPN are still up;
- stale handshakes are reconnected only after a network probe also fails;
- Android TV shows the watchdog state on the main tunnel screen and exposes watchdog tuning in Settings.

Build and install the debug APK:

```
$ ./gradlew :ui:assembleDebug
$ adb install -r ui/build/outputs/apk/debug/ui-debug.apk
```

After reinstalling, apply the XGIMI grants and background whitelists. The script defaults to the debug package:

```
$ scripts/apply-xgimi-vpn-grants.sh
```

For the release package:

```
$ scripts/apply-xgimi-vpn-grants.sh org.amnezia.awg
```

By default the script uses `ALWAYS_ON_MODE=clear`. This intentionally removes stale Always-on VPN state before first manual authorization, because Android can close the VPN consent dialog when Always-on points at a different package. After the user has successfully enabled a tunnel once and confirmed the Android VPN dialog, set Always-on explicitly:

```
$ ALWAYS_ON_MODE=set scripts/apply-xgimi-vpn-grants.sh org.amnezia.awg.debug
```

ADB grants, `appops ACTIVATE_VPN`, Doze whitelist, and netpolicy whitelist do not replace Android's `VpnService` consent. If the app was reinstalled, data was cleared, package/signature changed, or Android reset the prepared VPN state, the user still needs to approve the system VPN dialog once from the device UI.

Useful checks:

```
$ adb shell settings get secure always_on_vpn_app
$ adb shell settings get secure always_on_vpn_lockdown
$ adb shell pidof org.amnezia.awg.debug
$ adb shell ip addr show tun0
$ adb logcat -d -s AmneziaWG/XgimiWatchdog AmneziaWG/TvMainActivity
```
