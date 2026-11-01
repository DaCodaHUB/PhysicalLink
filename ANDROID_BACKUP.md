# PhysicalLink Android backup audit and configuration

Audited 2026-10-31. This report supersedes the backup-rule findings in the earlier release-readiness audit. `allowBackup=true` remains unchanged. No DataStore filenames, keys, codecs, device identities, BLE communication, UI, firmware, analytics, or signing configuration changed.

## Previous behavior

The manifest references `@xml/backup_rules` through `android:fullBackupContent` and `@xml/data_extraction_rules` through `android:dataExtractionRules`. These are the repository's actual filenames; there are no files named `fullBackupContent.xml` or `dataExtractionRules.xml`.

| Platform | Before this change | After this change |
| --- | --- | --- |
| Android 7–11, API 24–30 | Empty `full-backup-content` meant broad default inclusion of eligible app files/preferences/databases. The old template comment incorrectly suggested this file was ignored below API 31. | Legacy rules include the two preference files below. |
| Android 12+, API 31+ with this app's target SDK 37 | Empty cloud rules and no device-transfer section left broad default inclusion for both modes. | Both cloud and device-transfer sections explicitly include the same two files. |

Cache, code-cache, and no-backup directories are excluded by Android independently. An explicit include list replaces broad default inclusion. Backup eligibility is not a guarantee that a backup or restore has occurred. [Android Auto Backup documentation](https://developer.android.com/identity/data/autobackup).

## Implemented policy and local data

The `file` domain is relative to the app's private `filesDir`, normally `/data/user/0/com.dacodahub.physicallink/files/`. Preferences DataStore uses files under `datastore/`, not Android's `shared_prefs/` directory.

| Eligible file, relative to `filesDir` | Contents preserved |
| --- | --- |
| `datastore/dashboard_layouts.preferences_pb` | Per-device layouts, widget instance IDs/types/order/spans/visibility, device and sensor source IDs, visualization bounds, thresholds, precision, chart window and stale-after settings. Also the separate Demo layout and introduction-dismissed/demo-explored flags. |
| `datastore/appearance.preferences_pb` | Existing `appearance_mode`: Light, Dark, or System. System follows the destination phone's system appearance. |

Both files are included for legacy backup, cloud backup, and Android-to-Android device transfer. Exact filenames avoid including temporary DataStore siblings, coordination files, future diagnostic files, other preferences, databases, or external files. No encryption-capability requirement was added that would disable otherwise supported backups. New persistent features must be reviewed before their files are added; changes within either included DataStore also need a privacy review.

**No application-level sensitive credential store was found.** Source review found no file writer for BLE connection state, scan results, sensor readings/history, or diagnostic logs. These runtime values remain in memory; Android/JVM diagnostic logging is not an app-managed log file. They require no new BLE code or synthetic exclusion paths. Unapplied Details drafts are also not saved to DataStore. No file-backed reading-history store was found.

## Restored identities and compatibility

- The existing `layout:<deviceId>` key and version-2 layout codec remain intact; existing version-1 migration/compatibility tests remain in place.
- V2 sources keep `v2:<firmware device UUID>` plus the sensor's stable ID. Changing a board's display name or session epoch does not change that mapping. Legacy sources retain their Bluetooth-address-based identity; if firmware/address/sensor IDs change, an old layout cannot automatically be mapped to a new identity.
- Restored IDs identify previously configured sources. They do not create scan results, restore pairing/permissions, or establish an active connection. There is no persistent known-device list UI being added: saved layouts are looked up when the corresponding source is discovered/selected again.
- A fresh BLE repository starts disconnected, with no selected address/dashboard ID, metadata, readings, retained history, or discovered devices. It does not auto-connect from restored preferences. Real readings require a new successful session.
- Duplicate widgets preserve separate IDs/settings, even when they use the same sensor. Demo keeps its existing `physicallink:demo:sensor-station:v1` namespace; restoring or resetting Demo cannot replace a real board's layout. Demo's virtual availability is not a real BLE connection.

## Automated verification and files changed

Production changes are limited to:

1. [backup_rules.xml](app/src/main/res/xml/backup_rules.xml): exact legacy file includes.
2. [data_extraction_rules.xml](app/src/main/res/xml/data_extraction_rules.xml): matching cloud and device-transfer includes.

Tests:

- [BackupRulesTest.kt](app/src/test/java/com/dacodahub/physicallink/data/repository/BackupRulesTest.kt): checks packaged XML resources, equivalent allowlists against real DataStore path resolution, and the manifest's backup-enabled flag.
- [BackupRestorePersistenceTest.kt](app/src/test/java/com/dacodahub/physicallink/data/repository/BackupRestorePersistenceTest.kt): writes real DataStore files, closes them, copies only paths from the legacy XML into a fresh directory, then reopens them. Checks exact preference compatibility; layout order/spans/visibility; Chart/Gauge/Range settings; independent duplicates; stable v2 and legacy mappings; Demo isolation; onboarding flags; appearance; and exclusion of temporary/log files. Structural codec assertions ensure only configuration fields are persisted, with no reading/session fields.
- [BleDeviceRepositoryTest.kt](app/src/test/java/com/dacodahub/physicallink/data/repository/BleDeviceRepositoryTest.kt): adds a fresh-process disconnected/empty-state check and verifies no automatic scan or device lookup.

This document is the remaining changed file. The manifest itself did not need modification. No `.gitignore` changes, commits, pushes, or publication were performed.

Validation: `.\gradlew.bat test lint build` **passed**; 263 tests, zero failures/errors/skips. Lint reports zero errors and 15 existing warnings (dependency updates and unused template launcher drawables). File-copy tests are storage-restoration tests, **not** execution of Android's Backup Manager or an OEM transport.

## Remaining manual backup/restore testing

Use disposable test devices/profiles and the same application ID with an accepted signing/version relationship. Do not clear an existing user's app data merely to run this checklist.

1. Exercise Android 7–11 legacy backup and Android 12+ cloud restore separately; also test a real device-to-device setup transfer. Confirm the selected transport/user settings and inspect its backup contents where tooling allows. Follow [Android's backup/restore testing guide](https://developer.android.com/identity/data/testingbackup).
2. Before backup, customize multiple boards and Demo, duplicate a sensor with different settings, resize/reorder/hide widgets, set bounds/precision, and choose an appearance. Restore into a fresh installation and compare all settings. Repeat appearance checks for Light, Dark, and System.
3. Start with no ESP32 connected. Verify no discovered/connected board or readings/history are restored. Enter Demo, return, then explicitly discover/connect the same real board; check stable sensor mappings and fresh-session readings.
4. Verify permissions/Bluetooth-disabled states and destination-phone appearance independently. Inspect that temporary files and arbitrary diagnostic files are absent. Test backup-disabled/offline/no-backup-available cases without promising recovery.

No cloud backup, Backup Manager restore, account access, data wipe, or phone migration was executed in this task. Existing older backups were not inspected or erased. New rules do not promise to remove data already held in an older backup snapshot; validate upgrade/restore behavior as well as fresh backups.

## Privacy summary and information for the Play policy

PhysicalLink stores dashboard customization, configured board/sensor identifiers, Demo/introduction choices, and appearance preferences in app-private storage. These preferences are eligible for Android system cloud backup and device transfer. Live BLE state, scan results, readings, and session history are not saved in those files.

No transmission to PhysicalLink-operated servers was found: the app has no Internet permission, no server client, and the release dependency audit found no analytics or crash-upload SDK. This describes the inspected implementation; it does not mean data never leaves the phone. Android and its backup provider can transfer the eligible preferences under the user's system settings.

Users can remove local saved app data with Android **Clear storage** or uninstall. Reset layout only clears the selected device's customization, not all preferences. Deleting local data does not guarantee deletion of an existing system backup; users must manage system backups through their device/provider controls. Backup timing, availability, retention, security, restore eligibility, and transfer support are managed by Android/the provider, not guaranteed by PhysicalLink.

The final Google Play privacy policy needs the owner's actual developer identity/privacy contact and public policy URL. It should disclose the included board/sensor IDs and preferences, local BLE processing, system backup/transfer, local deletion versus backup deletion, and these limits. Do not invent a contact address or promise universal encryption, instant backup deletion, or guaranteed recovery. Eligibility of these two files is verified; provider/account behavior still needs manual verification.
