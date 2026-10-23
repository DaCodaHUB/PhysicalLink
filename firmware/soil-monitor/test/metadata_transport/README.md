# Experimental metadata-only BLE transport - Sprint 5C.2B

Status: implemented behind an explicit experimental build; source-reviewed,
native-tested and ESP32-compiled. **No BLE capture, upload, or hardware testing
was performed. Long-read safety is partially verified, not hardware verified.**
The default v1 sketch, PhysicalLink.begin(), UUIDs and platformio.ini are unchanged.
The existing v2 state/live characteristic is NOT registered. No notifications,
state synchronization or Android v2 implementation are included.

## Changed files in this sprint

Paths are relative to firmware/soil-monitor:

- `lib/PhysicalLink/src/V2MetadataSession.{h,cpp}`: owned immutable manifest,
  per-connection page state, single-command mailbox and two pinned generations.
- `lib/PhysicalLink/src/ExperimentalV2Metadata.{h,cpp}`: separate experimental
  lifecycle facade. Validates/builds metadata before starting BLE.
- `lib/PhysicalLink/src/ExperimentalMetadataDriver.h`: private native-test seam.
- `lib/PhysicalLink/src/ExperimentalMetadataNimBLE.cpp`: raw NimBLE GATT access
  registration, real ATT offsets, bounded critical sections, GAP lifecycle and
  host-task deferred disconnects. No dependency source is patched.
- `platformio-v2-metadata.ini`: opt-in configuration, separate src_dir, pinned
  NimBLE-Arduino 2.5.1 / Espressif32 7.1.3, server test MTU cap 23.
  Its build/libdeps directories are isolated under .pio/build-v2-metadata and
  .pio/libdeps-v2-metadata so switching configs preserves default artifacts.
- `examples/v2_metadata/main.cpp`: separate 16-sensor deterministic test sketch.
- `test/metadata_transport/{test_metadata_transport.cpp,test_nimble_binding.cpp,
  NimbleMetadataTestPort.h,verify_pages.py,README.md}`: tests, test-only port and
  these instructions. Reuses Sprint 5C.2A fixtures without modifying them.
- `test/sensor_registry/CMakeLists.txt`: registers nine new native tests.
- `lib/PhysicalLink/README.md`: links the experimental instructions.

No Android, existing v1 implementation, local design documents or .gitignore
were changed. Earlier Sprint 5C.2A files already present in the working tree are
not changes introduced by this sprint.

## Exact service contract

| Role | UUID | Properties |
| --- | --- | --- |
| Service | b89e2000-7c42-4f9d-9d25-8a639eab5120 | Primary |
| Metadata | b89e2001-7c42-4f9d-9d25-8a639eab5120 | READ only |
| Control | b89e2002-7c42-4f9d-9d25-8a639eab5120 | WRITE with response only |
| State/live | b89e2003-7c42-4f9d-9d25-8a639eab5120 | Absent |

Metadata is the exact V2Metadata page: 16-byte header + up to 496 payload bytes,
never more than 512 bytes total. Manifest limit remains 8192 bytes (17 pages).
The raw GATT callback enforces returned size through the bounded page codec;
there is no NimBLE characteristic setter that could silently truncate it.
Pages contain JSON slices, not standalone JSON documents. No sensor is omitted;
only the complete validated concatenation is a JSON document.

Control accepts exactly `01 <page:u8>`. No write-without-response property,
notifications, CCCDs, capture opcode or additional protocol commands are exposed.
Page zero is selected for every connection, including reused connection handles.
Only one client is admitted. A second connection is terminated without resetting
the active client's state. Advertising stops on connection and restarts on
accepted-peer disconnect; the primary advertisement contains flags and the full
service UUID. No local-name AD structure is required: discover by service UUID.

The manifest is copied/generated once before advertising and owned by the
session until shutdown. Later changes to the caller's registry cannot change
published metadata. One two-byte page request is copied from the callback's
mbuf to session-owned state. poll applies at most one pending request. It does
not wait for a radio response or sleep. Callback critical sections cover only
bounded state operations/copies (at most a page); mbuf allocation/appending is
outside them. No callback waits for the maker to call poll.

A valid write ACK means queued, not applied. Until poll, reads retain the old
header. After poll, a NEW offset-zero read sees the requested header. Client
must wait for the full matching read before issuing another command. A command
while pending or awaiting a matching offset-zero read is a protocol violation.
Invalid opcode/length/index or selecting the already selected page also faults
the session per the minimal protocol, leaving the selected page unchanged.
The raw binding can return an ATT error immediately; poll schedules the faulted
peer's disconnect on the host event queue. While poll is paused, the disconnect
is deferred too. Wrong-peer operations cannot alter the admitted peer's state.

## Long-read investigation and safety argument

Inspected actual bundled NimBLE-Arduino 2.5.1 files:

1. `src/NimBLEServer.cpp`, handleGattEvent (around lines 723-745): high-level
   readEvent is conditioned on mbuf length. It does not expose the ATT offset
   to user callbacks. It is not a transaction-boundary guarantee.
2. `src/nimble/nimble/host/include/host/ble_gatt.h`, ble_gatt_access_ctxt:
   the raw access context contains an actual uint16 offset.
3. `src/nimble/nimble/host/src/ble_gatts.c`, ble_gatts_chr_val_access
   (around lines 510-529): copies the ATT offset into gatt_ctxt.offset.
4. Same file, ble_gatts_val_access (around lines 445-495): calls the application
   to append the FULL value, then slices it using the requested offset. Therefore
   the application must not pre-slice the value or offsets would be applied twice.
5. `src/nimble/nimble/host/src/ble_att_svr.c`, ble_att_svr_rx_read_blob:
   reads the on-wire offset and forwards it to ble_att_svr_read_handle.
6. `src/NimBLEServer.cpp`, start/resetGATT: resets and replaces service
   definitions. Consequently the experimental backend does not create or start
   a NimBLEServer; it registers raw definitions and uses raw GAP advertising
   on the same NimBLEDevice-initialized stack. This is a lower-level binding
   for the same specified protocol, not a substitute wire transport.

Per connection, an offset-zero read pins the published generation. All later
positive-offset reads return that exact generation, including offset==length
for an empty terminal blob. Offset>length and a positive offset with no initial
pin are rejected. A new offset-zero request starts a new read and repins.
There is no idle-time, MTU-size, callback-count or inferred completion heuristic.

poll writes only to the other page buffer and atomically changes the published
slot. The old slot remains pinned. A second command is blocked until a read
pins the new slot, so the next writer can safely reuse the old slot. Even a
broken caller cannot force overwrite: poll checks that its destination is not
pinned and faults rather than replacing it. Read callbacks copy the entire
pinned page into a 512-byte local buffer under the short lock, then copy it into
an owned mbuf outside the lock. poll cannot change bytes already in the response.
Disconnect discards pins, mailbox and observation state before reuse.

The raw access callback has connection handle + offset, not a bearer ID.
**EATT is explicitly rejected at compile time**. Only the single ATT bearer
profile is supported; multiple clients are rejected. A new offset-zero read
abandons the prior read generation by definition, so overlapping application
reads must not be interleaved by the client. NimBLE version is also checked at
compile time; another version requires re-auditing offset/slicing behavior.

Native tests prove the two-generation algorithm for all offsets 1..512,
including selection while a previous page is being read. Tests also compile
the actual C++ raw binding against a test-only API port to verify that real
callback offsets are passed through, full values are appended, mailbox input
is copied, and cleanup retains ownership on host-stop failure. This port does
not execute NimBLE C or model its radio/task scheduler. Real ESP32 compilation
checks the actual vendor API. Together this establishes an implementable,
source-supported pinning contract for the pinned single-bearer stack; end-to-end
transaction behavior remains a hardware release gate. Status: partially verified.
Do not generalize this result to EATT, other NimBLE versions or other BLE stacks.

## Ownership, lifecycle and failure handling

Use a single maker task for the facade. begin first validates and serializes
metadata, then acquires experimental singleton ownership and initializes the
stack. An already initialized external stack or existing NimBLEServer returns
Busy without deinitializing it. Validation failure never advertises. Raw UUIDs,
service definitions, callback arguments, pending event and page storage all
outlive the host. No callback ownership is transferred to NimBLEServer.

end marks callbacks stopping, stops advertising, removes a queued lifecycle
event, then stops the host with deinit(false) before resource destruction via
deinit(true). Failure returns StopFailed and retains storage/ownership for a
later end retry. Destructor attempts the same cleanup; if host stop fails, it
retains the implementation instead of freeing live callback arguments. Startup
failure invokes this cleanup too. Deferred fault disconnects run on the host
queue, where the handle cannot be recycled between fault check and terminate.
Reconnect clears pending commands; stale handles cannot change current state.

Facade results: Ok, InvalidMetadata, AlreadyRunning, NotRunning, Busy,
OutOfMemory, StartFailed, StopFailed. poll reports StartFailed if an asynchronous
advertising restart failed; the sketch reports it and shuts down rather than
silently retrying forever. Hardware host resets/re-synchronization beyond a
normal disconnect are not claimed as supported; end/begin or board restart is
required after a host failure. Explicit nothrow allocations are checked; deeper
NimBLE/stdlib allocation behavior remains that of the platform.

Owned storage includes the <=8193-byte manifest character array and two
512-byte page arrays, plus flags/lifecycle/backend state. This is roughly 10 KiB
of owned heap storage, not a measured runtime allocation peak. The read callback
uses a 512-byte stack scratch buffer; manifest validation has additional bounded
stack use. No heap/stack high-water measurements were performed.

## Build and manual hardware procedure

From firmware root, build the separate experiment:

```text
pio run -c platformio-v2-metadata.ini -e v2-metadata-experimental
```

When ready to test a connected board, explicitly upload and monitor:

```text
pio run -c platformio-v2-metadata.ini -e v2-metadata-experimental -t upload
pio device monitor -b 115200
```

These upload commands are instructions only; they were NOT executed. Restore
v1 using `pio run -c platformio.ini -e esp32dev -t upload` when finished testing.
Default firmware/build command continues to use platformio.ini and v1 behavior.

The test sketch registers sensor.0 .. sensor.15, names Sensor 0 .. Sensor 15,
unit raw, device Garden, deterministic fixture identity/epoch. Its manifest is
916 UTF-8 bytes: page 0 is 512 bytes (496 payload), page 1 is 436 bytes (420
payload). The deterministic identity and epoch are for golden test matching,
not production provisioning; give each test board its own ID outside this
fixture test. NVS identity and random per-run epoch provisioning are future work.

Using a BLE GATT client with hex reads/writes:

1. Discover by full service UUID, allowing unnamed devices. Inspect properties:
   metadata READ, control WRITE; state/live UUID and CCCD must be absent.
2. Connect, verify negotiated ATT MTU 23. The experimental config sets server
   preferred MTU to 23 via PHYSICAL_LINK_METADATA_TEST_MTU=23, so a client's
   automatic larger request cannot raise the negotiated value. Record the actual
   client-reported MTU; a configured cap is not hardware evidence by itself.
3. Read metadata using the client's complete/long-read operation, not only the
   first ATT response. Save the entire hex value as page0.txt. Expected header:
   `50 4c 02 00 02 00 94 03 e0 1a 87 2d 80 ca 98 b3`.
4. Write `01 01` with response to control. Read metadata until the page index
   byte is 01; allow >=100 ms between stale reads, <=5 seconds total. Save the
   complete value as page1.txt. Expected header is identical except byte 3=01.
   Do not resend a page-select command while waiting. Do not select an already
   selected page; simply read it again.
5. Strip each 16-byte header, concatenate payloads in index order and decode
   UTF-8/JSON only after complete reconstruction. Check exact lengths, totals,
   epoch and all 16 IDs in registration order. Compare bytes against fixture
   wire.page.sixteen.0 and wire.page.sixteen.1 in numeric-v2.json.
6. Send Serial `p` to pause maker poll while keeping BLE callbacks live. Queue
   `01 00`; reads must remain page 1 until Serial `r` resumes poll, after which a
   new read returns page 0. Resume within the five-second protocol client bound
   for a conforming session. This intentionally delayed application is not a
   BLE callback sleep.
7. On separate fresh sessions, write invalid opcode `02 01`, invalid length
   `01`, out-of-range `01 ff`, duplicate-current `01 00`, or queue two page
   requests before reading the matching result. Expect an ATT error and a
   deferred disconnect once poll runs. Page content must never be corrupted.
8. Disconnect while a page selection is pending (pause poll first); reconnect,
   resume and verify page 0. Repeat rapid disconnect/reconnect and verify no
   command carries into the next session, including recycled handles.
9. Send Serial `e` while connected; verify clean disconnect and advertising
   stops. Reset the board to start again. Native tests additionally cover
   repeated end/begin and destructor cleanup; hardware must confirm lifecycle.
10. For a larger-MTU test, change ONLY the experimental config's test MTU flag
    from 23 to 247, rebuild/upload that experiment, and request a larger MTU
    from the client. Record negotiated MTU and repeat page reads/reconstruction
    and reconnect tests. Restore flag 23 afterwards. Wire pages must be identical.

At MTU 23, read response payload is at most 22 bytes, so a 512-byte page needs
24 response chunks. At MTU 247 it needs three. These are protocol arithmetic,
not measured timing. Some mobile BLE clients automatically assemble reads and
cannot expose blob offsets; a displayed first chunk is not a successful test.

For the release-critical interleaving test, use a client/test harness that can
issue raw ATT Read/Read Blob and Write Requests on the SAME unenhanced bearer:
read offset 0 of page 0 (first response), select page 1 and let poll apply it,
then issue Read Blob offsets 22,44,... against metadata. All continuation bytes
must still reconstruct page 0. Only a new offset-zero read may start page 1.
Capture ATT traffic. A normal Android high-level read may serialize/automate
its blobs and prevent this deliberate interleaving; do not claim this test from
ordinary UI reads. No raw-ATT client harness is included in this sprint.

Optional offline verification of saved complete hex pages:

```python
import json, struct
from pathlib import Path
pages = [bytes.fromhex(Path(f"page{i}.txt").read_text()) for i in range(2)]
payloads = []
for index, page in enumerate(pages):
    magic, version, got, count, reserved, total, epoch = struct.unpack("<2sBBBBHQ", page[:16])
    assert (magic, version, got, count, reserved, total, epoch) == (
        b"PL", 2, index, 2, 0, 916, 0xb398ca802d871ae0)
    assert len(page) == 16 + min(496, total - index * 496)
    payloads.append(page[16:])
raw = b"".join(payloads)
assert len(raw) == 916
manifest = json.loads(raw.decode("utf-8"))
assert [s["id"] for s in manifest["sensors"]] == [f"sensor.{i}" for i in range(16)]
```

## Validation and outstanding work

Early init failure is cleaned up through NimBLEDevice's guarded deinit rather
than raw GAP/NPL calls against uninitialized host locks. The vendor init path
can fail after partially initializing controller/HCI state before its initialized
flag is set; complete rollback of such platform-internal failures is not proven
by these native tests and may require board reset. This remains a platform
startup-failure limitation, not permission to reuse live callback storage.

Native: 49/49 CTest cases passed, including all prior registry, internal
serializer, v1 public API, v2 core and golden tests. Nine new cases cover single
and multi-page values, delayed commands, invalid commands, connection isolation,
pending disconnect, every page offset, ownership/teardown, golden pages,
actual-binding callback routing, startup failure injection at eight stages,
and retained storage when stopping fails. No native test emulates RF or executes
the actual NimBLE host task.

Separate Xtensa C++11 compilation of V2MetadataSession.cpp and the experimental
facade passed with warnings as errors, exceptions/RTTI disabled. PlatformIO
compiled the actual raw NimBLE binding and experiment; the default esp32dev
v1 build also passed. Final experimental MTU-23 build and size are recorded in
the appended validation result. Default v1 size remains 36,652 bytes static RAM
and 643,393 bytes flash. No hardware test is claimed.

Outstanding: captured MTU-23/larger-MTU long-read tests and deliberate offset
interleaving; runtime stack/heap/congestion measurements; Android v2 discovery;
production identity/epoch provisioning; host reset recovery. UUIDs remain the
minimal proposal's experimental allocations. Future state/live support needs
its own synchronization/notifications sprint. EATT/multiple clients and other
NimBLE versions remain unsupported. No alternate transport was introduced.

Final validation result: MTU-23 experiment built successfully with actual
NimBLE-Arduino 2.5.1; 38,124 bytes static RAM, 606,317 bytes flash. Default v1
build passed separately (36,652 RAM / 643,393 flash). All 49 native tests passed
after the final startup cleanup change. Experimental facade/session also passed
standalone Xtensa C++11 object compilation with warnings treated as errors.
The NimBLE binding itself was cross-compiled/linked by PlatformIO against its
real headers, in addition to the native test-port checks. No uploads were run.

Both artifacts are retained after validation: default at
.pio/build/esp32dev/firmware.elf and experiment at
.pio/build-v2-metadata/v2-metadata-experimental/firmware.elf. Symbol inspection
of the default ELF found no ExperimentalMetadata, MetadataSession or
NimbleMetadataDriver symbols. No default v1 source/configuration changes were
found relative to HEAD.
