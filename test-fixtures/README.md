# Android protocol test fixtures

`protocol-v2/numeric-v2.json` is Android's golden protocol-v2 fixture. Its bytes
are unchanged from the original firmware fixture.

Android adds this directory only to its unit-test resources and loads the JSON
from the classpath. Android APK builds and unit tests do not need firmware source,
C++, or PlatformIO. Keep this directory in Android-only checkouts that run tests.

The [separate firmware repository](https://github.com/DaCodaHUB/PhysicalLink-firmware)
owns an identical copy under `test-fixtures/protocol-v2/numeric-v2.json`. Its native
codec and metadata-page verification scripts load that firmware-owned copy.
For an intentional fixture update, review the wire-format impact, transfer the
identical reviewed JSON to both repositories, compare SHA-256 hashes, and run
both firmware native tests and Android protocol, codec, and connection-manager
tests. Record the counterpart revision in each review. Do not regenerate golden
vectors merely to make a failing encoder pass.
