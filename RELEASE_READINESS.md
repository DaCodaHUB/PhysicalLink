# PhysicalLink — Android Maker Beta release-readiness audit

Audit date: **2026-10-31 (America/Los_Angeles)**. Intended distribution: Google Play **internal testing only**.

**Verdict: the application builds and its automated checks pass, but the generated release bundle is unsigned and must not be uploaded as-is. Privacy-policy publication and in-app access are also unresolved.** No application, firmware, manifest, dependency, persistence-key, version, signing, or `.gitignore` changes were made during this audit. This report is the only repository change outside existing ignored artifact directories. No credentials were read or created; no commit, push, publication, or Play upload was performed.

## 1. BLOCKERS — resolve before upload / release

| Finding | Evidence | Required action |
| --- | --- | --- |
| **Unsigned release artifact** | No release `signingConfig` in [app/build.gradle.kts](app/build.gradle.kts). `bundleRelease` succeeds, but `jarsigner -verify` reports `jar is unsigned`; the AAB has no signature entries. A task named `signReleaseBundle` is not proof of a signature. | The owner must choose the upload-key/Play App Signing arrangement, provide signing through a private local or CI mechanism, and generate and verify a signed AAB. Do not use the debug key for Play. Do not place passwords or keystores in this repository. |
| **No privacy policy or in-app policy access** | Source/resource review found no privacy-policy text, URL, screen, or action. Getting Started is not a privacy policy. | Owner supplies the developer/privacy contact, approved policy, and public hosting URL; then add a clearly labeled in-app policy link or text. Disclose the actual BLE, storage, backup, and retention behavior described below. No identity, contact, or hosting address was invented. |
| **Play account/package status cannot be verified locally** | Package is `com.dacodahub.physicallink`, version code `1`; no Console access was used. | Account owner confirms package ownership/availability, that code `1` is unused, account verification, applicable declarations, and upload-key enrollment. This is an external prerequisite, not evidence that the current ID or version is wrong. |

Google's general [User Data policy](https://support.google.com/googleplay/android-developer/answer/10144311?hl=en) calls for a public privacy-policy URL in Console and policy text or a link inside the app, including developer/contact, handling, retention, and deletion information. Internal testing may accept an incompletely configured app and may receive different review treatment; that does not establish privacy compliance. [Internal-testing setup](https://support.google.com/googleplay/android-developer/answer/9845334?hl=en).

**Credential/account boundary:** signing-key selection, Console checks, policy ownership/contact, and hosting decisions are left to the owner. Work stops before those actions; a build success is not permission to upload.

## 2. IMPORTANT — address before inviting testers

1. **Test the signed release on physical devices.** This audit ran JVM/Robolectric checks, not a physical application walkthrough, BLE session, or signed-release installation. Device enumeration found a connected Pixel 3a; it was not installed onto, launched, reset, or otherwise used for validation. Test Android 11 or earlier, Android 12+, and the current target platform. The existing instrumentation test is a package-name check, not a release smoke suite.
2. **Approve the backup policy and disclosures.** `allowBackup=true`; both backup XML files are essentially Android Studio templates, with no effective exclusions. Dashboard identities/settings, onboarding state, and appearance preferences are eligible for OS backup/device transfer. Decide whether to keep and disclose that behavior or intentionally restrict it. No backup change was made during this audit.
3. **Prepare reviewer/tester instructions.** Explain the exact Demo Mode path and how to switch the default Value widgets to the other presentations. Include compatibility limits, session-only history, and a feedback channel. Demo is sufficient to explore the UI without an ESP32, but it does not validate real BLE behavior.
4. **Make firmware navigation convenient.** Getting Started includes the correct public repository URL as ordinary `Text`, without a tappable link or selection container. The repository and `main/DEVELOPMENT.md` both returned HTTP 200 without authentication. Their content and compatibility with a particular flashed board were not tested.
5. **Replace template launcher artwork and prepare listing materials.** Adaptive/round/monochrome resources exist, but the foreground remains the Android Studio robot template. No approved branded Play icon, feature graphic, listing copy, support contact, or store-ready screenshot set was found in the repository. These are not all prerequisites for an initial incompletely configured internal track; they are outstanding listing/readiness work, not reasons to redesign the UI.

## 3. OPTIONAL — can wait until after beta

- Keep R8/resource shrinking disabled for this beta unless there is a demonstrated release need. Enabling them introduces a new validation obligation; their absence is not an upload blocker. No obfuscation mapping is produced.
- Review unconditional BLE diagnostic logging before wider distribution. Logs contain GATT status, operations/UUIDs, error details, and exception traces; no explicit sensor-stream upload or analytics logger was found. They are not gated to debug builds. Avoid putting user device details or readings into future logs.
- Plan dependency maintenance separately. Lint reports newer versions, not confirmed vulnerabilities or release-breaking incompatibilities. No speculative upgrades were made.
- Improve orientation handling of unsaved Details drafts if desired: drafts use `remember`, so recreation can discard unapplied edits. Saved settings remain intact. Physical rotation/keyboard testing remains outstanding.
- Decide later whether devices without BLE hardware should be eligible for the app's Demo Mode. The manifest currently requires BLE hardware; lack of an ESP32 does not prevent Demo use on supported phones.

## 4. Confirmed app configuration and artifact

Source: [app/build.gradle.kts](app/build.gradle.kts), [version catalog](gradle/libs.versions.toml), merged release manifest, and the **actual AAB's protobuf manifest/configuration**.

| Item | Confirmed value |
| --- | --- |
| Application ID / namespace | `com.dacodahub.physicallink` / `com.dacodahub.physicallink` |
| App/launcher label | `PhysicalLink` |
| Version | `versionCode=1`, `versionName=1.0` |
| Minimum / target SDK | `24` / `37` |
| Compile SDK | Source selects release API `37`, minor `1`; packaged manifest records compile API `37` |
| Build tools | AGP `9.3.3`; Gradle wrapper `9.5.0` with distribution checksum; daemon JVM criteria select Java `21`; Java source/target compatibility `11` |
| Release mode | Optimization explicitly disabled; no R8 minification/resource shrinking configuration or custom ProGuard rules. D8 release dex generation observed. |
| Debug exposure | Release manifest has no `debuggable` or `testOnly` opt-in, so default false. Main activity is exported for launcher use. No developer-only activity/deep link found. Demo is intentionally available in release. |
| Signing | No release signing configuration; generated AAB and release APK are unsigned |
| AAB | `app/build/outputs/bundle/release/app-release.aab` — **9,142,303 bytes**, one base module, three DEX files |
| AAB SHA-256 | `95210fcddaca3240bfcf04b2bc9aa84527fa2c4b2a0f3cef22cd936907aedd26` |
| Release APK | `app/build/outputs/apk/release/app-release-unsigned.apk` — **9,746,182 bytes**; not installable as a signed release |
| Bundle validation | Cached bundletool `1.18.3` validation API passed; actual bundle manifest decoded and inspected |
| Native packaging | Two AndroidX libraries for each of arm64-v8a, armeabi-v7a, x86, x86_64. All eight pass static ELF LOAD-segment alignment/congruence checks; the release APK's uncompressed `.so` offsets are 16 KB aligned. AAB config explicitly requests `PAGE_ALIGNMENT_16K`. No 16 KB runtime test was performed. |

Target API 37 exceeds the current API 36 minimum for new phone/tablet submissions. [Official target API requirement](https://developer.android.com/google/play/requirements/target-sdk). Native checks follow the distinction between bundle packaging, ELF alignment, and runtime testing in [Android's page-size guidance](https://developer.android.com/guide/practices/page-sizes); passing static checks is not runtime certification.

### Dependencies and SDK audit

The resolved `releaseRuntimeClasspath`, not just declared dependencies, was inspected. Main families are AndroidX Compose/Material, Activity, Core, Lifecycle, Navigation, DataStore, Hilt/Dagger, Kotlin/coroutines, Okio, annotations, and supporting AndroidX libraries. Notable resolved versions: Compose UI `1.11.0`, Material3 `1.4.0`, Activity `1.13.0`, Lifecycle `2.11.0`, Navigation `2.9.7`, DataStore `1.2.0`, Hilt `2.59.2`, Kotlin stdlib `2.2.20`, coroutines `1.9.0`.

No Firebase, Crashlytics, Sentry, advertising SDK, analytics SDK, Play Services identity/location SDK, or HTTP-client stack was found in that dependency graph. Release includes `ui-tooling-preview` metadata; the interactive `ui-tooling` and Compose test manifest are debug dependencies. JUnit, Robolectric, Mockito, and test-only JSON dependencies are not release SDKs. `DebugProbesKt.bin` from coroutines is packaged; its presence alone does not mean an active debugger or telemetry agent.

The merged manifest adds AndroidX Startup initializers for emoji, process lifecycle, and profile installation; an unexported startup provider; a DUMP-protected profile installer receiver; and a signature-protected internal receiver permission. These are not analytics endpoints. The manifest has **no Internet permission**. This is a static source/dependency/artifact audit, not a CVE scan or packet-capture attestation.

## 5. Permissions and privacy findings

### Bluetooth permissions

| Platform | Declarations and runtime behavior | Assessment |
| --- | --- | --- |
| Android 12+ | `BLUETOOTH_SCAN` with `neverForLocation`, plus `BLUETOOTH_CONNECT`; requested together only after an explicit scan action and explanation | Matches existing scan/connect functionality. No advertising or location request on this path. |
| Android 7–11 | Legacy `BLUETOOTH`, `BLUETOOTH_ADMIN`, and runtime `ACCESS_FINE_LOCATION`, all capped at API 30 | Appropriate legacy BLE handling. Guidance explains that the system Location setting may also be necessary. No location-coordinate API was found. |
| All supported devices | BLE hardware declared required; no background location, notification, storage, advertising-ID, camera, microphone, or Internet permission | No unrelated application permission found. |

Permission denial offers Settings/retry guidance. Scanner/connection code catches revocation, adapter-disabled/unavailable, failures, and timeouts; tests cover late callbacks and environment changes. Scans stop after 15 seconds and on screen/activity stop. The scanner temporarily receives broad scan results and filters to the PhysicalLink service in the callback before publishing devices. `neverForLocation` is consistent with the absence of location derivation; it can filter some beacon results, so confirm real supported firmware discovery on Android 12+. [Android Bluetooth permission guidance](https://developer.android.com/develop/connectivity/bluetooth/bt-permissions).

### Actual data handling

| Data / operation | Observed handling |
| --- | --- |
| Nearby board names, Bluetooth addresses, RSSI | Accessed during discovery/connection; displayed locally. Scan RSSI is labeled as a last-scan measurement. |
| Board/sensor identities | Stable firmware device ID for v2; legacy address-based identity where applicable. Device and capability IDs are saved as dashboard source identities. No identity/key changes were made. |
| Names, units, sensor values, availability, acquisition ages, diagnostics | Received from the connected board and held/displayed in memory. No server upload found. These are arbitrary maker sensors, not fabricated hardware capabilities or a medical claim. |
| Reading history | Bounded in-memory buffers: up to 64 sensors, 4,096 samples per sensor, 15-minute ingestion retention. A new session replaces the prior session. Last-session snapshots can remain in memory after disconnect; there is no disk history store. |
| Dashboard configuration | App-private DataStore `dashboard_layouts`; `layout:<deviceId>` stores widget IDs/types, source IDs, positions/order, spans, hidden state, and validated numeric settings. No readings are encoded by the layout codec. |
| Other preferences | Introduction/demo-explored flags in the existing dashboard DataStore; appearance in `appearance` DataStore. Demo uses its existing distinct device ID/layout. |
| Outgoing BLE traffic | Discovery/notification setup and protocol metadata/state synchronization control messages. No app account, phone identifier, analytics event, or device-output feature found in this path. |
| Logs | Local Android/JVM diagnostic logging. No custom crash upload or remote logging service found. OS/Play diagnostic services are separate from an app-bundled crash SDK. |
| Background behavior | No foreground monitoring service, history sync worker, or background monitoring feature. The application-scoped existing BLE connection can outlive a screen while the process survives; it is not a guaranteed background-monitoring service. |

DataStore is app-private storage, not application-level encrypted storage. The app does not enforce Bluetooth bonding/authenticated encryption or implement an additional encrypted channel. Do not promise encrypted BLE transport based on this audit.

**Backup caveat:** the current template rules permit system-managed backup/transfer of persisted preferences, potentially including board identifiers and customization. Android documents that default Auto Backup can include app files and preferences. Therefore, “nothing ever leaves the phone” is unsupported, even though no app/SDK server transmission was found. [Android Auto Backup](https://developer.android.com/identity/data/autobackup).

The owner-approved policy should describe these local accesses, their purpose, the actual backup choice, session-only history, diagnostic logs, lack of app accounts/telemetry, and deletion through Android Clear storage/uninstall. Reset layout clears customization for that device; it does not erase every preference. Explain OS backup retention separately rather than promising that uninstall immediately deletes every backup.

### Preliminary Google Play Data Safety answers

These are review inputs, **not a submitted declaration**. Internal-only apps are exempt from the Data Safety form; closed/open/production distribution changes that requirement. Local processing is distinguished from off-device collection in [Google's Data Safety guidance](https://support.google.com/googleplay/android-developer/answer/10787469?hl=en).

| Topic | Preliminary answer / remaining qualification |
| --- | --- |
| Collection by app/bundled SDKs | No off-device user-data collection found in audited code/dependencies/manifest. A provisional **No** requires owner confirmation of the backup treatment and unchanged distribution integrations; do not make an unconditional claim that the app accesses/stores no data. |
| Sharing / advertising / analytics | No app/SDK sharing, ads, or analytics found. No advertising ID permission. |
| Device identifiers, location, sensor data, app activity | Board identities, scan results, readings, and preferences are processed/stored locally as above. Legacy location permission is for BLE discovery, not observed GPS collection. No such data was found being sent to a developer server. |
| Encryption in transit | Do not assert a verified encryption guarantee. No app server transport exists, and BLE encryption was not established. Follow the form's conditional questions after finalizing collection/backup answers. |
| Accounts / deletion URL | No account creation or authentication found; account-deletion flow is not applicable to current functionality. Provide accurate local deletion/backup instructions in the policy. |
| Privacy-policy URL | Missing; must be supplied by owner and reflect the final app/SDK/backup behavior. |

## 6. Demo Mode and final UI verification

| Reviewer task | Evidence and result |
| --- | --- |
| Enter without an ESP32 or scan permission | Try Demo is available from Device Overview/introduction. Entry bypasses the scan permission flow. Onboarding tests exercise entry while permission is denied. Demo repository owns no BLE objects and its radio operations are inert. |
| See realistic, labeled readings | Simulated temperature, humidity, pressure, and soil-moisture channels update once per second with bounded smooth variation. Dashboard says `Demo Mode - Simulated readings`; widget/Details status also identifies simulation. These are demo channels, not inferred connected-board capabilities. |
| Access all four presentations | Default dashboard has four **Value** widgets. Edit dashboard → configure a numeric widget → choose Value/Chart/Gauge/Range. Gauge/Range require valid bounds. All four renderers are shared between Demo and hardware sources. This path is available, but there is no preset four-visualization demo dashboard. |
| Open matching Details | Type-specific Details resolve by device/sensor/widget IDs and share the parent Demo ViewModel. Navigation tests cover all four types, duplicate-widget routing, and retained Demo parent state. |
| Customize and persist | Demo ViewModel tests cover configure/resize/duplicate/hide/restore/reorder, recreation, and isolation from the real BLE source. Disk persistence tests cover Demo isolation and widget settings. |
| Exit safely | Demo Back/Devices pops to Device Overview; lifecycle tests cover simulation cancellation and restart. Navigation tests cover restoration/background transitions. No full signed-app physical navigation test was run. |

**Latest inline editing is complete in the audited source.** Value has decimal precision input; Chart has inline 1/5/15-minute choices, independent Auto/Custom axes, precision, and Auto reset; Gauge/Range have bounds, precision, and immediate valid previews. Existing Auto precision is not supported: the model uses integer precision 0–6. No top-right Details editor action or “Edit bounds and precision” navigation remains. Sensor information is read-only.

Drafts stay local until Apply; invalid drafts disable Apply, Cancel restores saved settings, and leaving Details discards unapplied input. Validation rejects nonfinite/unordered bounds and out-of-range precision, permits intermediate text, and supports finite signed/scientific values. Persistence and widget identity are shared with the dashboard, without new stores. Tests cover previews, invalid drafts, cancellation/reentry, duplicate isolation, layout preservation, and dashboard-scroll restoration.

Light/dark Compose captures were regenerated by the clean test run. Visual inspection included a Chart editing capture, a dark large-font Gauge, dark Value, and unavailable Range. Tests exercise 1.6× Details fonts, long labels/units, stale/unavailable states, missing widget/sensor, empty/single-sample charts, and persistence errors. Scrolling/wrapping retains access to controls; no redesign was made. [Screenshot index](artifacts/sensor-details-inline/README.md); fresh captures are in `app/build/reports/sensor-details-2/` and `app/build/reports/sensor-details/`.

TalkBack support is supported by source/semantics checks: labeled Back buttons and inputs, selected chips, headings, reading descriptions, chart summaries, bounded-state descriptions, Material field errors, polite persistence-error announcements, and accessible reorder alternatives. Primary Details actions/chips have 48 dp minimum sizing; Material supplies standard button/input touch behavior. **No spoken TalkBack session or physical IME/focus-order test was performed.** An automated semantics check is not accessibility certification.

## 7. Release assets and validation results

### Asset inventory

| Asset | State |
| --- | --- |
| App name/labels | PhysicalLink is consistently configured; user-facing source text is predominantly hardcoded English. |
| Launcher icons | Legacy density images plus adaptive, round, and monochrome declarations exist. Adaptive foreground is template robot artwork. |
| Privacy access | Missing; blocker above. |
| Help | Getting Started, replayable introduction, permission explanations, and device diagnostics exist. Guide describes compatible BLE ESP32 firmware and says Android does not flash boards. |
| Public firmware | `https://github.com/DaCodaHUB/PhysicalLink-firmware` and `.../blob/main/DEVELOPMENT.md` both verified HTTP 200. |
| Play listing | No finalized short/full descriptions, reviewer notes, support/privacy contact, category/audience/content-rating answers, approved 512×512 listing icon, or 1024×500 feature graphic found. Console-side state remains unknown. |
| Store screenshots | Existing images are QA captures, not an approved listing set. The 360×800 Details captures exceed the cited maximum 2:1 dimension ratio and should not be uploaded unchanged. No promotional assets were generated. |

Published listings need an icon, feature graphic, short description, and suitable screenshots; Google's current specs include a 512×512 PNG icon, 1024×500 feature graphic, and minimum two screenshots with size/format constraints. [Play asset requirements](https://support.google.com/googleplay/android-developer/answer/9866151?hl=en). Internal testing can begin before the listing is fully configured; confirm Console's actual requirements rather than treating all listing work as an AAB upload blocker.

### Commands and results

The repository wrapper is `gradlew.bat`; the requested `gradlew\\.bat` spelling was treated as that wrapper.

| Validation | Result |
| --- | --- |
| `.\gradlew.bat clean test lint build` | **PASS**, 2m 16s; 260 tests in 48 suites, zero failures/errors/skips |
| Lint | **0 errors, 13 warnings**; all concern newer dependency/tool versions, not manifest, permission, or UI correctness errors |
| `.\gradlew.bat bundleRelease` | **PASS**, 4s; actual AAB exists, but unsigned |
| Release dependency inventory | **PASS**, resolved graph saved and reviewed |
| `jarsigner -verify ...app-release.aab` | **Unsigned**; no signing certificate exists in this artifact |
| Actual AAB protobuf/config + bundletool validation | **PASS**; expected identity/version/SDKs, base module, 16 KB request; validation is not Play acceptance/signing validation |
| Static native checks | **PASS** for packaged ELF alignment and release APK zip offsets; no runtime claim |
| Public firmware URLs | HTTP **200** without authentication |
| Physical BLE / signed-release / TalkBack / OS backup-restore tests | **NOT RUN** |

Build warnings also note that the two prebuilt AndroidX native libraries could not be stripped; they were packaged unchanged. Static alignment checks still passed. No stripping or dependency changes were made merely to silence warnings.

Evidence: [artifact checks](artifacts/maker-beta-audit/artifact-verification.json), [test-suite results](artifacts/maker-beta-audit/test-summary.json), [lint](artifacts/maker-beta-audit/lint-results.txt), [resolved dependencies](artifacts/maker-beta-audit/release-dependencies.txt), [merged manifest](artifacts/maker-beta-audit/merged-release-manifest.xml), [actual bundle manifest](artifacts/maker-beta-audit/bundle-manifest.json), [bundle config](artifacts/maker-beta-audit/bundle-config.json), [bundle validation](artifacts/maker-beta-audit/bundle-validation.txt). `artifacts/` is already ignored; no ignore rule was changed.

## 8. Exact next actions

1. **Owner:** confirm the package/version in Play Console and choose/register the upload key / Play App Signing arrangement. Keep credentials private. Do not change the application ID or version based on speculation.
2. **Owner:** approve backup behavior, policy content, developer/privacy contact, and a public policy URL. Then implement policy access using that approved information. Keep all existing storage keys/identities intact.
3. **Release engineer:** build a signed AAB through the approved private signing mechanism. Verify its signature, package, version, target SDK, and hash again. The hash above identifies the current unsigned artifact only.
4. **QA:** install the signed release on dedicated test devices without clearing existing user data. Test cold start with no ESP32 and permissions denied → Demo → all four widget types/Details → inline valid/invalid Apply/Cancel → reopen/relaunch → exit to Devices. Exercise both themes, system Back, rotation, keyboard, largest fonts, and spoken TalkBack.
5. **Hardware QA:** on Android ≤30 and ≥31, test grant/deny/permanent denial/revocation, Bluetooth off/on, system Location off on legacy devices, real compatible ESP32 discovery/connect/reconnect, metadata failures, stale/unavailable readings, process recreation, and background/foreground transitions. Use separate compatibility checks for current target API and a 16 KB environment. Do not infer protocol or firmware compatibility from Demo.
6. **Owner/QA:** verify backup/restore and deletion behavior against the policy; recheck Data Safety answers if distributing beyond internal testing. Prepare the tester feedback channel, demo access notes, and initial release notes; finish listing assets when needed without claiming roadmap features.
7. **Owner only, outside this audit:** resolve Console declarations/account requirements and decide when to upload the verified signed bundle to internal testing. No Console changes or upload are authorized/performed here.
