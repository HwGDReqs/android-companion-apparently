# hwgdreqs mobile

Android (Kotlin + Jetpack Compose + Material 3) companion app for
[HwGDReqs](https://github.com/HwGDReqs/HwGDReqs). **Skeleton** to build on.

- Package: `com.malikhw.hwgdreqs` · App name: `hwgdreqs mobile`
- minSdk 24 · targetSdk/compileSdk 35 · AGP 8.7.3 · Kotlin 2.0.21 · Gradle 8.9

## Flow

1. **`DeviceSelectActivity`** (launcher) – mDNS discovery of `_hwgdreqs._tcp.local.`
   (TXT: `port`, `name`, `version`, `path`, `login`). Lists each desktop with its login,
   address and version. Scans only while the screen is visible.
2. **`PairingActivity`** – on tap:
   - a stored token for that host exists → `POST /mobile/auth`; valid → straight to the queue
   - no token / rejected (401) → random 4-digit PIN is shown, `POST /mobile/pair` blocks
     until the PIN is typed into the desktop popup (desktop times out after 60 s) → token stored
   - failures show a message with *Try again* (new PIN) / *Back*
3. **`MainActivity`** – the queue. `GET /queue` every 2 s (only while the app is in the
   foreground). Each entry: **name**, by **author**, from **requester**.
   If the connection drops the last data stays visible with a "retrying" banner.
   Back returns to device selection.

## Layout

```
app/src/main/java/com/malikhw/hwgdreqs/
  HwGDReqsApp.kt            service locator (prefs + api)
  DeviceSelect{Activity,ViewModel}.kt
  Pairing{Activity,ViewModel}.kt
  MainActivity.kt, QueueViewModel.kt
  data/AuthPreferences.kt   device_id + token per desktop host
  network/DeviceDiscovery.kt  NsdManager + MulticastLock, serial resolve queue
  network/HwGDReqsApi.kt      OkHttp client (pair / auth / queue)
  network/Models.kt
  ui/                        Compose screens + theme
branding/                    original logo + 512px icon
```

## Build

Open the folder in Android Studio (Ladybug or newer, JDK 17+) and run, or:

```
./gradlew assembleDebug      # app/build/outputs/apk/debug/app-debug.apk
```

On the PC enable **Settings → API → Host to local network**; phone and PC must be on the same Wi-Fi.

## Notes / next steps

- Token is stored in private SharedPreferences (`AuthPreferences`); swap the backing store there
  if you want Keystore-encrypted storage.
- `GET /queue` is currently unauthenticated on the desktop; the app still sends
  `X-Device-Id` / `X-Auth-Token` so it keeps working if that changes. The write endpoints
  (`/add`, `/delete`, …) need the token — `HwGDReqsApi` is the place to add them.
- The queue payload also has `difficulty`, `platform`, `message`, `likes`, `aredl_position`, … —
  extend `QueueEntry` + `parseQueue` to use them.
- Guide corrections applied: `Context.NSD_SERVICE` (not `NSD_SERVICE_SERVICE`), PIN range 1000–9999
  inclusive, serialized `resolveService` calls.
- Possible additions: manual IP entry (when mDNS is blocked), auto-connect to the last device.
