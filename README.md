# MockLocation — Android mock location app

Appears in **Settings → Developer options → Select mock location app**.

## What it does
- Enter lat/lng → **Start mocking** → mocks every 1s via foreground service on both stacks:
  - `LocationManager` test providers (GPS + Network),
  - Play Services **Fused** provider via `setMockMode(true)` + `setMockLocation()` (this is what Google Maps & co. actually use).
- **Stop mocking** removes test providers and disables fused mock mode.
- Persists last coordinates. Shows hint if app is not selected as mock app.

## Why it shows in Developer options
`AndroidManifest.xml` declares:
```xml
<uses-permission android:name="android.permission.ACCESS_MOCK_LOCATION" />
```
Android lists every app declaring this permission in "Select mock location app".

## Open in Android Studio
1. Open this `MockLocation` folder in Android Studio (Electric Eel+).
2. Let Gradle sync (AGP 8.5.2, Kotlin 1.9.24, compileSdk 34, minSdk 26).
3. Run on a physical device (emulators already allow mock via Extended Controls).

## Use on device
1. Install debug APK.
2. Grant Location permission when asked.
3. Enable Developer options: Settings → About phone → tap **Build number 7×**.
4. Settings → System → Developer options → **Select mock location app** → choose **MockLocation**.
5. Back in app: enter lat/lng → **Start mocking**.
6. Verify in Google Maps — blue dot jumps to mocked point.
7. **Stop mocking** restores real GPS.

Quick test points:
- NYC: `40.712776, -74.005974`
- London: `51.507351, -0.127758`
- Tokyo: `35.6762, 139.6503`

## Project structure
```
app/src/main/
  AndroidManifest.xml            # ACCESS_MOCK_LOCATION + foreground service
  java/com/example/mocklocation/
    MainActivity.kt              # UI, permissions, start/stop
    MockLocationService.kt       # foreground service, 1s push loop
    LocationMockManager.kt       # addTestProvider / setTestProviderLocation
  res/layout/activity_main.xml
  res/values/strings.xml, themes.xml
```

## Notes / limits
- Must be **selected** as mock app or `SecurityException` is thrown (app shows dialog).
- Fused mock needs Google Play Services on the device (any stock phone has it).
- If Maps still shows the real spot: make sure this app is selected as mock app, location is ON, then **Stop → Start** again and wait ~5s for the blue dot to jump.
- Apps with anti-fraud (Yandex Maps/Go, some banking/taxi apps) check Android's `isMock` flag and deliberately ignore mocked fixes. The Verify screen shows `[isMock=true]` — that flag is set by Android itself and no non-root app can clear it. Options: airplane mode + Wi-Fi (kills cell-tower geolocation so the app falls back to GPS), or root + Smali Patcher / "Mock Mock Locations" Xposed module.
- Android 12+ requires `FOREGROUND_SERVICE_LOCATION` + foregroundServiceType="location" (already declared).
- Some banking / games detect `isMock()` / `isFromMockProvider()` and refuse to work — expected.
