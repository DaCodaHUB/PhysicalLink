# PhysicalLink API and lifecycle

Include `PhysicalLink.h`; the other headers are implementation details and are
not a supported maker API. See [getting started](README.md) first.

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


For the frozen BLE contract and hardware acceptance procedure, see the
[repository transport guide](https://github.com/DaCodaHUB/PhysicalLink/blob/feature/ble-protocol-v2/firmware/esp32sensors/test/metadata_transport/LIVE_TRANSPORT.md).
