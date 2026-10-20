# Sprint 3A: Complete Android UI redesign

## Audit and scope

The existing app already had two routes, working BLE/GATT ownership, dynamic DashboardState, immutable edit operations, and device-scoped DataStore persistence. The previous UI refinement still used large body headings, separate device-card buttons, a dashboard status badge/body header, and an Options menu below each widget. This iteration replaces those presentation patterns without replacing the architecture.

## Devices

The compact Material 3 app bar contains PhysicalLink/Devices and one state-aware Scan/Stop action. Nearby compatible projects are compact fully tappable rows: tapping the row requests a connection, while the independent Details icon opens read-only information. Rendering a list or opening Details never connects automatically. The existing explicit-connect flow opens Dashboard once that user-requested connection reports Connected.

Friendly Strong/Good/Weak signal labels summarize the last scan RSSI; they do not imply exact distance or a live measurement. Invalid/unavailable RSSI values have an explicit unavailable label. Raw RSSI remains in Device Details. Empty, scanning, connecting, connected, Bluetooth-disabled, permission-denied, and error states stay distinct. Error diagnostics remain in Details; the main screen provides recovery guidance. Connecting can be cancelled. The existing scan timeout/lifecycle behavior is retained.

## Dashboard

The device name and inline text connection status share a compact app bar. Edit is an accessible header icon. Device Details and Disconnect are in overflow rather than the primary content. The former large Dashboard/body header is removed: readings start immediately below the app bar when ready. Loading, empty, unavailable, and save-error feedback appears only when needed.

Widget cards use consistent Material 3 surfaces and spacing, prominent values, adjacent/wrapping units, and full-name semantics. Long numeric readings receive smaller text and can wrap rather than being clipped or silently shortened. Existing numeric rounding/safety behavior is preserved; no values or units are fabricated. Layout still supports two columns and full-width spans; narrow windows or large font scales display one column without rewriting stored sizing preferences.

The current sensor stream has no reading timestamps. No last-update time or fresh/stale claim is invented. Device Details continues to explain this limitation. Future Gauge/Chart/Range containers remain explicitly unavailable, without fake plots, scales, or decorative gauges that appear functional.

## Edit mode and configuration

The compact editing app bar reads Edit dashboard, device name, Done. Drag handles and labeled Configure actions are inside each card header. Existing long-press drag movement, outlines, drop targets, stable item keys, and commit-on-release behavior are retained. Card accessibility actions and the configuration sheet offer Move up/down alternatives.

The Material 3 configuration bottom sheet shows the sensor name, source project, capability ID, device identifier, Half/Full width selection, visualization, Duplicate, Hide, and accessible reorder controls. Boundary moves and duplication of unavailable sensors are disabled. The sheet follows the selected widget's stable ID through reordering and uses current DashboardState while readings update. It is dismissed if the widget disappears, edit mode ends, or the selected device changes.

Only Value is offered as a visualization choice because it is the only supported renderer. Saved Gauge/Chart widgets remain readable as unavailable configurations and can be explicitly converted to Value without changing their identity/source/order/visibility. Unsupported Gauge/Chart/Range choices are not offered as if they work. The UI-only Range presentation kind is not added to persisted WidgetType.

Add widget is at the bottom of the grid in edit mode. It opens a Material 3 sheet with retained hidden instances first, followed by available sensors for new Value instances. A hidden-count hint makes restoration discoverable. Reset is also at the grid bottom and still requires confirmation; cancelling preserves the layout. Back exits edit mode; settings sheets handle their own dismissal first. Edits remain immediately queued through the existing persistent repository rather than waiting for Done.

Half/Full selection uses an explicit idempotent resize operation. It intentionally does not translate a selected width into a toggle, which could undo itself when repeated taps queue before the first DataStore commit. Value selection similarly changes only the existing type field. All other editing and save operations use the same repository and state holder.

## Architecture and serialization compatibility

- Exactly two Navigation Compose destinations remain: Devices and Dashboard. Sheets and overflow do not add routes.
- BLE repository, manager, GATT queue, scanner, protocol, firmware, Hilt bindings, and DataStore implementation are unchanged in this iteration.
- The version-1 JSON serializer and DashboardLayout/DashboardWidget/SensorSource fields are unchanged. Enum values remain VALUE, GAUGE, CHART. No migration, automatic conversion, reset, or saved-data deletion occurs.
- Resize sets columnSpan to 1 or 2. Explicit Value selection sets the existing type field only. Instance ID, source, order, rowSpan, and hidden state are preserved. The old toggle operation remains available for existing callers/tests.
- UI action callbacks and card controls are ephemeral presentation events, not another layout source of truth. Application-scoped queued writes still capture their original device key and survive navigation.
- No production or test dependencies were added in this iteration. Native Compose glyphs avoid adding an icon library. Light/dark/dynamic Material 3 themes are preserved.

## Changed files in this iteration

Production Kotlin paths are relative to app/src/main/java/com/dacodahub/physicallink:

- core/designsystem/ActionGlyph.kt (new): lightweight native action glyphs for labeled 48dp controls.
- core/designsystem/ConnectionStatus.kt: inline device status text for compact headers.
- feature/devices/DevicesScreen.kt and DeviceControls.kt: compact header, one scan action, tappable rows, signal labels, and existing state feedback.
- feature/dashboard/DashboardScreen.kt: compact view/edit app bars, overflow, grid-first content, in-card controls, configuration-sheet ownership, bottom Add/Reset actions, stable drag behavior, and device-keyed UI state.
- feature/dashboard/WidgetConfigurationSheet.kt (new): source, explicit sizing, supported visualization, duplicate/hide/reorder controls.
- feature/dashboard/WidgetEditControls.kt: in-card drag/configuration header instead of a detached Options menu.
- feature/dashboard/WidgetContainer.kt, SensorCard.kt, DashboardWidgetCard.kt: shared card chrome, flexible large readings, unit placement, and retained unsupported/missing-source behavior.
- feature/dashboard/AddWidgetDialog.kt: replace the old add dialog with AddWidgetSheet for hidden restoration and adding sensors.
- feature/dashboard/DashboardViewModel.kt and domain/model/DashboardEditing.kt: explicit idempotent resize and Value-selection commands using existing immutable persistence transformations.
- app/src/test/java/com/dacodahub/physicallink/feature/DashboardUiTest.kt: updated/expanded Compose semantic UI tests.
- app/src/test/java/com/dacodahub/physicallink/feature/dashboard/DashboardEditingTest.kt: idempotent selection and association-preservation regression.
- app/src/test/java/com/dacodahub/physicallink/data/repository/DashboardPersistenceTest.kt: repeated selections and real-file reopen regression.
- app/src/debug/java/com/dacodahub/physicallink/preview/PhysicalLinkPreviews.kt: updated previews plus compact device rows and the configuration sheet.
- docs/sprint-3a-ui.md: this current report, superseding the earlier UI simplification report.

Prior uncommitted sprint/audit changes were retained; they are not all new changes in this iteration.

## Validation and previews

Final validation passed: `.\gradlew.bat test lint build` completed with BUILD SUCCESSFUL. All 91 tests passed with zero failures, including 13 Compose UI tests plus state/persistence regressions. Debug and unsigned release APKs were built. Lint reported zero errors and 28 non-blocking warnings (update notices, template resources, and style/catalog suggestions); warnings were not suppressed. All 12 debug-only preview configurations compile. `git diff --check` passed.

Compose/Robolectric tests cover scan transitions, friendly signals, explicit row connection versus Details, connecting cancellation, compact dashboard editing, overflow diagnostics, supported-only choices, idempotent width selection, duplicate-source identity, reorder boundaries/stable IDs, hide/restore, reset confirmation, live values, and sheet dismissal on device switching. Existing BLE/scanner and persistent-layout tests remain in the suite. Real-file DataStore tests additionally repeat explicit width/type selections, reopen storage, and verify identity/source/visibility.

Select Android Studio's debug variant and open PhysicalLinkPreviews.kt in Design/Split mode. Preview configurations include light/dark/narrow/large-text Dashboard, tablet editing, Devices states and compact rows, an unavailable Range frame, and the Value-only configuration sheet. Sensor readings are null in previews. The sample advertisement RSSI is explicitly a debug-only design fixture, not a physical-device observation. Preview code is excluded from release builds. These are supplied Compose previews, not claimed physical-device screenshots.

## Known limitations and manual Android checks

No physical-device or emulator verification was performed. Before release:

1. Connect a current ESP32 project by tapping a device row; confirm Details does not connect, Scan/Stop follows real callbacks, permission/Bluetooth-disabled/error flows recover, and connecting can be cancelled.
2. Verify real readings/units update while dragging, resizing, reordering, and while settings is open. Check long names, very large readings, large fonts, narrow/split windows, landscape, light/dark/dynamic colors, and system insets.
3. Use TalkBack/Switch Access to reach labeled icon buttons, sheet width choices, Move up/down, Duplicate, Hide, Add/Restore, Reset, and Done. Verify sheet scrolling and system Back/dismiss behavior near screen edges.
4. Open an existing customized version-1 layout; verify IDs, duplicates, hidden records, ordering and widths survive navigation, unexpected disconnect, restart/reconnect, and switching between two separately connected devices. Cancel reset, then confirm reset separately.
5. Verify drag outlines/drop targets and cancellation on device changes. Dragging remains limited to visible targets without edge auto-scroll; use accessible reorder actions or scroll first for off-screen moves.
6. Render previews in Android Studio and install a signed release on representative Android versions. Semantic tests and preview compilation do not establish physical BLE behavior, pixel-perfect rendering, OEM bottom-sheet/back behavior, or uninterrupted background connections.

DataStore commits remain asynchronous; a very recent edit can be lost if the process is killed before commit. BLE address remains the temporary saved-layout identity. Reading freshness cannot be determined without real timestamps. Gauge, Chart, and Range are not implemented or offered as selectable visualizations.
