# PhysicalLink BLE transport developer guide

Maker applications should use `PhysicalLink.h`. The existing production backend
is `BleTransport` → `MetadataDriver` / `NimbleBleDriver` → `MetadataSession`,
using `ProtocolCodec`, `SensorState`, and `Metadata` under `physical_link::protocol`.
Renaming/promoting these components did not change wire version **2**.

## Frozen GATT contract

All UUIDs have suffix `-7c42-4f9d-9d25-8a639eab5120`:

| Role | Prefix | Properties |
| --- | --- | --- |
| Service | b89e2000 | Primary |
| Metadata | b89e2001 | READ, max 512-byte page |
| Control | b89e2002 | WRITE with response |
| State/Live | b89e2003 | READ + NOTIFY, CCCD 2902 |

GAP/scan-response name remains `PhysicalLink V2`; discovery uses the service UUID.
The validated default MTU remains 23. One peer/ATT bearer is supported (EATT off).
NimBLE is pinned at 2.5.1 because raw ATT offset/pinning behavior was audited there.

## Commands, pinning, and live delivery

Metadata selection is `01 <page:u8>`. State capture is
`02 <requestId:u16-le>` after subscription. First ID is 1, later IDs strictly
increase through 65535 (gaps allowed); reconnect resets IDs and subscription.
WRITE acknowledgment means receipt, not completion. Poll until the READ header
matches; wait at least 100 ms between old-header reads, with a five-second
deadline. Do not repeat a command or overlap another before completing its read.
Callbacks only validate/copy a bounded command; maker poll applies it. Invalid
commands/overlap fault the connection and schedule host-task disconnection.

Metadata pages and state images have independent double buffers and pins.
Offset-zero READ pins a generation; Read Blob returns that immutable generation
through all offsets even if the other generation is published. The raw callback
provides the full attribute for NimBLE's subsequent offset slicing. Before the
first capture on every connection, state is request-0/all-unavailable even if
acquisition already populated the cache.

State images contain a 16-byte header plus 20-byte ordered records (1–16
sensors, max 336 bytes). Notifications are always one 20-byte record: type 0x20,
channel, availability status, reserved zero byte, sequence u32 LE, acquisition
age-ms u32 LE, binary64 LE. Unavailable status is 0x01 with age ffffffff and zero
value bits. State type is 0x21 and its header includes request ID and epoch.
Protocol fixtures remain the byte-level authority.

One coalesced host event attempts at most one notification every 50 ms: max
20 attempts/s aggregate with no catch-up credit/FIFO. A round-robin dirty-channel
scheduler compares cache vs successfully queued sequence. Failed allocation/send
consumes budget and leaves newest values eligible. No sends before subscription
or after unsubscribe. Cache/age and limiter survive reconnect; commands, pins,
subscriptions and capture IDs do not. Host events validate the current peer,
preventing saved packets going to recycled handles. Encoding/cache use a bounded
lock; allocation, send and disconnect run outside it on the host task.

End disables work, removes the event, stops the host, then frees callbacks/GATT
resources. Stop failure retains storage/ownership for retry. Successful begin
resets cache/sequences and starts a fresh epoch. Update rejects NaN/Inf by marking
the known channel unavailable. Clock ambiguity/sequence exhaustion faults the
session; restart with end/begin and fresh acquisitions. All maker calls use one
owner task. No radio acknowledgment or lossless historical delivery is promised.

## Stress example and native tests

`pio run -e stress-test` builds sixteen channels `zone.0.temperature` through
`zone.15.temperature`. Values/cadence remain the original formula at 1 Hz;
zone 15 is unavailable at uptime seconds 20..29 of each minute. Serial at 115200:
`f` = 100 Hz acquisition stress, `n` = 1 Hz, `p/r` = pause/resume poll,
`e/b` = end/begin. The original provisioned test UUID is retained; use one such
test board at a time or provision distinct identities. This advanced stress
harness accesses the internal facade to preserve its test identity; beginner
and real-sensor examples use only `PhysicalLink.h`.

The native suite in `test/sensor_registry/CMakeLists.txt` retains registry,
codec/fixture, metadata paging, transport lifecycle, real NimBLE binding via a
test port, and state/live scheduler tests. Public API tests exercise arbitrary
registration and errors, copied identity, unavailable/recovery, restart reset,
startup cleanup, owner exclusion and failed-stop retry. These do not emulate RF.

## Hardware acceptance (not performed during consolidation)

1. Discover by service UUID and inspect all characteristics/CCCD. Read each
   metadata page completely, selecting subsequent pages with `01 <i>`; match
   page headers before issuing another command. Assemble/validate JSON, epoch,
   stable UUID, IDs, units, and ordering.
2. Before subscription read state: request 0, all unavailable; no notifications.
   Subscribe to State/Live. Check 20-byte numeric/unavailable records and ages.
3. Write `02 01 00`, read until request ID 1, finish the complete state image
   (96 bytes real sensors, 336 stress). Repeat READ during live updates; the
   captured image stays immutable. Issue `02 02 00` and check newer acquisitions.
4. Verify metadata and state long reads at MTU23 and deliberate raw ATT offset
   interleaving (old pinned generation survives publication). Inspector APIs
   that serialize whole long reads cannot prove this interleaving.
5. Exercise stress f/n, pause/resume poll, unavailable/recovery, and reconnect.
   Check sequence gaps coalesce and no backlog drains. UI arrival times cannot
   prove host send spacing; measure with hardware instrumentation.
6. Use fresh connections for malformed/overlapping commands, capture before
   subscribe, zero/reused/decreasing IDs, invalid pages. Expect ATT error and
   disconnect after poll; previous pinned buffers must not be overwritten.
7. Exercise end/begin and long runs; monitor heap/stack, recovery, subscription,
   and packet captures. Verify Android charts, freshness, reconnection, all four
   widget types, and saved layouts after ESP32/app restarts.

The user reported hardware success before this consolidation. No new upload,
radio trace, or physical hardware acceptance was performed here.
