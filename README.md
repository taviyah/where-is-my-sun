# Where's my Sun

Point your phone at the sky and see **today's or tomorrow's sun and moon paths** drawn over the
camera, with hour marks. The question it answers: *will the spot where I parked be in the shade
in three hours?*

## Features

- Sun and moon paths over the live camera image, with hour marks, for today or tomorrow
- A time slider to see where the sun will be at any time of day
- A 2D sky map for when the camera isn't useful
- Saved places
- Tap-the-real-sun calibration, and a warning when nearby metal disturbs the compass
- An e-ink mode (black on white, no animations) for e-paper phones

## How it works

- Positions of the sun and moon come from [commons-suncalc](https://github.com/shred/commons-suncalc).
- The phone's rotation-vector sensor gives where the camera points; the app projects each sky
  position onto the screen (`orientation/`, `astro/`).
- Kotlin, Jetpack Compose and CameraX. No account, no server, no network access needed.

## Build

Open the folder in Android Studio, or:

```bash
./gradlew :app:assembleDebug
./gradlew :app:testDebugUnitTest   # astronomy and orientation tests
```

minSdk 26. Built with AI coding tools (Claude Code and Codex), designed and tested on real phones
by the author.

## License

MIT
