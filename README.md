<div align="center">

# Longplay

**Classic film, TV and radio from the Internet Archive, on your TV.**

[![License: GPL-3.0-only](https://img.shields.io/badge/License-GPL--3.0--only-blue?style=for-the-badge)](LICENSE)
[![Android TV](https://img.shields.io/badge/Android%20TV-API%2023%2B-3DDC84?style=for-the-badge&logo=android&logoColor=white)](#building)
[![Buy me a coffee](https://img.shields.io/badge/Buy%20me%20a%20coffee-FFDD00?style=for-the-badge&logo=buymeacoffee&logoColor=black)](https://buymeacoffee.com/519green)

</div>

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

## Support

Longplay is free, with no ads and no tracking. If it has made a few evenings better, you can buy me a coffee:

<a href="https://buymeacoffee.com/519green"><img src="https://cdn.buymeacoffee.com/buttons/v2/default-yellow.png" alt="Buy me a coffee" height="48"></a>

Bug reports and ideas are just as welcome. To support the archive itself, donate at
[archive.org/donate](https://archive.org/donate).

## License

Copyright (C) 2026 James Waite

This program is free software: you can redistribute it and/or modify it under the terms of the GNU General Public
License, version 3 only, as published by the Free Software Foundation (SPDX: `GPL-3.0-only`). See [LICENSE](LICENSE).

This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied
warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
