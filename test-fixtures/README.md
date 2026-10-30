# Shared protocol test fixtures

`protocol-v2/numeric-v2.json` is the single golden fixture for Android and native
firmware tests. Its bytes are preserved from the original firmware fixture.

Android adds this directory only to its unit-test resources and loads the JSON
from the classpath. Android APK builds and unit tests do not need `firmware/`,
C++, or PlatformIO. Keep this directory in Android-only checkouts that run tests.

The native `verify_fixtures.py` and `verify_pages.py` scripts locate this fixture
relative to their own repository location, independent of the working directory.
They compare production C++ output against the same golden vectors as Android.
Do not generate or copy a separate Android fixture.
