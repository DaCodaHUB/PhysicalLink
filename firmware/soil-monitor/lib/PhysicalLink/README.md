# PhysicalLink ESP32 library

Sprint 5B.1 implements a transport-independent numeric sensor registry. Sprint
5B.2 adds a bounded capability serializer that reads the same registry. Both
require C++11 and the standard library, with no Arduino, NimBLE, sensor-driver or
Android dependencies. Sprint 5B.3 connects the registry to the existing firmware
through the separate fixed-profile `LegacyBleAdapter` described below. Sprint
5B.4 adds the public `PhysicalLink.h` API; makers should start here.

## Public API (Sprint 5B.4)

Include only `PhysicalLink.h` in maker code. The sketch owns GPIO/I2C setup,
sensor drivers and acquisition. The library owns registration, legacy metadata,
four GATT characteristics, advertising and BLE shutdown. No sensor-driver
headers or Adafruit dependencies occur in the library sources.

```cpp
#include <Arduino.h>
#include <PhysicalLink.h>

PhysicalLink dashboard("My garden");
bool ready = false;

void setup() {
    // Initialize your hardware here, even if some probes are absent.
    if (dashboard.addSensor("soil", "Soil", "raw") != PLResult::Ok ||
        dashboard.addSensor("temperature", "Temperature", "C") != PLResult::Ok ||
        dashboard.addSensor("humidity", "Humidity", "%") != PLResult::Ok ||
        dashboard.addSensor("pressure", "Pressure", "hPa") != PLResult::Ok) return;
    ready = dashboard.begin() == PLResult::Ok;
}

void loop() {
    if (!ready) return;
    dashboard.poll(); // Call frequently; production code should log errors.
    static uint32_t previous = millis() - 1000;
    const uint32_t now = millis();
    if (uint32_t(now - previous) < 1000) return;
    previous = now;
    // Replace with actual acquisitions; these are illustrative values only.
    dashboard.update("soil", 2048);
    dashboard.update("temperature", 23.5);
    dashboard.update("humidity", 45.0);
    dashboard.unavailable("pressure"); // Probe missing/failed; never invent zero.
    dashboard.poll();
}
```

The working BME280/soil sketch is `../../src/main.cpp` relative to this README.
It checks registration, startup, update and poll results. It retains GPIO 34,
I2C 21/22, BME address 0x76, hPa conversion and nominal one-second acquisition,
using a millis deadline instead of sleeping between samples.

`projectName` is copied, nonempty UTF-8, at most 64 bytes. Invalid constructor
input is reported by begin(). It is reserved local configuration in this v1
API: the advertising name and manifest device field are always **PhysicalLink
Soil**. Registered display names are stored but v1 has no display-name field.
IDs and units are copied and become the legacy manifest entries in registration
order. No maker-written JSON is needed.

Exactly four channels are supported: soil/raw, temperature/C, humidity/%,
pressure/hPa. Valid registry metadata may be registered for other channels,
but begin() rejects any unsupported, incomplete, extra or incompatible profile
before initializing BLE. There is no sensor removal; recreate an instance to
replace an erroneous accepted registration. The internal serializer is unchanged.

All methods return `PLResult` except the constructor/destructor and isRunning().

| Result | Meaning |
| --- | --- |
| Ok | Operation accepted/completed; update is not a delivery acknowledgment |
| InvalidProjectName | Null, empty, malformed UTF-8 or >64-byte project name |
| InvalidId, InvalidName, InvalidUnit | Registration metadata fails registry validation; malformed update IDs return InvalidId |
| DuplicateId, CapacityExceeded | Duplicate registration or registry capacity reached |
| UnsupportedProfile | begin requires the exact four legacy IDs and units |
| MetadataTooLarge | Metadata exceeds the BLE 512-byte attribute bound |
| UnknownSensor | Well-formed update/unavailable ID is not registered |
| InvalidValue | Nonfinite, soil outside integer 0..4095, or decimal text exceeds 20 bytes |
| NotRunning | update, unavailable or poll called while stopped |
| AlreadyRunning | begin called on an already running instance |
| Busy | Registration while running/cleanup pending; another instance or external NimBLE owner |
| OutOfMemory | Public implementation or transport allocation failed |
| BleStartFailed | BLE initialization, service/characteristic setup, server or advertising start failed |
| BleStopFailed | Host shutdown failed; retain ownership and retry end() |
| SendFailed | A publication failed; pending latest values remain for next poll |

Lifecycle rules:

- Construct/register first. Constructor has no BLE side effects. Use one owner
  task for all calls across all instances; no ISR or concurrent-call support.
- begin() validates registration and metadata before any advertising. Only one
  PhysicalLink instance can own BLE. An externally initialized NimBLE stack
  returns Busy and is never deinitialized by this library.
- Registration freezes while running. Successful begin resets all readings to
  unavailable. update/unavailable are accepted only while running.
- update caches finite numeric readings. Repeated updates coalesce to the
  latest value before poll; calling update again with the same acquired value
  still schedules a notification to preserve freshness.
- InvalidValue marks that known channel unavailable and schedules an empty
  readable attribute without notification, preserving Sprint 5B.3 failure
  behavior. Other validation failures do not mutate readings. A later valid
  update restores publication. v1 still relies on Android freshness timeouts.
- poll publishes at most four dirty channels, with no delay, hardware reads,
  radio-acknowledgment waits or invented sampling schedule. It is bounded work,
  not a hard realtime guarantee; NimBLE calls and string allocation take time.
  A poll without pending updates emits no notifications.
- NimBLE owns its default callbacks. Its built-in advertiseOnDisconnect
  restarts advertising; no application callback pointer is registered or freed.
  Reconnect reads the current characteristic cache, without refreshing samples.
- end() disables disconnect advertising restart, stops advertising and stops
  the NimBLE host (including disconnecting peers), then frees server/GATT
  resources. Repeated end succeeds. Registrations survive; begin can restart.
  Startup failure invokes the same cleanup path and normally permits retry.
- The host is stopped before GATT deletion, because installed NimBLE deinit(true)
  otherwise deletes resources even when stopping fails. On stop failure the
  object reports stopped, retains BLE ownership, and blocks startup until end()
  succeeds. Destruction invokes end(); if host stop still fails it deliberately
  retains the implementation/transport rather than freeing live resources.
  Call end() explicitly to observe and retry this exceptional failure.
- Heap is used for the private implementation, transport and temporary numeric
  strings. The static RAM report therefore excludes the owned registry now on
  the heap. OutOfMemory covers explicit nothrow allocations; allocation failures
  inside NimBLE/std::string follow the platform's allocator behavior.

The private transport boundary separates the public API/cache from NimBLE and
supports native tests with a recording transport. A future v2 backend can reuse
registration and update logic, but will require explicit protocol selection,
identity and typed/status contracts. This sprint exposes no v2 mode, paging,
fragmentation, snapshots, facade options or new Android dependency.

Manual hardware checks for this refactor (not performed in Sprint 5B.4):

- Verify discovery by the original name, unchanged UUIDs, byte-identical
  298-byte metadata and Android long reads at MTU 23/larger MTUs.
- Verify initial reads and all four subscriptions, sample cadence, decimal
  representation, hPa conversion, and continued sampling without subscribers.
- Boot with BME absent; verify soil continues and environmental values remain
  unavailable. Inject invalid readings, check stale behavior and valid recovery.
- Disconnect/reconnect repeatedly; verify advertising restart and current reads.
- Exercise end while connected, repeated end, begin after end, and destruction
  of a local instance; check disconnect and no advertising after shutdown.
- Run for an extended period; measure heap, largest block, stack and poll latency.

Automated Sprint 5B.4 validation: **34/34 CTest cases passed** (28 existing
registry/serializer/legacy cases and six public-API suites). Native API tests
compile the production facade, registry and adapter against a recording
transport; they cover registration/errors, profile validation before transport
startup, registration order, repeated begin, singleton ownership, startup
failure cleanup/retry, send retry, idempotent end, shutdown failure/retry,
destructor cleanup, unavailable recovery and restart reset. They do not simulate
RF or execute the real NimBLE stack.

Standalone Xtensa C++11 compilation passed for SensorRegistry,
CapabilitySerializer, LegacyBleAdapter and PhysicalLink with warnings as errors,
exceptions and RTTI disabled. PlatformIO esp32dev also compiled the real NimBLE
transport and sketch successfully (Espressif32 7.1.3, Arduino package
4.20017.260907+sha.dcc1105b, GCC 8.4.0, NimBLE 2.5.1, BME280 2.3.0).
Final build reports 36,652 bytes static RAM and 643,393 bytes flash. This is not
a runtime memory measurement; the registry has moved from static RAM to owned
heap storage. No total heap/stack improvement is claimed.

The user reports Sprint 5B.3 hardware verification. That does not verify this
Sprint 5B.4 refactor; no new hardware upload/execution or Android testing was done.

## Usage

PlatformIO's private-library discovery supports `#include <SensorRegistry.h>`.
Other builds should add `src/` to their include path and compile
`src/SensorRegistry.cpp`.

```cpp
#include <SensorRegistry.h>

using physical_link::SensorRegistry;
SensorRegistry sensors; // static lifetime is useful on small task stacks

bool registerSensors() {
    return sensors.addSensor("temperature.indoor", "Indoor", "C") ==
               SensorRegistry::Result::Ok &&
           sensors.addSensor("temperature.outdoor", "Outdoor", "C") ==
               SensorRegistry::Result::Ok;
}

bool acceptReading(double reading) {
    return sensors.update("temperature.indoor", reading) ==
           SensorRegistry::Result::Ok;
}

void markProbeMissing() {
    // Handle a non-Ok result if IDs are supplied dynamically.
    sensors.unavailable("temperature.outdoor");
}

const SensorRegistry::Sensor* indoorReading() {
    return sensors.getSensor("temperature.indoor");
    // Consumers must check pointer != nullptr and available before using value.
}
```

IDs identify individual channels, so identical names/units and repeated
measurement types are supported. The maker chooses IDs that remain stable across
boots and firmware updates. There is no automatic identity generation or storage
in NVS.

## Contract and bounds

| Field | Limit / behavior |
| --- | --- |
| Sensor count | Fixed 16 (`MaxSensors`), including unavailable sensors |
| ID | 1–32 ASCII bytes, `[a-z][a-z0-9_.-]{0,31}`, case sensitive |
| Display name | At most 64 UTF-8 bytes; null/empty falls back to ID |
| Unit | At most 16 UTF-8 bytes; null/empty means no unit |
| Numeric value | Finite `double`; negative values and zero are valid |
| Initial state | `available == false`, `value == 0.0` |

Limits exclude the terminating NUL. IDs, names and units are copied into fixed
arrays before a successful `addSensor()` returns. Input strings must be readable
C strings; validation scans at most the field limit plus one byte, and rejects
overlength strings instead of truncating. Embedded NUL ends the input. Name/unit
validation rejects malformed UTF-8, including overlong encodings, surrogates and
out-of-range code points; it does not normalize Unicode or sanitize display
control characters.

`addSensor`, `update` and `unavailable` return `SensorRegistry::Result`:
`Ok`, `InvalidId`, `InvalidName`, `InvalidUnit`, `DuplicateId`,
`CapacityExceeded`, `UnknownSensor`, or `InvalidValue`. Registration validates
ID/name/unit, then duplicates, then capacity. Updates validate ID, then existence,
then finite value. Every rejected operation leaves registry state unchanged.
`getSensor()` returns null for malformed or unknown IDs. `getSensorAt(index)`
returns a read-only record in registration order, or null when index >= size().
`size()` counts all registered channels, not just those currently available.

`update()` replaces the latest finite numeric value and marks it available.
NaN and positive/negative infinity are rejected without changing value or
availability. `unavailable()` is idempotent and marks the channel unavailable
while retaining its last finite value for inspection. That retained value is
historical and must not be displayed as a current successful reading. A later
valid update restores availability. No timestamps, freshness logic or error
reason codes are included in this small registry step.

Storage is an inline `std::array` of 16 records; construction and all operations
perform no dynamic allocation. Lookups are bounded linear scans (at most 16
records), and metadata validation is bounded by the public length constants.
Both lookup methods expose a read-only live view into owned storage. Its address stays
stable across registration and updates until the registry is destroyed; it is
not an immutable snapshot. The class cannot be copied or moved. Input buffers
can be changed or released after their accepting call returns.

Use one owner task or external synchronization for all calls and borrowed views.
There are no locks, callbacks or ISR guarantees. For task-stack budgeting,
`sizeof(SensorRegistry)` was **2184 bytes on the tested Windows x64 compiler**;
target alignment can differ. Prefer static/global lifetime on ESP32. Compile
without unsafe floating-point flags such as `-ffast-math`, which can invalidate
finite-number checks.

## Generated capabilities (Sprint 5B.2)

`CapabilitySerializer::serialize()` reads the registry directly using indexed
read-only access. It does not retain the registry, create a second metadata list,
or allocate memory. Include `CapabilitySerializer.h` and compile
`src/CapabilitySerializer.cpp` alongside the registry source when building
outside PlatformIO.

The internal capability schema is version 1:

```json
{"schemaVersion":1,"sensors":[{"id":"temperature.indoor","name":"Indoor","unit":"C","type":"number"}]}
```

The root has exactly `schemaVersion` (integer 1) and `sensors` (array). Each array
entry has `id`, `name`, `unit`, and `type`, all strings. The registry currently
accepts only numeric channels, so type is always `number`. Names and units are
the registry's stored values, including resolved name fallback and empty units.
All sensors are emitted in registration order, including unavailable channels.
No values, availability, device identity, UUIDs or transport bindings appear.
An empty registry produces `{"schemaVersion":1,"sensors":[]}`.

Encoding is compact and deterministic: field order is fixed, quotation marks and
backslashes are escaped, and each non-NUL control byte U+0001–U+001F is emitted as
lowercase `\u00xx`. NUL cannot occur inside stored C-string metadata; registration
treats it as the string terminator. Forward slashes remain literal. Validated
UTF-8 bytes are preserved without normalization. No runtime reading or
availability change affects the generated metadata.

```cpp
#include <CapabilitySerializer.h>
using physical_link::CapabilitySerializer;

// Caller chooses its budget; large registries may require more than 1024 bytes.
static char capabilities[1024];

bool serializeCapabilities() {
    auto result = CapabilitySerializer::serialize(
        sensors, capabilities, sizeof(capabilities));
    if (result.status != CapabilitySerializer::Status::Ok) {
        // Do not consume capabilities: it has NOT been overwritten on failure.
        // result.requiredCapacity reports the needed size, including the NUL.
        return false;
    }
    // capabilities is complete JSON, NUL-terminated; result.bytesWritten
    // excludes the terminator and is suitable for a future transport adapter.
    return true;
}
```

The caller supplies a writable buffer and capacity in bytes; the buffer must not
overlap registry storage. The serializer first measures the exact escaped JSON,
then writes only if the complete document plus NUL fits. It returns:

| Status | Meaning |
| --- | --- |
| `Ok` | Full JSON and final NUL written; `bytesWritten` excludes NUL |
| `BufferTooSmall` | Capacity insufficient; `bytesWritten == 0`, output unchanged |
| `InvalidBuffer` | Null output with nonzero capacity; `bytesWritten == 0` |

`requiredCapacity` always reports the exact needed byte count including NUL.
`serialize(registry, nullptr, 0)` is a count-only query returning `BufferTooSmall`.
On failure there is no partial document, clearing, truncation or other buffer
mutation. Previous buffer content may therefore still be present; callers must
check status before using it. A capacity exactly equal to the JSON byte length
is insufficient because the NUL also needs space. The same owner-task or
external-lock discipline as the registry applies: do not mutate it during the
two serialization passes. There are no callbacks between passes.

Buffer limits, for the current fixed registry bounds:

| Case | Capacity including NUL |
| --- | --- |
| Empty registry | 33 bytes |
| Exact requested document | Reported by `requiredCapacity` |
| Worst-case 16 sensors | 8960 bytes (`CapabilitySerializer::MaxBufferSize`) |

The worst case includes 32-byte IDs, 64-byte names and 16-byte units with all
name/unit bytes requiring six-byte control-character escapes. Tests construct
this bound exactly. The serializer does not reserve an 8960-byte internal
buffer; it uses constant-sized working state and traverses at most 16 records
twice. A caller that reserves the full worst-case buffer should give it static
storage rather than put it on a small ESP32 task stack.

**Compatibility:** `schemaVersion: 1` versions this internal representation;
it is not the BLE v1 `version: 1` schema, nor the proposed full BLE v2 schema.
The output is not directly accepted by the existing Android v1 parser. No
characteristic UUIDs are generated. BLE-specific metadata mapping, service and
characteristic binding, and transport payload-size checks belong to a future
adapter integration. A document fitting this caller buffer does not imply it
fits one BLE attribute or notification. No paging, fragmentation or snapshots
are implemented here.

## Automated validation

No test framework download or hardware is required. With CMake, a host C++
compiler and Python 3 (standard-library JSON parser, tests only), run from the
firmware project directory:

```sh
cmake -S test/sensor_registry -B .pio/registry-tests
cmake --build .pio/registry-tests --config Release
ctest --test-dir .pio/registry-tests -C Release --output-on-failure
```

On Windows with Visual Studio 2022, the tested configure command was:

```sh
cmake -S test/sensor_registry -B .pio/registry-tests -G "Visual Studio 17 2022" -A x64
```

The original 14 registry CTest cases cover registration, copied ownership, optional metadata,
duplicates, invalid/null/overlength IDs, byte boundaries, valid/malformed UTF-8,
finite numeric extremes, NaN/infinity rejection, availability transitions,
unknown sensors, full capacity, multiple same-type sensors and stable views.
Sprint 5B.2 adds 12 C++ serializer/access tests and one Python JSON round-trip
test, for **27 total cases**. These cover empty/one/multiple channels, registration
order, every representable JSON control byte, quotes/backslashes, UTF-8,
maximum-length fields and full sensor count, exact worst-case escaping size,
all insufficient capacities for a sample document, exact-fit capacity, null
buffers, determinism, metadata preservation and independence from readings.
The Python test strictly decodes actual serializer output and checks four
complete documents against expected metadata, rejecting duplicate JSON keys.

Checks remain active in Release builds. CMake compiles the production sources
with warnings treated as errors. These are host tests run through CTest, not
PlatformIO device tests; `pio test` is not configured for this suite.

Compile the existing firmware separately:

```sh
pio run -e esp32dev
```

In Sprint 5B.2 the firmware did not yet include these components. Both production
sources were additionally cross-compiled with the installed ESP32 toolchain:

```sh
xtensa-esp32-elf-g++ -std=gnu++11 -Wall -Wextra -Werror -fno-exceptions -fno-rtti -c lib/PhysicalLink/src/SensorRegistry.cpp -o .pio/SensorRegistry.o
xtensa-esp32-elf-g++ -std=gnu++11 -Wall -Wextra -Werror -fno-exceptions -fno-rtti -c lib/PhysicalLink/src/CapabilitySerializer.cpp -o .pio/CapabilitySerializer.o
```

Validated in Sprint 5B.2: 27/27 host tests, both ESP32 object compilations, and
the existing `esp32dev` firmware build (Espressif 32 7.1.3, Arduino package
4.20017.260907+sha.dcc1105b, Xtensa GCC 8.4.0, NimBLE 2.5.1). Dependencies remain
as configured in the existing project. No hardware execution or BLE verification
was performed.

## Deliberate limitations

Only numeric channels and binary availability are implemented. Capacity is a
fixed 16 in this step; the earlier proposal's configurable capacity up to 64 is
future work. There is no removal, scheduling,
persistence, protocol v2 transport, metadata paging,
fragmentation, or snapshots. Registry registration remains independent of any
future BLE lifecycle. The public facade and BLE bridge are described below; the registry itself
continues to have no BLE dependency.

Next integration steps: review and freeze the transport-specific metadata
mapping, build a separate adapter that consumes registry metadata, and test its
payload limits and Android compatibility explicitly before any firmware wiring.
Keep the existing v1 firmware contract intact throughout that later work.

## Legacy BLE integration (Sprint 5B.3)

`LegacyBleAdapter` is a temporary Android v1 bridge, independent of NimBLE.
It reads the registry directly, requires exactly soil/raw, temperature/C,
humidity/%, pressure/hPa, and preserves registration order. Unsupported IDs,
missing channels and incompatible units reject the entire configuration.
Duplicate registration is rejected by SensorRegistry; the adapter additionally
checks duplicate bindings. Sensor display names are intentionally absent from
the original v1 schema. Device name remains PhysicalLink Soil.

Mappings are soil=5678, temperature=5679, humidity=567a, pressure=567b.
The firmware uses these same mappings to create its four fixed characteristics;
service 1234 and metadata characteristic 5670 are unchanged. Metadata is 298
bytes excluding NUL, exactly matching the previous literal. Output requires 299
bytes; failed serialization leaves the caller buffer unchanged. The adapter
checks the 512-byte attribute limit independently of caller buffer size. The
validated fixed profile cannot currently grow beyond 298 bytes; tests exercise
the production size predicate at 512/513 and size_t maximum, rather than
pretending an oversized compatible four-sensor configuration exists.

Valid soil readings retain integer ADC formatting (0..4095); environmental
readings retain std::to_string six-decimal formatting. Firmware feeds acquired
values into the registry, marks rejected nonfinite samples unavailable, and
formats only available registry values. Failed/unavailable channels have empty
readable attributes and emit no notification. No numeric sentinel, null/error
JSON or new v1 status protocol is introduced. This differs from the Sprint 5A
future mirror proposal's retained GATT values: empty reads avoid returning an
old sample as current. Already-connected Android clients still rely on their
existing freshness timeout because v1 cannot signal an explicit error.

BME280 initialization failure now allows BLE advertising and soil acquisition
to continue with all environmental channels unavailable. There is no automatic
BME reinitialization; recovery from initialization failure requires restart.
Runtime successful acquisitions restore availability. GPIO 34, I2C 21/22,
address 0x76, Pa-to-hPa conversion, acquisition cadence, advertising name/service,
and disconnect-triggered advertising restart are preserved. Static callbacks
are registered with deleteCallbacks=false to preserve their ownership.
An unplugged analog probe cannot be detected reliably by analogRead alone;
raw ADC results remain raw measurements, with no invented fault detection.

Validation: 28 CTest cases (all 27 registry/serializer regressions plus the legacy
profile/reading suite), separate C++11 ESP32 object compilation of all three
library sources, and PlatformIO esp32dev build. The existing NimBLE deprecated
service->start() warning remains. No Android source, internal serializer schema,
protocol v2, fragmentation, metadata paging or snapshots changed.

Final PlatformIO size report: static RAM 38,812 bytes (11.8%), flash 638,677
bytes (48.7%). No controlled source-baseline delta or runtime heap measurement
was performed. Build matrix: Espressif32 7.1.3, Arduino package
4.20017.260907+sha.dcc1105b, Xtensa GCC 8.4.0, NimBLE 2.5.1,
Adafruit BME280 2.3.0. Dependency declarations remain unchanged.

Manual acceptance still required on ESP32/current Android:

- Discover PhysicalLink Soil, read the complete 298-byte manifest at MTU 23
  and a larger MTU, and verify all original service/characteristic UUIDs.
- Verify initial reads, four subscriptions, integer/decimal formatting and
  notifications against Serial/known sensor inputs, including hPa conversion.
- Boot without BME280; verify soil works, environmental reads are empty, and
  no environmental notifications or fabricated readings appear.
- Inject nonfinite acquisition after a valid sample; verify empty reads,
  suppressed notification, Android freshness timeout, and later valid recovery.
- Disconnect/reconnect repeatedly and check advertising resumes and all initial
  reads/subscriptions work. Check long-running behavior and heap on hardware.

No hardware execution, upload or Android verification has been performed.

## Protocol v2 core codecs (Sprint 5C.2A)

Transport-independent V2Codec, V2SensorState and V2Metadata components now live
in src/. They are not used by PhysicalLink.begin() or the existing BLE backend.
Numeric records are 20 bytes; complete state images are at most 336 bytes;
metadata is bounded to 8192 bytes and pages to 512 bytes including the 16-byte
header. No v2 Bluetooth services/advertising or Android changes are enabled.

See [the core codec notes](../../test/protocol_v2/README.md) for API/ownership,
clock and sequence rules, files changed, golden fixtures, buffer limits,
specification clarifications, validation and remaining implementation work.

## Experimental v2 metadata BLE transport (Sprint 5C.2B)

A separate opt-in metadata-only sketch/configuration now registers v2 metadata
READ and page-select WRITE characteristics. Default PhysicalLink.begin() and
v1 firmware are unchanged; no state/live characteristic or notifications exist
in this experiment. The raw NimBLE 2.5.1 binding uses actual ATT offsets and
pinned page generations, restricted to one client and EATT disabled.

See [experimental transport instructions](../../test/metadata_transport/README.md)
for builds/uploads, UUIDs, page selection, MTU-23/larger-MTU manual tests,
long-read evidence and limits, lifecycle tests and remaining release gates.
No hardware verification has been performed for this transport.
