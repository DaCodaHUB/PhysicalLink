# Dashboard customization (Sprint 2A)

The existing DashboardLayout remains the configuration source. DashboardWidget now retains a hidden flag. Immutable domain operations reorder, toggle spans, hide, restore, and add VALUE instances. The ViewModel combines this configuration with repository metadata and live values, plus edit mode. No BLE or GATT behavior changes and no production dependencies were added. Coroutine test dispatchers are used only in JVM tests.

Edit freezes the default layout only when a configuration operation occurs. Done retains edits; Sprint 2B now persists them through DashboardLayoutRepository (see dashboard-persistence.md). Reset (after confirmation) returns to metadata-driven defaults, discarding hidden widgets and extra instances. Default IDs remain deterministic; added instances get UUIDs. Widget identity and SensorSource never change during reorder, resize, hide, or restore. Missing configured sources remain unavailable; newly discovered sensors can be added from Add Widget.

The responsive grid has two columns at widths of at least 360dp and one below that. Spans are clamped to available columns for display; a two-column preference survives a narrow window. Move Up/Down follows visible list order, skipping hidden widgets. Restoration keeps the retained list position. Editing controls are also available to accessibility services.

## Drag behavior and limitations

Long-press a drag handle, move over another visible widget, and release. The card moves with the pointer, the target gets an outline and Drop here label, and release moves the widget to the target's list position. Cancellation or dropping into empty space leaves ordering unchanged. Reordering commits on release, so sensor updates continue without rearranging the grid during a drag.

Dragging is limited to visible targets. There is no edge auto-scroll; scroll first or use Move Up/Down for off-screen destinations. Grid scrolling is disabled while dragging. This is list ordering with spans, not free placement or a persistent row/column coordinate system. There is no resize gesture; width changes use explicit controls. Sprint 2B adds persistence; charts, gauges, and simultaneous multi-device sessions remain out of scope.

## Validation

Unit tests cover immutable reorder and boundaries, hidden-item skipping, resize, hide/restore, duplicate sensor instances, identity/source preservation, changing metadata, reactive live updates during editing, and ViewModel reset. Gradle test/build also checks existing tests and Android lint. Touch interaction, accessibility-service behavior, orientation changes, and BLE hardware require manual device validation; automated unit tests do not establish those behaviors on hardware.
