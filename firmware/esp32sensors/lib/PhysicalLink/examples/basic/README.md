# Basic synthetic dashboard

Copy `main.cpp` into your own PlatformIO project's `src/` and use the archive
installation configuration in the [library README](../../README.md).
Run `pio run`; physical sensors are not required. This example uses only the
public `PhysicalLink.h` API. Temperature and humidity are synthetic values.

After you flash your board, connect using the PhysicalLink Android app to see
its dashboard. Keep the stable sensor IDs when replacing synthetic values with
your own acquisition code. Report failed acquisitions with `unavailable(id)`.
