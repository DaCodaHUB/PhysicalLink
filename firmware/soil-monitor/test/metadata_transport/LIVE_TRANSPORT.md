# Experimental ESP32 v2 state/live transport — Sprint 5C.2C

Implemented on feature/ble-protocol-v2, using the existing numeric codecs and
SensorState cache. Native tests and ESP32 builds passed. No upload, BLE packet
capture, or hardware verification was performed in this sprint. Earlier hardware
success for metadata retrieval was reported by the user, not independently tested.
The older README.md records the metadata-only milestone.

## GATT and advertising

All UUIDs below have suffix `-7c42-4f9d-9d25-8a639eab5120`:

| Role | UUID prefix | Properties |
| --- | --- | --- |
| Service | b89e2000 | Primary |
| Metadata | b89e2001 | READ, up to 512 bytes |
| Control | b89e2002 | WRITE with response; exactly 2-byte page or 3-byte capture command |
| State/Live | b89e2003 | READ, NOTIFY, CCCD (2902); state up to 336 bytes |

NimBLE's raw GATT registration adds the CCCD for the NOTIFY flag; its assigned
State/Live value handle is used for both subscription routing and custom sends.
The control and metadata UUIDs/properties/page wire format are unchanged.
Standard GAP/GATT services are retained. Legacy service 1234 is not started by
this experiment. PhysicalLink V2 remains the GAP name and complete scan-response
name. Advertising is still flags + complete service UUID (21/31 bytes); scan
response is the 15-byte name + AD header (17/31 bytes). Discovery uses service
UUID, never a required name match.

## State, command and notification behavior

The experimental facade owns its cache; the maker's input registry is copied at
begin and is never mutated by this transport. Use update(id, double),
unavailable(id), and poll() from one owning application task, not ISRs. The
backend samples millis inside the session lock for updates, captures and live
encoding. Begin initializes a fresh all-unavailable cache. Updates while offline
are retained, including their original acquisition time and per-channel sequence.
Unknown IDs do not alter it. NaN/Inf marks a known channel unavailable and returns
InvalidValue. Valid samples recover on the next update. Sequence exhaustion or
ambiguous time stops live transmission and faults the connected session; restart
with end()/begin() and a fresh epoch. Observe the millis clock at least once in
every interval shorter than 2^31 ms, as required by the existing cache.

The control callback validates and copies one bounded command. Page selection
remains `01 <pageIndex:u8>`. Capture is `02 <requestId:u16-le>`, first ID 1, then
strictly increasing through 65535; gaps are allowed, reuse/decrease/zero is not.
Capture requires the admitted peer's State/Live notification subscription at
acceptance and application. One mailbox and one header-observation gate cover
both command types. Invalid commands or overlapping commands latch a fault;
poll schedules disconnection on the host task. The backend also returns an ATT
error. No capture is performed in a BLE access callback.

Write response means receipt. Read the target until its requested page/ID is
observed; allow at least 100 ms between old-header retries, with a 5-second
deadline. Never resend the command or send a second command before completing
the matching read. Reading the other characteristic cannot acknowledge a command.

Capture in poll copies every channel at one cache instant and encodes the
existing 16-byte header plus 20-byte records (36..336 bytes). Metadata and state
each have independent double buffers and pins. Offset zero pins that
characteristic's published generation; subsequent Read Blob offsets return the
full pinned buffer for NimBLE's post-callback slicing. A capture/page command
publishes the other generation, preserving an in-progress read. Unsupported
overlap faults rather than overwriting a pinned slot. Before the first capture
on every connection, state READ is the initial all-unavailable request-ID-zero
image, even when the maker cache already has live data.

Live records use Codec::encodeRecord, exactly 20 bytes regardless of MTU. Every
packet contains a channel, availability, acquisition sequence, acquisition age
and finite binary64 value; unavailable has age ffffffff and all-zero value bits.
No raw C++ structs are sent. ble_gatts_notify_custom receives an explicit mbuf,
so it never replaces or reads the captured-state buffer.

One host-task event is coalesced from poll calls. It attempts at most one packet
per event/poll scheduling opportunity, with at least 50 ms between attempts:
at most 20 attempts per second, below the protocol's four-per-poll ceiling.
There is no catch-up credit after slow polling and no per-sample FIFO. Dirty
channels are found round-robin by comparing cache sequence against the last
successfully queued sequence. Higher-rate acquisitions coalesce to the newest
sample. Failed mbuf allocation or notify calls consume the same budget and leave
the channel dirty; later retries use its newest value. On subscribe, current
entries (including unavailable) become eligible once, then only changed
sequences are sent. No sends occur before subscription or after unsubscribe.
Successfully queued means NimBLE accepted the packet, not delivery acknowledgment.

Host events validate the current connection/subscription and serialize/send on
the same task as GAP callbacks; a queued event cannot send a saved old packet
to a recycled handle. Cache copies use a bounded lock; mbuf allocation, send and
termination occur outside it. Disconnect discards pending commands, subscriptions,
IDs, page selection, pins and send acknowledgments, preserving maker cache/ages.
Reconnection starts unsubscribed and requires a new capture ID 1. The rate
limiter does not reset on reconnect. End disables work, removes the event, stops
the host, then frees callbacks and GATT definitions. Failed stop retains storage
and ownership for an end retry; subsequent begin resets cache and protocol state.

## Synthetic example

Only examples/v2_metadata/main.cpp changes. It registers 16 numeric temperature
channels, zone.0.temperature through zone.15.temperature, in C. Synthetic values
are `20 + 0.3*zone + 2*sin(millis/12500 + zone)`, sampled at 1 Hz/channel.
Zone 15 is unavailable during uptime seconds 20..29 of each minute and then
recovers. There is no BME280, ADC or physical sensor dependency. Sixteen sensors
exercise multi-page metadata and the maximum-size state image.

Serial commands at 115200 baud: `f` selects 100 Hz/channel acquisition stress,
`n` restores 1 Hz, `p` pauses poll while acquisition continues, `r` resumes poll,
`e` ends the transport, and `b` begins it again when stopped. An already queued
host event can finish after p. Each begin generates a fresh random nonzero
epoch. Identity is still a fixed maker-provisioned test UUID: provision a unique
ID on every board. NVS persistence remains outside this experimental sprint.

## Automated validation and memory

From firmware/soil-monitor (use the installed pio executable if not on PATH):

```text
cmake -S test/sensor_registry -B .pio/registry-tests
cmake --build .pio/registry-tests --config Release
ctest --test-dir .pio/registry-tests -C Release --output-on-failure
pio run -c platformio-v2-metadata.ini -e v2-metadata-experimental
pio run -e esp32dev
```

59/59 native CTest cases passed, including existing v1 and v2 golden fixture
oracles. Eight new session suites cover 1..16 sensors, initial unavailable,
consistent capture/ages, IDs through 65535, invalid commands, subscription loss,
wrong peers, independent command completion, all state blob offsets, coalescing,
NaN/recovery, failed-send acknowledgment, 50-ms rate bounds, millis wrap, slow
poll, disconnect/reconnect and restart. Two additional NimBLE binding suites
exercise the real driver through the test port: custom 20-byte payloads,
subscription routing, pinned READ preservation, failed allocation/send, queued
events across disconnect, end/begin and rate limits. Existing startup failures
and safe-stop retry tests remain. The native port does not emulate the radio,
CCCD attribute implementation, or the full ATT host.

Actual ESP32 cross-compile/link succeeded with Espressif32 7.1.3, Arduino
4.20017.260907+sha.dcc1105b, Xtensa GCC 8.4.0 and NimBLE-Arduino 2.5.1.

| Build | Static RAM | Flash |
| --- | ---: | ---: |
| Default v1 | 36,652 bytes | 643,393 bytes |
| Experimental v2 live | 38,132 bytes | 616,953 bytes |
| Previous named metadata-only experiment | 38,124 bytes | 606,433 bytes |

The experimental image grows by 8 static RAM bytes and 10,520 flash bytes,
including the changed synthetic sketch. v1 sizes are unchanged. Static RAM
does not include heap-owned transport state. A separate Xtensa sizeof probe
measured MetadataSession at 12,856 bytes (previous 9,264; +3,592). Its owning
Impl is 12,864 bytes, and the driver allocation is 272 bytes from target object
code. Cache SensorState is 2,472 bytes, included in the session. The session
includes two 336-byte capture slots plus a 336-byte immutable initial-image
template, two 512-byte metadata slots, manifest, cache and scheduler bookkeeping.
These exclude allocator overhead, stack, NimBLE services/CCCDs, mbufs and radio
queues. Runtime heap/stack use, lock duration and RF throughput are unmeasured.

## Exact manual BLE acceptance steps (not performed)

1. Build and upload only the experimental environment:
   `pio run -c platformio-v2-metadata.ini -e v2-metadata-experimental -t upload`.
   Open Serial at 115200. Leave the existing default configuration untouched.
2. In nRF Connect or another inspector, clear cached services if upgrading this
   device. Scan actively and find advertised service
   b89e2000-7c42-4f9d-9d25-8a639eab5120. The scan-response name is PhysicalLink V2.
   Connect and inspect all three characteristics above and CCCD 2902 under
   State/Live. The current experimental MTU cap is 23; record negotiated MTU.
3. Read Metadata completely. Check header `50 4c 02 00`, page count at byte 4,
   total JSON length at bytes 6..7 and epoch at bytes 8..15 (little endian).
   For each remaining page i, write hex `01 <i>` to Control with response, then
   read Metadata until header byte 3 equals i. Complete that read before the next
   command. Assemble JSON slices and confirm 16 ordered synthetic channels.
4. Before subscribing, read State/Live: exactly 336 bytes, beginning
   `21 10 00 00 <epoch:8 bytes LE> 00 00 00 00`. Every 20-byte record has its
   channel, status 01, sequence 00000000, age ffffffff and eight zero value bytes.
   Wait several seconds: no notifications should arrive, and this readable
   initial image remains unchanged despite acquisition.
5. Enable notifications on State/Live (CCCD hex `01 00`). Observe complete
   20-byte values, never state images. Decode bytes 0=20, 1=channel, 2=status,
   3=00, 4..7=sequence LE, 8..11=age-ms LE, 12..19=binary64 LE. Check channels
   0..15, per-channel increasing sequences and smoothly changing temperatures.
6. Write hex `02 01 00` with response. Read State/Live until request-ID bytes
   2..3 equal `01 00`. Complete the 336-byte read; check epoch, 16 records in
   channel order, and realistic values/ages. Repeat the read while notifications
   continue: its bytes must remain identical. Live sequences may be newer.
7. Write `02 02 00`; read to matching ID 2 before another command. New snapshot
   values/sequences should advance. `02 01 01` requests ID 257 and must work;
   IDs need increase, not be consecutive. Reconnect to reset before further
   tests that use ID 1.
8. Watch channel 15 during uptime seconds 20..29 (Serial uptime or a minute-long
   observation). Its status becomes 01, age ffffffff, value zero, with increasing
   sequence; it then recovers to valid temperature. Other channels continue.
9. Send Serial `f` and log notification timestamps/decoded sequences for at least
   10 seconds. Requested acquisition is now 1600 records/s; successful sends
   remain at most 20/s aggregate, with sequence gaps and fair channel progress.
   Send `n` to restore normal acquisition. No accumulated sample backlog should
   drain. RF arrival timestamps may bunch; use an over-the-air trace/server
   instrumentation for exact send spacing, rather than treating UI timestamps
   as radio timing proof.
10. Send Serial `p`, then a valid next capture ID. Its write ACK can arrive but
    reads must keep returning the previous immutable image. Send `r` within
    five seconds; retry only READ until the requested ID appears. It captures
    the latest cache at poll time, not the time of the write ACK.
11. Disconnect during a capture/read and reconnect. State starts at request 0,
    page starts at 0, subscription is off, and capture numbering starts at 1.
    After subscription/capture, cache sequences and acquisition ages continue
    under the same epoch. Pending old commands must not publish into this session.
12. On separate fresh connections test: capture `02 01 00` before subscribing;
    zero ID `02 00 00`; first ID 2 `02 02 00`; reused/decreasing ID after a matching
    capture read; bad opcode `03 01 00`; short `02 01`; long `02 01 00 00`;
    page index equal to pageCount; redundant page 0; and two commands while Serial
    p has paused poll. Expect ATT error and disconnect after poll runs. Failed
    commands must not replace the prior readable page/image. Resume with r.
13. For deliberate pinning verification, use a client capable of raw ATT requests
    at MTU 23: read State at offset 0 (save the first 22 bytes), issue a valid
    capture, let poll run, then issue Read Blob at offsets 22,44,...,330. Their
    assembled result must be the old image. A new offset-zero read must see the
    new capture. Repeat for metadata and with simultaneous live notifications.
    Ordinary inspector/Android long-read APIs may serialize the whole procedure
    and cannot prove this deliberate interleaving. Native tests cover all offsets;
    real ATT/hardware confirmation remains a release gate.
14. Send Serial e, confirm disconnection/advertising stop, then b. Reconnect and
    confirm a new epoch, initial unavailable state and reset acquisition sequences.
    Repeat and monitor real heap/stack over a long run. Independently upload the
    default esp32dev build and verify PhysicalLink Soil and v1 1234/567x behavior.

## Remaining limitations

Experimental, one client and one ATT bearer (EATT disabled). No Android changes
were made in this sprint; existing Android metadata retrieval does not consume
live records yet. Identity provisioning/NVS, RF congestion measurements, larger
MTUs, long-run heap/stack checks, actual CCCD interoperability, raw ATT pinning,
rapid reconnect races and live sensor delivery still require hardware acceptance.
Notifications are unacknowledged current state, not lossless history. Clients
must use captures for synchronization/recovery; no history/cloud/type extensions
are implemented. A custom caller must supply a fresh epoch on each begin; the
experimental sketch now does so automatically.
