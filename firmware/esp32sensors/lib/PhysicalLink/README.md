# PhysicalLink ESP32 library

Register 1–16 numeric sensors and connect them to the PhysicalLink Android
dashboard. Include **PhysicalLink.h**; no maker-written BLE packets, GATT setup,
metadata JSON, UUIDs, or session epochs are needed. The Arduino sketch owns
GPIO/I2C, drivers, and sampling. The library has no BME280/Adafruit dependency.

## Install in an independent PlatformIO project

Use ESP32 Arduino with the validated Espressif32 platform and pinned NimBLE:

```ini
[env:esp32dev]
platform = espressif32@7.1.3
board = esp32dev
framework = arduino
monitor_speed = 115200
lib_deps = h2zero/NimBLE-Arduino@2.5.1
```

Copy this entire directory into the new project's `lib/PhysicalLink`. The
explicit dependency pin installs NimBLE before PlatformIO scans private library
sources. It also matches `library.json`'s required dependency.

Alternatively package this directory (`library.json`, `README.md`, and `src/`
at the archive root) as `PhysicalLink.zip` in your independent project and use:

```ini
[env:esp32dev]
platform = espressif32@7.1.3
board = esp32dev
framework = arduino
lib_deps = file://PhysicalLink.zip
```

PlatformIO installs the archive and its declared NimBLE dependency. Both forms
use relative/local project files, not repository-specific includes or flags.
`test/standalone/prepare_consumer.py` prepares the second form for automated
verification, with a copied library archive and an independent application.
No registry publication has been performed.

Start with [the basic synthetic example](../../examples/basic/main.cpp) or
[the real-sensor example](../../examples/real_sensors/main.cpp).

## Public API

```cpp
#include <PhysicalLink.h>
PhysicalLink device("My Greenhouse");
// setup():
//   Check device.addSensor("temperature", "Temperature", "C");
//   Check device.begin();
// loop(), on your sampling schedule:
//   Check device.update("temperature", yourTemperatureReading);
//   Or device.unavailable("temperature") if acquisition failed.
// Every loop: check device.poll().
```

All methods except constructor/destructor and `isRunning()` return `PLResult`.
`Ok` means accepted, not acknowledged by Android. Check registration and startup
before sampling; report errors through Serial as the complete examples do.

| Operation | Contract |
| --- | --- |
| Constructor | Copies nonempty UTF-8 name, max 64 bytes; no BLE side effects |
| `addSensor(id, name, unit)` | Copies stable metadata; allowed only while stopped; 1–16 numeric sensors |
| `begin()` | Validates metadata, uses stable board identity and fresh epoch, starts BLE; every reading initially unavailable |
| `update(id, double)` | Accepts finite numbers, coalesces to latest; NaN/Inf marks a known sensor unavailable and returns `InvalidValue` |
| `unavailable(id)` | Explicit unavailable state; never republishes an old number as current |
| `poll()` | Services pending commands and bounded live scheduler; no acquisition or radio waits |
| `end()` | Idempotent, stops advertising/host and releases ownership; retains registration; retry `BleStopFailed` |
| `isRunning()` | Whether updates/poll are accepted |

IDs: 1–32 ASCII bytes matching `[a-z][a-z0-9_.-]{0,31}`. Names: max 64 UTF-8
bytes (empty/null falls back to ID). Units: max 16 UTF-8 bytes (empty/null allowed).
Malformed UTF-8 is rejected; inputs are copied. Repeated measurement types and
arbitrary units are allowed. No fixed sensor profile or decimal-text value limit.

Failures distinguish `InvalidProjectName`, `InvalidId`, `InvalidName`,
`InvalidUnit`, `DuplicateId`, `CapacityExceeded`, `UnknownSensor`, `InvalidValue`,
`InvalidMetadata`, `MetadataTooLarge`, `OutOfMemory`, `NotRunning`,
`AlreadyRunning`, `Busy`, `BleStartFailed`, `BleStopFailed`, `InvalidTime`, and
`SequenceExhausted`. Bad IDs/unknown sensors do not mutate known readings.
Invalid time/sequence exhaustion requires `end()` then `begin()` to reset the
session; restore fresh samples afterwards.

## Identity, scheduling, and ownership

The persistent device UUID remains
`504c0000-0000-4000-8000-<factory eFuse MAC as 12 lowercase hex digits>`.
Keep IDs stable across updates to retain saved dashboard bindings. The existing
advertised name is **PhysicalLink**; Android discovers by service UUID. The project
name becomes the manifest display name.
Protocol version stays 2. The session epoch changes on every begin/restart.

Call all methods from one application task, never ISR or concurrently. Keep
calling poll frequently, including while disconnected. Sample on your own
millis schedule; there is no per-sample FIFO. Acquisition time/sequence is retained
while disconnected; reconnect uses state synchronization. At most one 20-byte
notification attempt per 50 ms is made, with no catch-up credit. Failed sends
remain eligible and consume the same budget. Unavailable records use the existing
status representation. Observe the clock at least every interval shorter than
2^31 ms. No history, Wi-Fi, cloud, or remote-control API is included.

One BLE owner is allowed. Externally initialized NimBLE is reported Busy and is
not deinitialized. Shutdown stops the host before freeing callbacks/resources.
If host shutdown fails, ownership/storage are retained for an `end()` retry;
destruction deliberately retains any still-live transport storage instead of
freeing callbacks. Use explicit end to observe this failure. Heap is used for
implementation/transport state and NimBLE; static build RAM is not total runtime
memory use. Bounded work does not imply hard realtime guarantees.

## Developer tests

See [firmware validation](../../README.md#validate-changes) and the
[transport guide](../../test/metadata_transport/LIVE_TRANSPORT.md). Tests include
arbitrary registration, 16-channel bounds, unavailable/recovery, startup/shutdown
failure cleanup, owner exclusion, stable identity across epochs, codecs and
unchanged binary fixture oracles. Test-only drivers do not emulate the radio.
