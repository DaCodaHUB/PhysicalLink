# PhysicalLink library release preparation

Status: **PhysicalLink 0.9.0 Beta**, prepared for its first public beta release;
nothing published. Work is on `feature/ble-protocol-v2`. The manifest version is
exactly **0.9.0**. Package version and BLE wire protocol version are independent;
the BLE wire version remains **2**. Beta does not imply production reliability.

## Release audit and decisions

The initial package was 0.1.0 with no license or author metadata, no scoped
exports, and example/documentation links pointing outside the distributable.
No local Git tags existed. The checkout's `origin` confirms the repository URL
`https://github.com/DaCodaHUB/PhysicalLink.git`; no URL or email was invented.

The user approved MIT, copyright (c) 2026 Dang Le, public maintainer Dang Le.
That license applies to this library directory and its packaged examples, not
a blanket relicensing of the Android app or all monorepo contents. Dependency
licenses stay separate; see the packaged `THIRD_PARTY_NOTICES.md`.

The intended PlatformIO owner is **dannypov**, but account creation and username
confirmation are explicitly pending. A read-only `pio pkg search
"name:PhysicalLink"` returned no results during preparation. This is not a name
reservation or proof of account ownership; repeat the lookup before publication.

## Readiness and blockers

Resolved: license/maintainer metadata, standalone beginner and real-sensor
examples, beginner documentation, exact NimBLE dependency, archive export rules,
and packaging helper using PlatformIO's actual package command instead of a
recursive ZIP that bypasses export rules.

Publication blockers: create the registry account, confirm its actual username
and publishing rights, confirm the final package name under that owner, approve
the final Beta release/tag and intended release commit. No credentials or
account access were requested or changed.

Production reliability remains unverified on hardware: ATT offset/long-read
interleaving at MTU23, simultaneous reads and publications, repeated disconnect
and end/begin cycles, and extended heap/stack monitoring. Follow the existing
[test/metadata_transport/LIVE_TRANSPORT.md](test/metadata_transport/LIVE_TRANSPORT.md)
hardware procedure with the real sensors and 16-sensor stress example before
calling this Beta hardware-qualified. Existing host tests cover modeled pinning and
lifecycle behavior; they cannot prove RF delivery or long-run memory stability.

Optional improvements: a release CI job, more ESP32 variants/core versions after
validation, automated checks preventing the packaged example copies from drifting,
and broader hardware soak testing. No repository split is required. The existing
stress harness stays a development example because it uses internal transport
headers and a provisioned test identity; it is excluded from the public package.
Basic and real_sensors use only the public PhysicalLink API, with Arduino/Wire/ADC
sensor acquisition outside the library. No extra sensor package is required.

## Supported environment and public API

Tested build: ESP32-WROOM-32 board `esp32dev`, `espressif32@7.1.3`, Arduino
framework package `4.20017.260907+sha.dcc1105b`, NimBLE-Arduino **2.5.1 exactly**.
The backend contains a compile-time version guard and requires EATT disabled.
The manifest's Arduino/espressif32 categories do not validate other boards.

Only `PhysicalLink.h` is the supported maker API: constructor, `addSensor`,
`begin`, `update`, `unavailable`, `poll`, `end`, and `isRunning`, plus `PLResult`.
The implementation headers must remain in the package for compilation but are
not promised as a public API. Registration supports 1-16 arbitrary numeric
sensors, copied metadata, stable sensor IDs and factory-derived board identity.
One owning application task/peer/ATT bearer is supported. The packaged README
and API guide document startup checks, finite values, units, unavailable states,
safe stopping/retry, and the Android dashboard requirement.

## Local packaging and regression commands

Run these sequentially from `firmware/esp32sensors` (no upload targets):

```sh
cmake -S test/sensor_registry -B .pio/registry-tests
cmake --build .pio/registry-tests --config Release
ctest --test-dir .pio/registry-tests -C Release --output-on-failure
pio run
pio run -e basic
pio run -e stress-test
```

From `firmware/esp32sensors/lib/PhysicalLink`, pack to a temporary directory
outside this repository:

```sh
pio pkg pack --output <external-output-directory>/PhysicalLink-0.9.0.tar.gz
tar -tzf <external-output-directory>/PhysicalLink-0.9.0.tar.gz
```

On Windows when PlatformIO is not on PATH, its usual executable is
`$env:USERPROFILE/.platformio/penv/Scripts/pio.exe`; invoke it with `&`.
To exercise the standalone smoke sketch with an independently packed archive:

```sh
python test/standalone/prepare_consumer.py --output <fresh-external-project>
pio run -d <fresh-external-project>
```

For the two packaged examples, copy each example into a fresh external project's
`src/` (including `Bme280.h` for real sensors), copy the new tarball beside
`platformio.ini`, and use the configuration in the library README. Build both;
do not substitute `firmware/ConsumerTest/package/PhysicalLink.zip`.
The user's actual existing test project is `firmware/ConsumerTest/`, not
`firmware/PhysicalLinkConsumerTest/`; it was inspected but not modified.

The archive allowlist includes 19 source/header files, 5 root metadata/license/
documentation files, and 5 example files. Build outputs, machine settings,
private design documents, local archives, Android source, and the internal stress
harness must be absent. Inspect the exact archive, not only the manifest.

## GitHub preparation

Suggested Beta tag: `physicallink-v0.9.0` (not created). Mark the eventual GitHub
release as a prerelease/Beta; do not add a version suffix to the manifest.
Product description: "Register 1-16
numeric ESP32 sensors and visualize live readings in the PhysicalLink Android
app over BLE."

Keep the monorepo. Publish only the standalone library tarball as a release asset;
GitHub's automatic source-code archives contain the monorepo and are not the
PlatformIO dependency. Do not tell consumers to install the monorepo root Git URL.
There is no library manifest at its root. Use the owner-qualified registry package
or the standalone archive. Draft release notes are in
[RELEASE_NOTES.md](RELEASE_NOTES.md). A public monorepo release also needs a check
of all tracked monorepo content; the archive audit covers only the library asset.

## PlatformIO publication instructions (NOT executed)

1. Create a PlatformIO account and confirm that its username is `dannypov` (or
   replace that owner below). Authenticate with `pio account login`; enter the
   password interactively. For CI, use a securely injected `PLATFORMIO_AUTH_TOKEN`,
   never a committed token or token printed in a log.
2. Confirm owner/organization publishing rights and check the name again:
   `pio pkg search "owner:dannypov name:PhysicalLink"`.
3. Complete the release-readiness checks and approve the 0.9.0 Beta release.
   Keep the manifest at `0.9.0` and retain Beta labeling. Pack the final archive,
   inspect it, and rebuild both external examples from that exact archive.
4. After separate explicit publication approval, the intended command is:

   ```sh
   pio pkg publish <external-output-directory>/PhysicalLink-0.9.0.tar.gz --owner dannypov --type library
   ```

5. Verify processing and visibility after publication:

   ```sh
   pio pkg show dannypov/PhysicalLink@0.9.0
   ```

   Then create a fresh consumer with the configuration below and build it. Inspect
   the dependency graph/version and verify that both examples compile without any
   path into the monorepo. Confirm the package's license, examples, and version on
   its registry page. No registry verification can be completed before publishing.

   ```ini
   [env:esp32dev]
   platform = espressif32@7.1.3
   board = esp32dev
   framework = arduino
   lib_deps = dannypov/PhysicalLink@0.9.0
   ```

Do not reuse a published name/version: PlatformIO does not allow reuse even after
unpublishing. Increment PATCH for compatible fixes, MINOR for compatible public
API additions, and MAJOR for breaking API/compatibility changes. For later
prereleases, use distinct versions and visible Beta/RC labels. Package SemVer
must not silently change the
BLE wire version, UUIDs, identities, or Android persistence. Pin the exact
NimBLE version and re-audit ATT assumptions before any dependency upgrade.

Official references:
[package contents/pack](https://docs.platformio.org/en/latest/core/userguide/pkg/cmd_pack.html),
[export patterns](https://docs.platformio.org/en/latest/manifests/library-json/fields/export/include.html),
[publish](https://docs.platformio.org/en/stable/core/userguide/pkg/cmd_publish.html),
[authentication](https://docs.platformio.org/en/latest/core/userguide/account/cmd_login.html).

## Exact inspected Beta archive

Packed from the standalone library using `pio pkg pack`. Exactly 29 files,
matching the export allowlist and current source bytes. No links, traversal,
machine paths, old firmware paths, credential-pattern findings, or extra files.

SHA-256: `d0fbc7b504dd08d7a831d692fae594ae45c75689025d6464d54e09f268997821`

```text
API.md
LICENSE
README.md
THIRD_PARTY_NOTICES.md
examples/basic/README.md
examples/basic/main.cpp
examples/real_sensors/Bme280.h
examples/real_sensors/README.md
examples/real_sensors/main.cpp
library.json
src/BleDriver.h
src/BleTransport.cpp
src/BleTransport.h
src/DeviceIdentity.cpp
src/DeviceIdentity.h
src/Metadata.cpp
src/Metadata.h
src/MetadataSession.cpp
src/MetadataSession.h
src/NimbleBleDriver.cpp
src/PhysicalLink.cpp
src/PhysicalLink.h
src/ProtocolCodec.cpp
src/ProtocolCodec.h
src/ProtocolWire.h
src/SensorRegistry.cpp
src/SensorRegistry.h
src/SensorState.cpp
src/SensorState.h
```

## Proposed manifest

This is the proposed 0.9.0 Beta manifest. Do not silently substitute a different
manifest into an already inspected archive. Package version 0.9.0 does not
change BLE wire protocol version 2.

```json
{
  "name": "PhysicalLink",
  "version": "0.9.0",
  "description": "Beta: register 1-16 numeric sensors on an ESP32 and visualize readings in the PhysicalLink Android dashboard over BLE.",
  "keywords": ["ble", "esp32", "sensors", "dashboard", "physicallink"],
  "authors": [{ "name": "Dang Le", "maintainer": true }],
  "license": "MIT",
  "repository": {
    "type": "git",
    "url": "https://github.com/DaCodaHUB/PhysicalLink.git"
  },
  "homepage": "https://github.com/DaCodaHUB/PhysicalLink",
  "frameworks": "arduino",
  "platforms": "espressif32",
  "headers": "PhysicalLink.h",
  "dependencies": { "h2zero/NimBLE-Arduino": "2.5.1" },
  "export": {
    "include": [
      "library.json", "README.md", "API.md", "THIRD_PARTY_NOTICES.md", "LICENSE",
      "src/*.h", "src/*.cpp",
      "examples/basic/main.cpp", "examples/basic/README.md",
      "examples/real_sensors/main.cpp", "examples/real_sensors/Bme280.h",
      "examples/real_sensors/README.md"
    ]
  }
}
```

## Files changed in this preparation

- Library `library.json` and `README.md`.
- Added library `LICENSE`, `API.md`, and `THIRD_PARTY_NOTICES.md`.
- Added packaged `examples/basic/{main.cpp,README.md}` and
  `examples/real_sensors/{main.cpp,Bme280.h,README.md}`. Source copies are
  byte-identical to the existing firmware examples.
- Updated `test/standalone/prepare_consumer.py` and `platformio.ini` to use
  `pio pkg pack` and a tarball rather than an unrestricted recursive ZIP.
- Added this preparation report and draft `RELEASE_NOTES.md`.

No library implementation/header, original example, default environment, Android
file, golden fixture, `.gitignore`, or existing consumer-project change was made.
No commit, push, tag, registry publish, public GitHub release, or firmware upload
was performed. There is no need to create or split a standalone Git repository.

## Recorded source regression results

Before this Beta metadata update, the release-readiness run passed all 45 native
tests, default/basic/stress-test firmware builds, both external example builds,
the standalone-helper smoke build, and all 180 Android unit tests. Those checks
used the previous package metadata. Firmware implementation, public API, original
examples, Android, and shared protocol fixtures have not changed in this update.
The new 0.9.0 Beta archive is validated separately below; previous archive hashes
and installed-version evidence do not describe the new artifact.

Hardware validation remains outstanding: ATT long-read offset interleaving,
RF delivery/timing, repeated restart recovery, and long-run heap/stack behavior.
The intended owner remains dannypov, pending account creation and username
confirmation. Nothing has been committed, tagged, pushed, published, or uploaded.

## 0.9.0 Beta validation

- Native CTest rerun: 45/45 passed against the unchanged protocol fixtures.
- PlatformIO pack: passed; library metadata is 0.9.0 and explicitly labeled Beta.
- New external archive consumer: basic and real-sensor examples both passed.
- Both environments installed PhysicalLink 0.9.0 with NimBLE-Arduino 2.5.1.
- Inspected 491 compiler dependency files: no dependency on the original
  repository or old firmware directory.
- Archive: 29 allowlisted files; packaged bytes match current library source.
- Manifest embedded in this report matches the actual manifest and archive.
- No active release-preparation references to the superseded package version
  remain. Unrelated tool/plugin version numbers and old local artifacts are not
  PhysicalLink release metadata and have been left unchanged.
- No firmware implementation, public API, Android source, identity, persistence,
  protocol fixture, default target, or ignore-rule changes were made.
- Android and repository firmware builds were not repeated for this metadata-only
  update; their earlier passing results are recorded separately above.
- `git diff --check` passed. The actual index is empty; no archive or generated
  build artifact appears in Git status. No commit, tag, push, publish, or upload.

The archive is retained outside Git at
`%TEMP%/PhysicalLink-beta-0.9.0-pzn51hqs/PhysicalLink-0.9.0.tar.gz`.
Hardware reliability checks and registry account/username confirmation remain
pending. Beta status does not change BLE wire protocol version 2.
