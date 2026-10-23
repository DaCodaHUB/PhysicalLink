# Numeric v2 core codecs (Sprint 5C.2A)

These are transport-independent C++11 components in namespace
`physical_link::v2`. Nothing calls them from the current public PhysicalLink
API or v1 BLE transport. No new GATT definitions, advertising, control command
handling, Android code or identity storage are implemented.

## Files and responsibilities

- `lib/PhysicalLink/src/V2Codec.h` and `.cpp`: validated numeric record and
  complete state image encoders/decoders, plus common v2 types/status/results.
- `lib/PhysicalLink/src/V2Wire.h`: private explicit little-endian scalar helpers.
- `lib/PhysicalLink/src/V2SensorState.h` and `.cpp`: owns a separate existing
  SensorRegistry plus v2 timing/sequence state. No legacy registry mutation.
- `lib/PhysicalLink/src/V2Metadata.h` and `.cpp`: bounded manifest generation
  and metadata page encoding/header decoding.
- `test/protocol_v2/test_v2.cpp`: native binary, state, metadata, image tests.
- `test/protocol_v2/verify_fixtures.py`: independent Python JSON/struct checks
  against actual production C++ output; standard library only.
- `test/protocol_v2/fixtures/numeric-v2.json`: language-neutral golden vectors
  and input metadata, suitable for later Kotlin tests. No firmware JSON parser
  or Python dependency is introduced.
- `test/sensor_registry/CMakeLists.txt`: registers all existing and new tests.
- `lib/PhysicalLink/README.md`: links this core's usage and validation notes.

## Wire examples and fixtures

Every binary field is encoded individually, explicitly little endian. A scalar
binary64 is copied into uint64 bits then serialized byte by byte; no struct
layout or padding enters the protocol. IEEE-754 binary64 is a compile-time
requirement. Valid negative zero, subnormal and finite extremes round-trip.
Unavailable requires positive-zero bits, age ffffffff and status 1; valid
records require sequence >=1, finite numeric value and age <=fffffffe.

Valid channel 0, sequence 42, age 10, value 23.5:

```text
20 00 00 00 2a 00 00 00 0a 00 00 00 00 00 00 00 00 80 37 40
```

Unavailable channel 1, sequence 7:

```text
20 01 01 00 07 00 00 00 ff ff ff ff 00 00 00 00 00 00 00 00
```

The fixture file has 41 exact named wire vectors: three scalar records,
seven manifests and their pages (including 17 maximum-size pages), one initial
state image, one- and sixteen-sensor valid images, and wrap/recovery state
records. It also contains eight malformed record vectors and a deterministic
clock/sequence scenario. Manifest input objects identify their exact registration
order, device ID/name and epoch; `wire` values are lowercase hex bytes.
Fixture version 1 is the test-container version, not the BLE protocol major.

Golden expected values were constructed independently with Python json/struct,
not captured from the production encoder. CTest compares every actual byte
against them. The strict JSON oracle validates complete reconstructed manifests,
including required fields and ordering. The UTF-8 fixture deliberately splits
U+00E9 into c3 at manifest offset 495 and a9 at offset 496; neither page should
be individually interpreted as a complete UTF-8 JSON document.

## Usage and ownership

```cpp
#include <V2SensorState.h>
#include <V2Metadata.h>
using namespace physical_link::v2;

// Prefer static lifetime for the larger fixed buffers.
SensorState state;
Metadata metadata;

// Caller supplies/provisions these. No randomness or NVS here.
// Check every result in production; abridged error handling below.
void initialize() {
    state.addSensor("temperature.indoor", "Indoor", "C");
    state.addSensor("temperature.outdoor", "Outdoor", "C");
    state.start(UINT64_C(0xb398ca802d871ae0), 0);
    metadata.build(state.registry(),
        "710abdfc-9d05-46de-a790-7fe484816eea", "Garden",
        UINT64_C(0xb398ca802d871ae0));
}
```

Call `update(id, value, millis32)` or `unavailable(id, millis32)`. Known invalid
numeric acquisitions increment sequence and become unavailable, returning
InvalidValue; unknown/malformed IDs return UnknownSensor without changing a
sensor. Repeated successful updates or unavailable calls increment sequence,
even when unchanged. Registry retains historical finite values, but emitted
unavailable records always carry zero bits and unavailable age.

`reading(channel, millis32, Record&)` returns current state at that time.
`capture(requestId, millis32, Image&)` copies all registered channels at one
instant. Encode the detached Image with `Codec::encodeImage`. Updates after
capture do not alter it. Request 0 is reserved for an initial all-unavailable,
sequence-zero image and is rejected after any acquisition/status event. Request
ID ordering/CCCD rules belong to the future BLE session; these codecs validate
format, not BLE command history.

`Metadata::build` owns a compact manifest with exactly version/deviceId/device/
epoch/sensors and id/name/unit entries. It validates canonical lowercase UUID
syntax, nonzero numeric epoch, nonempty bounded UTF-8 project name and 1..16
registered channels. Registry registration already validates copied sensor
strings/IDs and prevents duplicates. The serializer reuses that validation and
the existing internal serializer's compact escaping rules; its schema and
implementation are unchanged. This sprint does not parse untrusted JSON in
firmware: duplicate/unknown-key rules apply to later receivers, while generated
JSON has only fixed known keys.

`page(index, output, capacity)` emits a 16-byte header plus raw JSON slice.
`decodePage` checks header fields and exact payload length, returning a borrowed
PageView into the input. A future receiver must additionally enforce expected
index, epoch, totals, page order and complete JSON schema across pages. This
method is not a transfer/reassembly state machine and does not validate JSON.

Caller-provided byte buffers must be disjoint from source objects. Failed
encodes/builds/decodes do not partially modify output objects or previous
metadata. Successful JSON build includes a local final NUL, never transmitted
in page payloads. Binary Result.requiredCapacity excludes NUL. Metadata build's
requiredCapacity includes NUL (also for MetadataTooLarge); bytesWritten excludes
it. Null output with zero capacity is a BufferTooSmall size query; null output
with nonzero capacity is InvalidBuffer. Metadata has an owned fixed-size buffer
rather than accepting an arbitrary external JSON buffer.

All state/cache/serializer operations are for one owning task, no ISR or
concurrent mutation. Capture consistency relies on this discipline, not hidden
locks. Copied Image objects are detached; PageViews are borrowed. SensorState
exposes only a const registry. Registration freezes after start; recreate the
object to change the sensor set. start with a different nonzero epoch resets
all values to unavailable and all counters/timing, retaining registration.
Caller guarantees epoch freshness and consistent epoch between state/metadata.

## Specification clarifications

No wire layout mismatch was found. The minimal design left these operational
choices implicit; this implementation makes them explicit without changing v1:

1. A 32-bit millis source cannot distinguish arbitrary multi-wrap gaps from
   backward time. Call tick/update/reading/capture at intervals strictly less
   than 2^31 ms (about 24.85 days), including while idle. Unsigned subtraction
   extends valid wraparound into uint64 elapsed time. A delta >=2^31 or uint64
   overflow returns InvalidTime without updating the clock. Multiple complete
   unobserved wraps cannot be detected and are outside this precondition.
2. Sequences may reach UINT32_MAX, but never wrap. An attempted next event
   latches SequenceExhausted and blocks all reading/capture/tick output until
   start with a different epoch. No BLE teardown is attempted by this core;
   future transport must stop/restart when this error occurs. A configurable
   smaller sequence ceiling exercises the identical exhaustion path in tests.
3. Valid sequence zero, nonzero unavailable value bits (including -0), and
   request-zero images containing noninitial entries are rejected as inconsistent
   with the specified initial/valid semantics. Repeated unavailable calls are
   accepted events and consume sequences just like repeated valid samples.
4. UUID validation checks lowercase canonical 8-4-4-4-12 syntax, not a UUID
   generation algorithm/version or authenticity. Device identity and epoch
   generation/persistence are caller responsibilities and remain future work.
5. ASCII controls in metadata are escaped as lowercase u00xx, quotes and
   backslashes escaped, valid UTF-8 preserved. C-string NUL terminates an input;
   embedded NUL cannot be represented through this registry API.

## Buffer and memory limits

| Object / wire unit | Bound |
| --- | --- |
| Channels | 1..16, registration order |
| Numeric wire record | Exactly 20 bytes |
| Image wire header | Exactly 16 bytes |
| Complete image | 16 + 20N, at most 336 bytes |
| Manifest wire JSON | At most 8192 UTF-8 bytes |
| Owned manifest character storage | 8193 bytes, including local NUL |
| Page wire header / payload | 16 / up to 496 bytes |
| Complete page / page count | At most 512 bytes / 17 pages |
| Valid age / unavailable age | Saturates fffffffe / sentinel ffffffff |

All production v2 core operations use fixed storage with no dynamic allocation.
SensorState owns the existing 16-record registry plus 16 sequence/timestamp
entries. C++ Record/Image objects have normal compiler padding and can exceed
wire sizes; size the byte output independently. Metadata build temporarily
uses a registry for project-name UTF-8 validation, and codec decode/capture
uses a bounded local Image; budget task stack accordingly. No runtime heap,
stack or hardware measurements are claimed.

## Automated validation

From firmware root:

```text
cmake -S test/sensor_registry -B .pio/registry-tests
cmake --build .pio/registry-tests --config Release
ctest --test-dir .pio/registry-tests -C Release --output-on-failure
```

Result: 40/40 native CTest cases passed (all 34 existing plus six v2 suites).
New coverage includes exact records/doubles, malformed lengths/tags/statuses/
reserved bytes, nonfinite wire patterns, output preservation, per-sensor state,
wrap/saturation/exhaustion/recovery, detached capture, all image sizes, strict
JSON golden comparison, exact 495/496/497 page boundaries, UTF-8 splitting,
8192-byte success and 8193-byte rejection, and oversized escaped metadata.

V2Codec.cpp, V2Metadata.cpp and V2SensorState.cpp separately cross-compiled with
Xtensa GCC 8.4.0, C++11, -Wall -Wextra -Werror -fno-exceptions -fno-rtti.
PlatformIO esp32dev firmware build also passed with the existing v1 sketch.
Build report: 36,652 bytes static RAM, 643,393 bytes flash, matching the preceding
v1 build. V2 sources are compiled into the library archive but remain unused by
the firmware; this is not a v2-enabled device.

## Remaining work

NVS identity and epoch provisioning; explicit future v2 API selection; transport
scheduler and connection admission; BLE command/mailbox handling; pinned
long-read generation management; metadata transfer; CCCD/setup synchronization;
Android codecs/session integration and layout identity; failure/reconnect tests
on real ESP32/Android. None is implemented here. No BLE or hardware verification
was performed in this sprint.
