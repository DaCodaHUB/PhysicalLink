# Android v2 Live Diagnostic — Sprint 5C.3B

Branch: `feature/ble-protocol-v2`. No firmware, protocol, dashboard, persistence or .gitignore changes.

## Behavior

V2 now requires Metadata READ, Control WRITE with response, and State/Live READ + NOTIFY with a CCCD. Old experimental metadata-only firmware is rejected with an actionable compatibility error; there is no downgrade to v1.

After validated metadata, the existing single-flight GATT queue enables local notifications and writes the State/Live CCCD. Only a successful descriptor callback queues initial capture `02 01 00`. Control ACK queues a State/Live read. An old, fully valid image with the previous request ID causes a read retry after 100 ms, never a repeated command. The command-to-match deadline is five seconds, including write time and reads. Failed/timed-out operations terminate the session rather than retry ambiguous Android callbacks. The overall setup bound remains 120 seconds, with separate 30-second connection/discovery bounds.

Image decoding validates exact 16 + 20N length (up to 336 bytes), tag, manifest epoch/count, reserved bytes, request ID and ordered channels. Android's GATT read callback supplies the complete long-read value; partial values are rejected. Records must be exactly 20 bytes with a valid channel/status/reserved, unsigned sequence/age and finite binary64 value. Available sequence zero and the unavailable age sentinel are rejected. Unavailable requires the sentinel age and all-zero value bits, and is represented by a null value rather than numeric zero.

Manifest registration order maps channels to sensor IDs. Notifications with duplicate/older sequences are ignored. While capturing, only the latest notification per channel is buffered. A complete validated matching image is merged with newer buffered and previously accepted readings before publication. No partial initial state is displayed. Each connection has a new synchronizer and request counter.

After initial synchronization, detected sequence gaps trigger a coalesced capture no sooner than five seconds after the previous capture start. A capture every 30 seconds recovers silent missing notifications even without a detectable gap. Only one command is outstanding; recovery does not retry failed sessions automatically. Request IDs increase up to 65535, then require manual reconnect. Disconnect/cleanup cancels recovery and invalidates delayed reads using GATT and session identity.

The experimental details screen displays metadata, synchronization status, sensor name/ID/unit, value, availability, sequence and acquisition age. Age advances every second from the firmware age plus elapsed monotonic receipt time. Valid readings become Stale at 30 seconds; unavailable values remain Unavailable with unknown acquisition age. Fatal setup/live/sync errors clear readings and return to the existing device error panel with Retry connection. V1 retains its original parser, subscriptions, history and dashboard navigation. V2 never publishes dashboard capabilities or v1 sensor values.

## Changed files

Relative to `app/src/main/java/com/dacodahub/physicallink/`:

- `data/ble/BleConnectionManager.kt`: State validation, CCCD/capture sequencing, bounded reads, live callbacks, recovery and cleanup.
- `data/ble/V2Protocol.kt`: State/Live UUID.
- `data/ble/V2LiveCodec.kt`: strict record/image decoding and bounded session synchronization.
- `domain/model/V2LiveState.kt`: diagnostic state, readings and acquisition-age presentation.
- `domain/repository/DeviceRepository.kt`, `data/repository/BleDeviceRepository.kt`, `feature/devices/DevicesViewModel.kt`: expose diagnostic state separately from v1/dashboard values.
- `feature/devices/DevicesScreen.kt`, `feature/devices/V2MetadataScreen.kt`: show initial sync and real diagnostic readings.

Tests under `app/src/test/java/com/dacodahub/physicallink/data/ble/`: `V2LiveCodecTest.kt` (new), `BleConnectionManagerTest.kt` (extended). This report: `app/BLE_V2_LIVE_DIAGNOSTIC.md`.

## Validation

` .\gradlew.bat test lint build `: 194 unit tests passed, zero failures/errors/skips. Lint: zero errors, 13 dependency/version warnings. `git diff --check` passed.

Tests read the existing firmware numeric-v2 golden fixture directly: valid/unavailable/negative-zero records, invalid record cases, initial image, 1- and 16-sensor images. Coverage includes exact/long/truncated lengths, invalid headers/channel order/epoch/request IDs, delayed capture, CCCD-before-command ordering, latest-notification buffering, duplicate/older sequence rejection, unavailable/recovery transitions, gaps, timeout, silent-loss periodic recovery, disconnect/reset/reconnect and old callbacks after replacement. Existing v1 regressions remain in the full suite.

## Known limitations

- Diagnostic only: no v2 widgets, charts, persisted readings or history.
- No hardware verification was performed. JVM tests validate complete callback payloads, not actual ATT Read Blob transactions or ESP32 poll timing.
- Live records carry no epoch; their domain is bound to the active validated GATT session. Only images explicitly confirm epoch.
- Age starts from firmware serialization plus time since Android callback receipt. It does not compensate BLE transmission latency or synchronize clocks. Stale threshold is a diagnostic policy, not a protocol guarantee or configurable sensor sampling deadline.
- Sequence gaps indicate missing/coalesced updates, not a recoverable historical stream. Capture recovers current cached state only.
- Recovery continues for an active session even when the diagnostic screen is not visible. It stops on disconnect/error; there is no automatic reconnect.

## Physical-device verification

1. Install `app/build/outputs/apk/debug/app-debug.apk` on a BLE-capable Android device. Enable Bluetooth and grant Nearby devices (Location on older Android). Power an ESP32 running the existing v2 State/Live experimental firmware.
2. Scan/connect. Verify advertised v2 identification and complete metadata; observe initial synchronization then actual registered sensor values with exact units/IDs. Verify v2 never opens the dashboard.
3. Use an inspector/sniffer or test peripheral to confirm CCCD notification enable completes before Control Write Request `02 01 00`, then State/Live reads. Delay firmware poll under five seconds and verify only reads repeat at least 100 ms apart; delay beyond five seconds and verify a visible failure and manual retry.
4. Test a 16-sensor board/image (336 bytes), ideally MTU 23: confirm all ordered sensors appear, with no truncated image acceptance. Check firmware pins the full readable image across Read Blob requests.
5. Change physical sensor inputs; verify finite real values, sequences and acquisition ages. Mark a sensor unavailable through the existing firmware API: the app must show Unavailable rather than zero. Restore acquisition and verify the next higher sequence recovers to Valid. Stop acquisitions for 30 seconds and confirm Stale even with no new notifications.
6. Suppress notifications with a test peripheral: verify periodic `02 02 00` capture around 30 seconds recovers cached current state. Deliver a sequence jump: confirm capture is coalesced and occurs no sooner than five seconds after the previous capture start. Flood sequence gaps while capturing; confirm no overlapping commands.
7. Inject malformed/short/long records, NaN/Infinity, wrong channel/order, wrong image epoch/count/request and nonzero unavailable payload through a test peripheral. Confirm visible failure, cleared diagnostic values and Retry connection. Duplicate/older valid sequences must be ignored without refreshing acquisition age.
8. Disconnect/power off during CCCD setup, command write, capture read and steady live updates. Verify values are cleared, retry restarts at request 1, and late callbacks do not change a replacement session. Disable Bluetooth or revoke permission and verify cleanup.
9. Connect an unchanged v1 board: verify normal dashboard navigation, initial values, notifications, history and disconnect. Switch v1 ? v2 ? v1 and verify no value leakage or v2 dashboard widgets.
