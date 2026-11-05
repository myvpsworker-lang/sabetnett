# SabetNet

SabetNet is an Android VPN client forked from [v2rayNG](https://github.com/2dust/v2rayNG) by 2dust.
It keeps v2rayNG's features and the [Xray core](https://github.com/XTLS/Xray-core), with a redesigned
interface, its own branding and an optional remote-config service for announcements.

SabetNet یک کلاینت اندروید است که از [v2rayNG](https://github.com/2dust/v2rayNG) ساخته شده؛ همه‌ی قابلیت‌های
اصلی و هسته‌ی Xray حفظ شده و ظاهر برنامه و هویت آن عوض شده است.

## What is different from v2rayNG

- New Compose / Material 3 interface with light and dark themes and a theme toggle
- Connect circle with running time, selected server and ping; server list opens with one tap
- Per-subscription tools: ping all servers of a group, usage information, support link
- Optional remote config (banners, sponsors page, announcements, push-style messages, accent color,
  welcome slides) read from a public JSON endpoint; the app works normally when it is unreachable
- Smart reconnect: switches to another reachable server after repeated failed health checks
  (only while the app process is alive)

## Building

Everything is built with GitHub Actions: see `.github/workflows/build-novanet.yml` and `NOVANET.md`
(build and signing instructions, in Persian). To build locally, initialise submodules, build the
native libraries as in the workflow, then run from `V2rayNG/`:

```
./gradlew :app:assemblePlaystoreDebug
./gradlew :app:testPlaystoreDebugUnitTest
```

Signing keys are never stored in this repository; release signing reads them from CI secrets.

## License and credits

This program is free software under the **GNU General Public License v3.0** (see `LICENSE`). It is a
modified version of v2rayNG, Copyright © 2dust and contributors; the original license and copyright
notices are kept. Distributed APKs must be accompanied by this corresponding source code.

Thanks to the v2rayNG, Xray-core and AndroidLibXrayLite authors.
