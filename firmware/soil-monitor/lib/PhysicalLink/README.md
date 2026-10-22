# PhysicalLink sensor registry and capabilities

Sprint 5B.1 implements a transport-independent numeric sensor registry. Sprint
5B.2 adds a bounded capability serializer that reads the same registry. Both
require C++11 and the standard library, with no Arduino, NimBLE, sensor-driver or
Android dependencies. The existing firmware does not yet use these components;
its BLE behavior is unchanged.

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

Because `main.cpp` intentionally does not include the new components, PlatformIO's
dependency finder does not link them into the firmware yet. Both production
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
persistence, BLE integration, protocol v2 transport, metadata paging,
fragmentation, or snapshots. Registry registration remains independent of any
future BLE lifecycle. No `PhysicalLink` dashboard facade or new BLE library is
introduced.

Next integration steps: review and freeze the transport-specific metadata
mapping, build a separate adapter that consumes registry metadata, and test its
payload limits and Android compatibility explicitly before any firmware wiring.
Keep the existing v1 firmware contract intact throughout that later work.
