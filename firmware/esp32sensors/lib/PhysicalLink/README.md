# PhysicalLink

Turn 1-16 numeric ESP32 sensors into a customizable Android dashboard over BLE.
Your sketch reads the sensors; PhysicalLink handles discovery, sensor metadata,
unavailable readings, and live updates. **The PhysicalLink Android app is required
for dashboard visualization.** The library does not draw a dashboard itself.

This is **PhysicalLink 0.9.0 Beta**, prepared for the first public beta release.
It has not been published. Release-readiness checks remain pending as documented
in the repository; do not assume an unpublished registry dependency is available.
The library package version is independent of BLE wire protocol version **2**.

## Supported setup

Validated build target: ESP32-WROOM-32 using PlatformIO board `esp32dev`,
`espressif32@7.1.3`, Arduino framework, and **NimBLE-Arduino exactly 2.5.1**.
Other ESP32 variants, Arduino core versions, ESP-IDF-only projects, and Arduino
IDE installation have not been validated. The manifest's `espressif32` category
is not a claim that every board in that platform works.

A single BLE peer and one owning application task are supported. Do not share
an independently initialized NimBLE stack with this library.

## Install from a local package

Create a PlatformIO ESP32 project. Place the freshly packed
`PhysicalLink-0.9.0.tar.gz` beside its `platformio.ini`:

```ini
[env:esp32dev]
platform = espressif32@7.1.3
board = esp32dev
framework = arduino
monitor_speed = 115200
lib_deps = file://PhysicalLink-0.9.0.tar.gz
```

PlatformIO installs the archive and the exact NimBLE dependency declared by the
library. No firmware-repository include paths or special build flags are needed.
Registry installation instructions will use the confirmed owner and published
version after release; this Beta has not been published.

The Git repository is a monorepo: adding its root Git URL to `lib_deps` does not
select this nested library. Use the standalone archive or, after publication,
the owner-qualified registry dependency. Keep the existing repository layout.

## First dashboard: synthetic readings

Copy [examples/basic/main.cpp](examples/basic/main.cpp) to your project's
`src/main.cpp`. It needs no physical sensors. The complete sketch checks startup
and update errors. The essential API is:

```cpp
#include <Arduino.h>
#include <PhysicalLink.h>

PhysicalLink device("My Greenhouse");
uint32_t lastSample = 0;

void setup() {
    Serial.begin(115200);
    if (device.addSensor("temperature", "Temperature", "C") != PLResult::Ok ||
        device.addSensor("humidity", "Humidity", "%") != PLResult::Ok) {
        Serial.println("Sensor registration failed"); return;
    }
    Serial.printf("PhysicalLink begin: %d\n", int(device.begin()));
}

void loop() {
    if (!device.isRunning()) { delay(1); return; }
    if (device.poll() != PLResult::Ok) {
        Serial.println("BLE error; stopping"); device.end(); return;
    }
    const uint32_t now = millis();
    if (uint32_t(now - lastSample) >= 1000) {
        lastSample = now;
        // Explicit synthetic demo values. Replace these with your sensor reads.
        const auto temperature = device.update("temperature", 20.0 + (now / 1000 % 10) * 0.1);
        const auto humidity = device.update("humidity", 45.0 + (now / 1000 % 5));
        if (temperature != PLResult::Ok || humidity != PLResult::Ok)
            Serial.println("Reading rejected");
        // For a failed sensor: device.unavailable("temperature");
    }
    delay(1);
}
```

Build with `pio run`. When you choose to flash your own board, use
`pio run -t upload`, then `pio device monitor --baud 115200`. No upload was
performed while preparing this Beta.

Open the PhysicalLink Android app, grant its requested Bluetooth permissions,
scan, select PhysicalLink, and connect. Open the dashboard and add Value, Gauge,
Chart, or Range widgets. Device Diagnostics helps inspect discovered sensors.
Synthetic values are demonstration data, not physical measurements.

For a BME280 and soil probe, follow the packaged
[real-sensor example](examples/real_sensors/README.md). Its helper and ESP32
Arduino `Wire`/ADC APIs are included; no Adafruit or other sensor library is needed.
The repository's default upload target remains the real-sensor application.
The internal 16-sensor stress harness stays in the repository as a development
example; it is not part of the public API examples in this package.

## Sensor values, IDs, and identity

Register **1-16 numeric sensors** before `begin()`. IDs are 1-32 ASCII bytes,
matching `[a-z][a-z0-9_.-]{0,31}`; keep them stable across reboots and updates so
saved widgets retain their bindings. Labels are up to 64 UTF-8 bytes. Unit labels
are up to 16 UTF-8 bytes (for example `C`, `%`, `hPa`, `raw`, or an empty string).
Units are labels; PhysicalLink does not convert or calibrate values.

Readings are finite double-precision numbers. Use `unavailable(id)` on acquisition
failure; NaN/infinity also marks a known sensor unavailable and returns an error.
A new session starts with all readings unavailable. The factory-eFuse-derived
device UUID stays stable on the same board, even between the basic and real-sensor
examples. Restarting the session changes its epoch, not the saved device identity.

Call `poll()` regularly, including while disconnected. Check `PLResult` values;
`Ok` means accepted locally, not delivery acknowledged by Android. `end()` safely
stops BLE and keeps registrations. Retry `end()` if it returns `BleStopFailed`;
do not assume the stack was released. See [API and lifecycle](API.md) for the
complete contract and failure behavior.

## Troubleshooting and limits

- Cannot find the board: check power, Bluetooth permissions, and matching firmware;
  disconnect any other client before scanning again.
- Build fails in NimBLE: use exactly 2.5.1 and the validated platform above. The
  backend deliberately rejects unsupported NimBLE versions.
- No readings: check startup/update results and keep calling `poll()`. A missing
  BME280 stays unavailable until initialization succeeds after a board restart.
- Wrong saved widgets: keep IDs and physical board identity stable. Alternate
  example sketches on the same board share its dashboard identity.
- Fast sampling: latest readings coalesce; this is not a lossless data logger.
  Live delivery is limited to 20 notification attempts per second in aggregate.
- There is no Wi-Fi/cloud service, text sensor type, or authenticated pairing
  implemented by this library. See the advanced guide for the BLE contract.

Builds and host tests do not prove radio reliability, ATT long-read interleaving,
or long-run heap/stack stability. Hardware acceptance is still required before
claiming production reliability for this Beta. See [API details](API.md) and
[third-party notices](THIRD_PARTY_NOTICES.md).

## License and maintainer

MIT; copyright (c) 2026 Dang Le. See [LICENSE](LICENSE). Maintainer: Dang Le.
Dependency licenses remain separate. The intended registry owner is `dannypov`,
pending account creation and username confirmation; no publication has occurred.
