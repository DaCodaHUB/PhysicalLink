# Draft: PhysicalLink 0.9.0 Beta

Not published. Proposed tag: `physicallink-v0.9.0` (not created). This is the first
public **Beta**, not a production-stability claim. Mark the GitHub release as a
prerelease when publication is separately approved. The numeric package version
is exactly `0.9.0`, with no beta suffix.

PhysicalLink turns 1-16 numeric ESP32 sensors into a customizable Android
dashboard over BLE. Register sensors through `PhysicalLink.h`, publish finite
readings or unavailable states, and call `poll()` regularly. The Android app is
required for visualization.

## Included

- Standalone source archive with the public PhysicalLink API and exact
  NimBLE-Arduino 2.5.1 dependency.
- Beginner synthetic and BME280/soil-probe examples with beginner setup guides.
- Stable factory-eFuse-derived board identity, copied sensor metadata, safe BLE
  lifecycle handling, and coalesced live readings.
- Scoped package exports; Android source, private notes, caches, and local
  consumer archives are not part of the library download.

## Compatibility

Validated build target: ESP32-WROOM-32 (`esp32dev`), Arduino framework via
`espressif32@7.1.3`. This does not claim compatibility with every ESP32 variant.
Package version 0.9.0 is separate from BLE wire protocol version 2. UUIDs, binary
formats, sensor IDs, board identities, and Android persistence are unchanged.
The repository still defaults to the real-sensor firmware application.

## Installation

Download the standalone `.tar.gz` asset, place it next to `platformio.ini`, and
use `lib_deps = file://PhysicalLink-0.9.0.tar.gz` with the validated platform above.
After registry publication, use `dannypov/PhysicalLink@0.9.0` instead (owner
account/username still pending confirmation).
Owner-qualified registry installation is a future instruction, not an existing
published package. Do not install the monorepo's root Git URL as this library.

## Known limitations and release gates

One peer, one owning application task, one ATT bearer, no lossless historical
stream, no cloud service, and no pairing/authentication implemented here. Long
reads with ATT offset interleaving and long-run heap/stack behavior need hardware
acceptance. A successful build is not proof of radio reliability.

MIT license: copyright (c) 2026 Dang Le. Public maintainer: Dang Le.

Before issuing this release: confirm registry ownership/name and source provenance, run the
hardware acceptance procedure, pack the final 0.9.0 Beta archive, and rerun archive consumers.
See `RELEASE_PREPARATION.md` for evidence and publication steps. No tag, push,
public release, registry publish, or firmware upload was performed in preparation.
