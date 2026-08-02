# MidiPad

[midipad.example.com](https://midipad.example.com) · [Download the APK](https://github.com/YOUR-USER/midipad/releases/latest)

An Android phone as a USB MIDI controller for Ableton Live: four momentary
buttons and a large XY pad, sending 14-bit values over a class-compliant USB
MIDI connection. No drivers, no network, no Bluetooth pairing.

## Build

Open the project folder in Android Studio (Koala or newer) and press Run, or
from the command line with a local Gradle wrapper:

```
gradle wrapper
./gradlew installDebug
```

Requires JDK 17, `compileSdk 35`, `minSdk 23`.

## Connect

1. Plug the phone into the computer with a data cable — many charge-only cables
   will not enumerate.
2. On the phone, pull down the notification shade, tap the USB notification,
   and choose **MIDI**.
3. Launch MidiPad. The status line should read *Sending to Android USB
   Peripheral*. If it does not, tap the status line to pick a destination
   manually.
4. In Live: **Preferences → Link/Tempo/MIDI**, find the new input port, and
   switch **Remote** on. Leave Track off unless you want the buttons to play
   the armed track.

## Map it

Hit Cmd/Ctrl+M, click a parameter, then move the control on the phone.

| Control | Message |
| --- | --- |
| Pad, horizontal | Pitch bend, channel 1 |
| Pad, vertical | Pitch bend, channel 2 |
| Buttons 1–4 | Note on/off, C1–D#1 (36–39), channel 1 |

Pitch bend is the default for the pad because Live maps it at full 14-bit
resolution — 16,384 steps rather than the 128 a 7-bit CC gives you. On a slow
filter sweep the difference between the two is clearly audible as stepping. Two
separate channels are used so the axes stay independent; there is only one bend
value per channel.

To change any of this, edit `Config.kt`. `MidiTarget` also has `Cc7` for plain
control changes and `Cc14` for MSB/LSB pairs, though Live ignores the LSB half
of a 14-bit CC pair, so it is only useful for other hosts.

## How it works

- **`MidiEngine`** owns the connection. All port writes happen on one
  background thread at urgent-audio priority, so a slow USB write can never
  stall the UI.
- **Coalescing.** Touch events arrive at 120–240 Hz. Sending every one of them
  floods the port and *adds* latency. The engine keeps only the newest value
  per axis and flushes every 5 ms, skipping any axis that has not changed.
- **`XYPadView`** reports normalised coordinates with a bottom-left origin, and
  latches on release — a filter stays where you left it instead of snapping
  back to centre.
- **`PadButton`** sends note-on at touch down rather than on release, which is
  where a normal Android `Button` would fire.
- The screen is kept awake while the app is in the foreground. On backgrounding
  the app sends all-notes-off, resets bend to centre, and releases the port.

## Notes and limits

- **Z axis is deliberately absent.** `MotionEvent.getPressure()` returns a
  constant on most phones without pressure hardware. If you want a third
  dimension later, the honest options are `getTouchMajor()` (contact area), a
  separate strip along one edge, or device tilt from the accelerometer.
- **Long-press the status line** to send a panic (all notes off on all 16
  channels, bend to centre).
- **No "Android USB Peripheral" in the list?** Not every phone ships the USB
  MIDI gadget driver, even on Android 6+. Test with a second app to confirm
  before assuming it is this one. If the phone genuinely lacks it, the
  fallbacks are BLE MIDI (fine on macOS, awkward on Windows) or a network MIDI
  bridge, both of which cost latency.
- The layout is locked to portrait so a stray rotation cannot interrupt a
  performance. Change `screenOrientation` in the manifest if you want otherwise.

## Layout

```
.
├── app/src/main/
│   ├── AndroidManifest.xml
│   ├── java/com/example/midipad/
│   │   ├── Config.kt          buttons, channels, axis targets — start here
│   │   ├── MainActivity.kt    wiring and status line
│   │   ├── MidiEngine.kt      device discovery, port, sender thread, coalescing
│   │   ├── MidiTarget.kt      message encoding (pitch bend / CC)
│   │   ├── PadButton.kt       momentary trigger view
│   │   └── XYPadView.kt       the pad
│   └── res/
│       ├── layout/activity_main.xml
│       └── values/{colors,strings,themes}.xml
├── docs/                      the site served at the custom domain
│   ├── CNAME                  the domain itself — GitHub reads this file
│   ├── favicon.svg
│   └── index.html             self-contained, no build step
└── .github/workflows/
    ├── build.yml              debug APK on every push
    └── release.yml            signed APK on every v* tag
```

## Publishing

### 1. Replace the placeholders

Two strings appear throughout: `YOUR-USER` and `midipad.example.com`.

```
grep -rl 'YOUR-USER\|midipad.example.com' . | xargs sed -i \
  -e 's/YOUR-USER/your-github-username/g' \
  -e 's/midipad.example.com/your-domain.com/g'
```

On macOS use `sed -i ''`.

### 2. Push

```
git init && git add . && git commit -m "MidiPad"
git branch -M main
git remote add origin git@github.com:your-github-username/midipad.git
git push -u origin main
```

`.github/workflows/build.yml` builds a debug APK on every push and attaches it
to the run as an artifact. Pushing a `v*` tag runs `release.yml`, which signs a
release APK and publishes it to GitHub Releases — see the comments at the top of
that file for the four repository secrets it needs.

### 3. Turn on Pages

**Settings → Pages → Source: Deploy from a branch → `main` / `/docs`.**

The site lives in `docs/` as a single self-contained HTML file. The hero is a
working XY pad: it runs the same 14-bit encoding as `MidiTarget.PitchBend` and
shows the actual bytes that would go out on the wire.

### 4. Point the domain

`docs/CNAME` already holds the domain, which is what tells GitHub to serve the
site there. Add the DNS records at your registrar:

**Apex domain** (`example.com`) — four A records, all to `@`:

```
185.199.108.153
185.199.109.153
185.199.110.153
185.199.111.153
```

Add the AAAA records too if your registrar supports them:

```
2606:50c0:8000::153   2606:50c0:8001::153
2606:50c0:8002::153   2606:50c0:8003::153
```

**Subdomain** (`midipad.example.com`) — one CNAME record:

```
midipad  CNAME  your-github-username.github.io.
```

A subdomain is the better choice: CNAME records follow GitHub if they ever
change those IPs, and apex A records do not.

Then back in **Settings → Pages**, confirm the custom domain is listed, wait for
the DNS check to pass, and tick **Enforce HTTPS**. Certificate issuing usually
takes a few minutes and occasionally up to an hour. These addresses are current
as of writing; if the DNS check fails, GitHub's own Pages documentation has the
authoritative list.
