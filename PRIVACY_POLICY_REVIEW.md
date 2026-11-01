# PhysicalLink privacy-policy link and verification

Reviewed 2026-10-31 against the current Android working tree, including the existing backup allowlist changes. Hosted page: [PhysicalLink Privacy Policy](https://dacodahub.github.io/physicallink/privacy/). An unauthenticated HTTPS fetch succeeded. The hosted page was not modified or published by this task.

## Application change

**Help → Getting Started → Privacy Policy** opens the supplied URL using an implicit browser intent. No WebView, network client, Internet permission, analytics, or browser dependency was added. The Material text button follows the existing guide's design and has a 48 dp minimum height.

If there is no browser, or device policy prevents the launch, a dismissible Material dialog shows guidance and a selectable URL. Closing it leaves the guide available. Direct intent launch avoids incorrectly rejecting browsers because of Android package-visibility filtering.

Files introduced/changed by this task:

- [GettingStarted.kt](app/src/main/java/com/dacodahub/physicallink/feature/devices/GettingStarted.kt): policy entry in the existing Help sheet.
- [PrivacyPolicy.kt](app/src/main/java/com/dacodahub/physicallink/feature/devices/PrivacyPolicy.kt): fixed URL, browser action, and fallback dialog.
- [strings.xml](app/src/main/res/values/strings.xml): policy/fallback labels.
- [PrivacyPolicyTest.kt](app/src/test/java/com/dacodahub/physicallink/feature/PrivacyPolicyTest.kt): entry, exact URL/intent, browser absence, restrictions, and dismissible fallback tests.
- This review document.

Existing backup XML, dashboard persistence, firmware, and BLE files were not changed by this task. The pre-existing backup changes remain in the working tree.

## Policy descriptions checked

| Topic | Current implementation and finding |
| --- | --- |
| Local preferences and identifiers | App-private DataStore holds dashboard/widget settings, associated source IDs, appearance, and Demo/introduction choices. The hosted policy describes local configuration/identifier storage. |
| Android backup | `allowBackup=true`; legacy, cloud, and device-transfer rules each include only `datastore/dashboard_layouts.preferences_pb` and `datastore/appearance.preferences_pb`. The policy acknowledges Android/provider-managed backup and separate retention, rather than claiming nothing leaves the phone. |
| Readings/history | `SessionSensorHistory` is bounded, memory-only storage, not a permanent database or part of either backed-up preference file. See the retention wording issue below. |
| App-operated analytics/cloud uploads | No analytics, advertising, crash-upload SDK, app-operated account/cloud client, or Internet permission was found in the current application configuration. Opening the policy sends a normal web request through the user's external browser; website hosting/browser/OS services are separate from app-operated sensor uploads. |
| Deletion | Clear storage/uninstall removes local saved app data. Reset layout affects the selected configuration. The policy correctly distinguishes local deletion from Android-managed backup retention. |

Relevant source: [backup rules](app/src/main/res/xml/backup_rules.xml), [cloud/device-transfer rules](app/src/main/res/xml/data_extraction_rules.xml), [DataStore providers](app/src/main/java/com/dacodahub/physicallink/di/DashboardModule.kt), [session history](app/src/main/java/com/dacodahub/physicallink/data/repository/SessionSensorHistory.kt), [manifest](app/src/main/AndroidManifest.xml), and [dependency catalog](gradle/libs.versions.toml). See [the backup audit](ANDROID_BACKUP.md) for file contents, restore tests, and manual transport limitations.

## Hosted-policy wording follow-up — resolved

The revised public page was fetched again on 2026-10-31. It now explains that temporary history may remain in memory after disconnection or session end while the process runs, and explicitly includes Demo/introduction choices in eligible preference backups. It also states that readings, history, and BLE connection state are excluded from backup/transfer. Both wording issues raised in the original review are resolved.

The Android wording check covered user-facing Kotlin text, string resources, diagnostics, Help, Sensor Details, and reset/deletion descriptions. Help's reference to current-session samples and Sensor Details' session-bounded history sentence were clarified to describe possible in-memory retention after disconnection. Existing accurate memory-only, local layout, and reset descriptions were retained. No extra privacy explanations were added to unrelated screens, and Help's policy URL/action is unchanged.

`endSession()` still marks the session inactive without clearing its buffers; `startSession()` clears the buffers. No app behavior, backup rules, DataStore, or BLE implementation was changed to match the wording, and no website edits were made. This is an implementation consistency review, not a legal-compliance certification or a packet-capture audit.

## Validation

`.\gradlew.bat test lint build` passed: 266 tests, zero failures/errors/skips. Lint: zero errors, 15 existing warnings (dependency updates and unused template launcher drawables). Tests use Compose/Robolectric; browser resolution and failure paths are simulated, not a physical-device browser walkthrough. No commit, push, or Android publication was performed.
