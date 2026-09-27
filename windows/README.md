# MockLocWin — Windows twin of MockLoc

Sets the **Windows default location** (the same store behind
Settings → Privacy & security → Location → Default location) via the
`Geolocator.DefaultGeoposition` API. No admin rights, no registry hacking,
no driver. Especially useful now that the Maps app (which used to set it)
was removed from the Store.

## What it does

- **Set as Windows default location** — writes lat/lng to the system store.
  Persists across reboots until cleared/changed.
- **Clear default location** — removes it (`DefaultGeoposition = null`).
- **Show current** — reads back the stored default (`DefaultGeoposition`,
  `IsDefaultGeopositionRecommended`) plus the *effective* position apps get
  right now (`GetGeopositionAsync`, with source like WiFi/IPAddress/Default).
- **Set location from IP country** — same as the Android app (ipwho.is →
  freeipapi.com, capital fallback, follows VPN).
- **Open Location Settings** — jumps to `ms-settings:privacy-location`.

## Requirements (no admin needed to set the location itself)

1. Settings → Privacy & security → Location: device + user location **ON**.
2. **Let desktop apps access your location** → **ON**.
3. The device-wide switch (step 1) needs an administrator; the app shows
   exact guidance when access is denied.

## Important limits (read this)

- The default location is a **fallback**: Windows uses it only when no more
  exact source (GPS, Wi-Fi, IP) is available. On a Wi-Fi-connected PC the
  network fix usually wins — use **Show current** to see what apps actually
  get. On desktops without Wi-Fi it works best.
- **Browsers differ**: Edge uses the Windows location API (affected by this
  app). Chrome/Firefox use their own network location (nearby Wi-Fi → Google
  servers) and are NOT affected — for them use DevTools Sensors override
  (F12 → … → More tools → Sensors) or a geolocation-spoof extension.
- Apps with anti-fraud (Yandex Go/Taxi etc.) may ignore or cross-check
  location regardless of source.

## Build (no Visual Studio needed, just .NET 9 SDK)

```powershell
cd windows\MockLocWin
dotnet build
# run:
bin\Debug\net9.0-windows10.0.22621.0\MockLocWin.exe
```

The project targets `net9.0-windows10.0.22621.0`, so the .NET SDK restores
the Windows projection automatically — no extra packs or registry tweaks.

Self-contained single-file exe for distribution:

```powershell
dotnet publish -c Release -r win-x64 --self-contained `
  -p:PublishSingleFile=true -p:IncludeNativeLibrariesForSelfExtract=true -o publish
```

## How it works (technical)

`Windows.Devices.Geolocation.Geolocator.DefaultGeoposition` is a settable
static `BasicGeoposition?` (confirmed in SDK metadata). Setting it goes
through the location broker (`lfsvc`), which stores it per-user under
`HKLM\SYSTEM\CurrentControlSet\Services\lfsvc\Migrated\UserStore\<SID>`
as an encrypted blob — hence writable only via the API, not by hand.
