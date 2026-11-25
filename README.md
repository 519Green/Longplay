# Longplay

An Android TV app for the film and audio collections of the Internet Archive, laid out for the remote.

- **Watch:** feature films from the silent era onward, classic TV grouped into shows, cartoons, short films,
  newsreels, and educational and industrial films.
- **Listen:** audiobooks chapter by chapter, old-time radio, 78 rpm records and LPs, and live concert recordings.
- **Built for the couch:** every screen works with a D-pad; Continue Watching and Continue Listening; My List;
  search by keyboard or voice.
- **Public domain by default:** out of the box, only titles marked public domain or openly licensed, titles old
  enough to be out of copyright in the United States, and US government works. Adult titles are hidden by default,
  and a PIN guards turning either filter off.

No account, no ads, no tracking. Everything streams from archive.org.

Longplay is an independent app. It is not affiliated with or endorsed by the Internet Archive.

## Building

Requirements: JDK 17 and the Android SDK (API 35). Point `local.properties` at your SDK (`sdk.dir=...`), then:

```
./gradlew assembleDebug        # app/build/outputs/apk/debug/
./gradlew testDebugUnitTest    # unit tests
```

Rows, sections and seasonal rows are configured in `app/src/main/assets/browse_config.json`.

## License

Copyright (C) 2026 James Waite

This program is free software: you can redistribute it and/or modify it under the terms of the GNU General Public
License, version 3 only, as published by the Free Software Foundation (SPDX: `GPL-3.0-only`). See [LICENSE](LICENSE).

This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied
warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
