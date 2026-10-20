# Dashboard persistence (Sprint 2B, updated for Sprint 3B)

## Ownership and writes

DashboardLayoutRepository is the domain interface. DataStoreDashboardLayoutRepository owns a FIFO command queue on an application coroutine scope and wraps a single Preferences DataStore created by the singleton Hilt DashboardModule provider. Persistence remains separate from BLE, GATT, and in-memory reading history. DataStore Preferences is the only new production dependency.

DataStore is the layout source of truth. Each editing command transforms the latest stored layout atomically in a DataStore edit transaction. The ViewModel does not hold a second optimistic configuration. Observed commits drive the existing DashboardState and widget rendering. Rapid commands are applied in submission order, including reset. Commands accepted before navigation continue in the application scope, even when the originating ViewModel is cleared. There can be a brief disk-commit delay before an edit appears. Sensor readings combine independently with observed configuration and never trigger disk writes.

Loading uses device-keyed flatMapLatest observation, plus a device-ID guard before presenting a loaded layout. Commands capture the BLE address and metadata at submission, not completion. Old writes can finish only under their original device key. Reconciliation transformations work against the current stored layout and never recreate a customization that a queued reset has already removed.

## Format

File: app-private files/datastore/dashboard_layouts.preferences_pb. One string preference per device, keyed by layout:<BLE address>. BLE addresses are temporary identities; address randomization or changing devices loses the identity match. Values are versioned JSON records inside the Preferences protobuf container:

```json
{
  "version": 2,
  "deviceId": "AA:BB:CC:DD:EE:FF",
  "widgets": [
    {
      "id": "independent-widget-id",
      "type": "VALUE",
      "deviceId": "AA:BB:CC:DD:EE:FF",
      "capabilityId": "temperature",
      "columnSpan": 2,
      "rowSpan": 1,
      "hidden": false,
      "settings": {
        "chartWindowSeconds": 60,
        "minimum": null,
        "maximum": null,
        "lowerThreshold": null,
        "upperThreshold": null,
        "precision": 2,
        "staleAfterSeconds": 15
      }
    }
  ]
}
```

Array order is widget order. VALUE, GAUGE, CHART, and RANGE enum names round-trip. Gauge/Range need explicit finite ordered bounds; migrated unconfigured widgets ask for bounds. Duplicate sources are valid; widget IDs must be unique. IDs and capability IDs must be nonblank, sources must belong to the record's device, columnSpan must be 1 or 2, and rowSpan must be positive. No sensor values or device capability JSON are persisted.

Missing key means no customization. An explicitly empty widget array is a saved empty layout, distinct from a missing key. Default generation happens only after a successful load finds no customization. Defaults are not saved until an actual edit occurs.

## Capability reconciliation and reset

A customized layout retains order, IDs, sizes, hidden widgets, extra instances, and missing sources. When connected metadata changes, genuinely new sources append as default VALUE widgets. A hidden widget still counts as representing its source and is never restored automatically. Existing widgets are not removed when capabilities disappear or discovery temporarily yields null. Returning sources resolve again through live DashboardState. New widgets use deterministic default IDs with collision suffixes if necessary.

Reset confirmation clears only the current device's preference, then rendering returns to current generated defaults. A stale reconciliation after reset sees null and leaves it null. Reset does not delete another device's layout.

## Recovery and errors

Wrong preference value types, malformed JSON, unknown schema versions or widget types, foreign device IDs, invalid dimensions, or duplicate widget IDs reject the affected record. Recovery logs a warning and removes that exact record only if its raw value still matches; a newer concurrent save is protected. Rendering then uses current defaults. Other device records survive. Version 1 records load with default WidgetSettings and migrate to version 2 through compare-and-replace, preserving IDs, sources, sizing, visibility, and order. Concurrent edits/reset cannot be overwritten by migration. Integer settings must be integral numbers; bounds/thresholds must be valid finite numbers. Invalid settings reject the affected device record under the same recovery policy. Unknown future data is currently discarded rather than preserved for downgrade recovery.

If the Preferences protobuf file itself is corrupt, ReplaceFileCorruptionHandler logs a warning and replaces the entire container with empty preferences; all devices return to defaults. Regular IO failures are not treated as corruption: failed loads show a retry message and retry after one second; failed saves log a warning and show an error while retaining committed configuration. Failed edits are not silently cached or automatically replayed; the user can try the operation again. Normal DataStore atomic-write durability applies; force-killing the process before a queued write commits can lose that last edit. BLE addresses and configuration are local app data, subject to Android app backup settings.

## Validation and manual checks

Automated JVM tests use real temporary DataStore files with the Okio storage adapter (Android Build.VERSION stubs select a nonportable file replacement path on Windows); Android production uses PreferenceDataStoreFactory.create with File storage. Tests share the production corruption handler and repository. They close/reopen scopes to simulate storage recreation, and exercise round trips, reset, per-device isolation, corrupt records, incompatible schemas, whole-file corruption, rapid queued edits, reconciliation, delayed old-device loads, navigation while writes are blocked, reconnecting, failed saves, and live sensor updates during pending persistence. Existing domain and session tests remain in the suite. Gradle test, lint, and build validate compilation, unit behavior, and static checks.

Manual Android-device checks remain necessary for process restart/force-stop timing, app navigation, touch dragging and accessibility, actual filesystem failure behavior, BLE reconnect timing and changing capabilities, and BLE-address stability. Automated storage and fake-repository tests do not establish physical BLE behavior. Existing drag limitations (visible targets only, no edge auto-scroll) still apply.

## Sprint 2B file inventory

- app/build.gradle.kts: add Jetpack DataStore Preferences.
- domain/repository/DashboardLayoutRepository.kt: device-scoped Flow observation and ordered atomic transformation contract.
- domain/model/DashboardReconciliation.kt: append new sources without altering saved widgets.
- data/repository/DashboardLayoutCodec.kt: versioned JSON serialization and validation.
- data/repository/DataStoreDashboardLayoutRepository.kt: application-owned write queue, device isolation, record recovery.
- data/repository/DashboardDataStore.kt: production DataStore factory and shared corruption handler.
- di/DashboardModule.kt: singleton Hilt repository, DataStore, and application IO scope.
- feature/dashboard/DashboardViewModel.kt: replace in-memory configuration with persisted observation and commands; handle load/save errors and reconcile capabilities.
- feature/dashboard/DashboardState.kt: layout-loading and persistence-error state.
- feature/dashboard/DashboardScreen.kt: loading/error feedback, disable editing while loading, dismiss customization dialogs on connection transitions.
- test FakeDashboardLayoutRepository.kt and TestDashboardDataStore.kt: domain-test fake and portable real-file DataStore fixture.
- test data/repository/DashboardPersistenceTest.kt: file round trips, reset, isolation, corruption, rapid writes, codec validation, reconciliation.
- test feature/dashboard/DashboardPersistenceViewModelTest.kt: stale loads, queued writes across navigation/reconnect, independent live values, capability changes, reset, failed saves.
- test feature/dashboard/DashboardEditingTest.kt and feature/SharedDeviceSessionTest.kt: inject the layout repository and initialize the test Main dispatcher where needed.
- docs/dashboard-customization.md: update in-memory lifetime statements for Sprint 2B.
- docs/dashboard-persistence.md: format, ownership, recovery, validation boundaries, and this inventory.

Production Kotlin paths above are relative to app/src/main/java/com/dacodahub/physicallink; test paths are relative to app/src/test/java/com/dacodahub/physicallink. Existing uncommitted Sprint 1/2A files were retained.
