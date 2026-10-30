# Real-sensor dashboard

Copy both `main.cpp` and `Bme280.h` into your independent project's `src/`.
Use the configuration in the [library README](../../README.md) and run `pio run`.
This example uses only the public PhysicalLink API. Sensor acquisition uses the
ESP32 Arduino framework's `Wire` and ADC APIs plus the supplied BME280 helper;
no extra registry sensor library is required.

Validated build target: ESP32-WROOM-32 (`esp32dev`). Use common ground and
3.3 V compatible power/signals.

| Stable sensor ID | Unit | Wiring |
| --- | --- | --- |
| temperature | C | BME280 at I2C 0x76, SDA GPIO21, SCL GPIO22 |
| humidity | % | Same BME280 |
| pressure | hPa | Same BME280 |
| soil | raw | Capacitive probe analog output to GPIO34 / ADC1 channel 6 |

Soil readings are raw 12-bit ADC values (0-4095), not calibrated moisture
percentages. An unplugged analog input can float to a valid value; GPIO34 alone
cannot reliably detect disconnection. BME280 initialization failure requires a
board restart after correcting wiring. Failed reads publish unavailable readings;
successful subsequent reads recover from transient transfer failures.

The sketch samples once per second and polls BLE continuously. The BME280 helper
uses checked I2C transfers and compensation formulas described in the Bosch
BME280 datasheet. The library itself has no sensor-driver dependency.

After you choose to upload, scan and connect with the PhysicalLink Android app.
Check all four sensors, units, unavailable states, reconnection, and saved widget
bindings after restarts. Analog wiring, radio behavior, and long-run reliability
still need verification on your hardware; a successful build does not prove them.
