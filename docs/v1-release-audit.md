# PhysicalLink v1 release audit and hardening

Audit date: 2026-10-20. Scope: existing BLE session, capability discovery, scanner, dashboard editing/persistence, UI feedback, and lifecycle. Existing uncommitted Sprint 1/2A/2B work was preserved. No firmware, GATT UUID/protocol changes, automatic reconnect loop, connection service, or architecture replacement was introduced.

## Audit evidence before changes

The implementation was read before production changes. The audit traced connection creation, every endSession call, explicit two-phase disconnect, operation queue identity/timeout guards, callback identity checks, parser publication order, scanner registration and callbacks, DataStore command ownership, and activity/navigation lifetime.

Twelve new Robolectric/Mockito GATT regression tests were then run against the original BLE implementation. All twelve failed: missing close calls on failure/timeouts, retained sensor readings on loss, pending-client replacement, and unsafe post-close diagnostic calls were observed in simulated Android execution. These are mocked-platform reproductions, not evidence of radio behavior or physical Android testing. Further fixes below have focused regression coverage or explicit code-path evidence noted separately.

## Confirmed defects fixed

| Defect and original failure path | Correction | Regression evidence |
| --- | --- | --- |
| endSession disconnected an active client but never closed it on the successful disconnect call. Connection failure, connection timeout, discovery rejection/failure/timeout, read/descriptor failure, and operation timeout all reached this leak. | Invalidate GATT identity and queue first; disconnect best effort and close in finally. Close exceptions are contained and logged. | BleConnectionManagerTest verifies close on connection/discovery/operation failures and timeouts, rejected discovery, read/descriptor failure, and permission exceptions. |
| connect could allocate while disconnectingGatt still owned a previous client. Later disconnects could overwrite the one retirement slot and timeout reference. Repeated connect taps also allocated unnecessary replacement clients. | Finalize an outstanding retirement before replacement, close the previous active client before allocation, ignore duplicate requests for the current active address, and leave repeated disconnect taps idempotent. | Replacement, duplicate-connect, repeated-disconnect, late-callback tests. |
| finishDisconnect accessed oldGatt.device, adapter, and connected devices after closing, outside exception handling; these unused diagnostics could throw on permission revocation or unavailable Bluetooth. | Remove the diagnostic Bluetooth calls; one guarded close path. | Baseline tests threw here; explicit disconnect, timeout fallback, and permission-revocation tests now pass. |
| Both teardown paths cleared metadata but retained sensorValues. Reconnecting a different device with the same capability IDs could show old values. The repository also assigned a new device ID before main-looper session invalidation. | Clear readings, value map, and UUID lookup on teardown. Publish the session address from the manager only after invalidating old metadata and values, retaining that address after disconnection for layout identity. | Loss clears values; synchronous device-ID observation sees no previous metadata/values; stale notification tests. |
| UUID conversion and GATT-target validation happened after capability publication. Duplicate IDs/UUIDs queued ambiguous repeated requests. A sensor pointing to the capabilities characteristic could recursively enqueue metadata reads. | Validate schema/string fields, nonblank IDs/types, unique IDs and canonical UUIDs, and reserved metadata UUID before publication. Validate all advertised characteristics/descriptors before exposing metadata. | Parser malformed/duplicate/reserved/UUID tests; missing service/characteristic/descriptor and invalid-metadata manager tests. |
| Every sensor was read and subscribed regardless of characteristic properties; valid read-only or indication-only sensors failed during setup. | Build the existing serialized queue from READ/NOTIFY/INDICATE properties, using the correct indication CCCD value. Sensors with neither property fail with an actionable error. | Read-only, notification-only, and indication-only tests. Existing firmware READ + NOTIFY behavior remains supported. |
| Scanner acquisition was outside its permission exception handler, null/disabled scanners failed silently, onScanFailed was only logged, and the same callback was reused without session identity. Late callbacks could alter a newer scan. | Main-looper scanner ownership; a fresh callback per registration; identity checks before result/error handling; protected acquisition and result access; observable ScanState with retry instructions; invalidate before stopping. | BleScannerTest covers acquisition and result permission exceptions, unavailable/disabled adapter, asynchronous failure and retry, late results/failures, and stop exceptions. |
| Low-latency scans were unbounded and not stopped when the app backgrounded. A disabled adapter or revoked permission could leave a stale active UI after idle sensor setup. | Bound scans to 15 seconds, stop on device-screen/activity stop, refresh permissions/adapter on foreground entry and protected adapter-state broadcasts. No reconnect is triggered. Disable scanning during Connecting/Discovering/Connected. | Scan timeout, scan permission/adapter refresh, GATT foreground refresh, and repository adapter-broadcast/no-reconnect tests. Activity lifecycle wiring is code-reviewed and still requires device validation. |
| Large decimal exponents could make setScale/toPlainString throw or attempt enormous allocation on the UI thread. | Bound decimal precision and scale before expansion; preserve extreme/opaque readings as text. | SensorFormattingTest covers both exponent extremes and unknown types. |
| Scan permission denial had no feedback; empty permission results were accepted by all(). Connecting/discovery had no cancel control on the dashboard. Status text contained broken ellipsis encoding; edit controls could overflow and duplicate-widget actions lacked context. | Check the complete required permission set, provide permission/settings and scan-state feedback, add Cancel connection, announce status/errors politely, wrap edit controls, and label widget actions with name/index. | UI branches and lifecycle hooks code-reviewed, compilation/lint validated. Physical TalkBack, font-scale, and touch checks remain manual. |

Unknown nonblank sensor types are intentionally opaque text in VALUE cards, preserving forward-compatible display. GAUGE/CHART retain the existing unsupported-renderer fallback. Empty capability lists remain valid. Malformed/ambiguous discovery fails the session with a firmware/reconnect message rather than persisting partial metadata or silently discarding saved widgets.

## Reviewed behavior preserved

- Explicit disconnect still detaches the active session immediately, requests disconnect, and waits for its DISCONNECTED callback or five-second fallback before close. A new user connection first finalizes that retiring client; permission failure forces immediate best-effort cleanup.
- GattOperationQueue remains unchanged. Clearing cancels all pending work; timeout aborts the session rather than retrying an ambiguous request. The manager cancels handler timeouts and clears characteristic/descriptor identity on teardown. Late callbacks are guarded by GATT identity plus active operation/target identity.
- Exactly one client is owned as active or retiring. Platform close/disconnect are best effort if Android throws after permissions or adapter loss. No claim is made about vendor radio teardown latency.
- Dashboard persistence logic remains unchanged. DataStore is application-scoped, queued updates capture their device key and transform latest saved state, and reconciliation does not recreate a layout after reset. UUIDs and capability IDs retain existing meanings. Unexpected disconnect clears transient metadata/readings only; persisted widget IDs, sources, order, sizes, visibility, and extra instances survive. New tests disconnect, clear the ViewModel, reconnect, and verify exact configuration restoration.
- No live readings are written to DataStore. Existing rapid-edit, delayed-load, device-switch, blocked-save, navigation, reset, corruption, and capability-change tests remain in the suite.
- An existing connection can continue while backgrounded if Android keeps the process alive. There is no foreground service or background-execution guarantee. Process recreation creates a disconnected repository; navigation returns to Devices and the user reconnects. Layout storage is restored after that reconnect. No automatic reconnection or saved live session is introduced.

## Potential risks not reproduced

- Real OEM behavior during simultaneous permission revocation, radio shutdown, disconnect callbacks, and close failure. Cleanup attempts are contained, but the OS may deny the platform calls; simulated tests cannot prove client unregistration on the radio.
- Android 37's BluetoothGattConnectionSettings/executor path is compiled; the simulated BLE tests use API 34. Pre-33 deprecated value callbacks are preserved but also need device validation. Background timer throttling can extend operation/fallback deadlines.
- Intermittent peripheral availability, Android status 133 behavior, radio interference, long capability reads/MTU behavior, and device-specific notification timing cannot be established with mocked GATT clients.
- Android process death can lose edits that were queued but not yet committed. DataStore atomic commits and saved-file reopen are tested; force-stop/power loss on Android is not. The temporary BLE-address identity will not match if a peripheral randomizes its address.
- Saved layouts use Android backup defaults. Backup/restore, low-storage IO behavior, and OEM permission settings remain device checks. Existing future-schema recovery discards unsupported records, as documented in dashboard-persistence.md.

## Manual hardware tests required

No physical-device or emulator verification was performed during this audit. Before publishing, execute and record results for:

1. Current ESP32 firmware: connect, load every sensor, verify fresh notifications, edit/reorder/resize/hide/duplicate/restore/reset, navigate away/back, disconnect, restart the app, and reconnect. Verify missing/returning/new capabilities preserve layout with a test peripheral.
2. Repeat explicit disconnect, double taps, reconnect to same/another address, and connection replacement during the five-second retirement window. Inspect Android Bluetooth/HCI diagnostics for closed clients; ensure at most one app session remains registered.
3. Power off or move the peripheral out of range during connection, discovery, characteristic read, CCCD write, and idle notifications. Trigger absent service/characteristic/CCCD and malformed/duplicate metadata using a test peripheral. Verify cleanup, actionable errors, no unexpected automatic reconnect, and intact layouts.
4. Disable Bluetooth during scan and every connection phase; re-enable and reconnect manually. Deny/revoke Nearby devices permissions, return from settings, and verify scan/connection recovery. On Android 11 and earlier, also test denied Location permission and disabled system Location services. Test scan registration failure/throttling with competing scanner apps.
5. Background/foreground, screen off, rotate, and let Android kill/recreate the process while connected and while editing. Verify scans stop, layouts restore after explicit reconnect, and pending/committed edits behave as documented. Do not expect continuous background BLE without a foreground service.
6. TalkBack/Switch Access Move Up/Down, hide/restore, width and reset controls; large font/display scales, narrow and landscape layouts, dark mode, drag feedback, and permission/error announcements. Existing drag targets remain limited to visible items without edge auto-scroll.
7. Install a signed release build on representative Android 24/30, 31+, 33+, and 37 devices. Validate both callback API families and API-37 connection setup. Current build outputs include an unsigned release APK; signing/distribution configuration and Play submission are outside this audit.

## Deferred improvements

- A foreground connected-device service, explicit background-session UX, or bounded opt-in reconnect policy if product requirements later demand background continuity. No unlimited reconnect loop should be added.
- Replace the current exact PhysicalLink Soil advertising-name filter with an explicit supported-device/service identification policy when additional hardware products are supported. This audit preserves discovery behavior for the current firmware.
- Full localization/string-resource migration, Compose screenshot/accessibility automation, and comprehensive Android instrumentation coverage. Robolectric/Mockito are test-only dependencies; no production dependencies were added.
- Debounced save-status/flush UX for shutdown-sensitive edits, stable protocol-level device identities, explicit future schema migrations, and drag auto-scroll. These are existing product limitations, not reproduced regressions requiring an architectural rewrite.

## Validation

Final validation passed: `.\gradlew.bat test lint assembleDebug assembleRelease` (BUILD SUCCESSFUL). All 76 tests passed with zero failures: the original 33 plus 43 new regressions. Debug and unsigned release APKs were produced under app/build/outputs/apk. `git diff --check` passed.

Lint completed with zero errors and 28 warnings. Reviewed warnings are dependency/toolchain update notices, unused template resources, version-catalog/style suggestions, a redundant manifest label, and the intentional API-31 permission flag on a min-24 app. No warning was suppressed to obtain this result. Dependency refresh and template/style cleanup are deferred rather than bundled into BLE stabilization.

The initial Sprint 2B suite had 33 tests; the audit adds targeted BLE, parser, formatting, scanner, repository, and unexpected-disconnection regressions. Tests include real temporary DataStore files via the portable Okio fixture already introduced in Sprint 2B and simulated Android API 34 BLE via Robolectric/Mockito. Neither verifies physical BLE or an installed release.

References used during the audit: [Android BluetoothGatt API](https://developer.android.com/reference/android/bluetooth/BluetoothGatt) for disconnect/close responsibilities and permission requirements; [Robolectric setup](https://robolectric.org/getting-started/) for Android-simulated local tests.

## Changed-file scope

- BLE: BleConnectionManager.kt, BleScanner.kt, DeviceCapabilitiesParser.kt, new CapabilityUuid.kt.
- Shared repository/session UI: BleDeviceRepository.kt, DeviceRepository.kt, new ScanState.kt, DevicesViewModel.kt, DevicesScreen.kt, DeviceControls.kt, MainActivity.kt.
- Dashboard/accessibility: DashboardScreen.kt, SensorFormatting.kt, ConnectionStatus.kt. Dashboard models, editing operations, and DataStore production implementation were retained.
- Regression tests: new BleConnectionManagerTest.kt, BleScannerTest.kt, BleDeviceRepositoryTest.kt; expanded DeviceCapabilitiesParserTest.kt, SensorFormattingTest.kt, DashboardPersistenceViewModelTest.kt; shared fake repositories adapted for scan/environment interface additions in SharedDeviceSessionTest.kt and DashboardEditingTest.kt.
- Build/documentation: app/build.gradle.kts adds Android-resource JVM testing plus test-only Robolectric/Mockito; this report documents findings and release validation limits. Other pre-existing Sprint files remain uncommitted and were not discarded.
