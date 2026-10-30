# Real sensor dashboard

This is the default PhysicalLink application for ESP32-WROOM-32. It uses only
the public `PhysicalLink.h` API for registration, startup, updates, unavailable
readings, and polling. Sensor acquisition is owned by the sketch.

| Channel | Stable ID | Unit | Wiring |
| --- | --- | --- | --- |
| 0 | temperature | C (degrees Celsius) | BME280 0x76, SDA GPIO21/SCL GPIO22 |
| 1 | humidity | % | Same BME280 |
| 2 | pressure | hPa | Same BME280; Pa converted to hPa |
| 3 | soil | raw | Capacitive probe output to GPIO34, ADC1 channel 6 |

Use common ground and 3.3 V compatible power/signals. Soil is 12-bit raw ADC
(0..4095), not calibrated moisture percentage. An unplugged analog probe can
float to a valid ADC value; GPIO34 alone cannot reliably detect disconnection.
ADC configuration/read failures are unavailable. Rail values are not invented
fault indicators.

The BME280 helper checks calibration and burst-read transfer results and uses
normal mode, x1 oversampling, and one-second standby. It follows Bosch's
floating-point compensation algorithm. Missing/wrong chip, short/failed transfer,
reset/sleep, disabled raw channel, invalid calibration, nonfinite or out-of-range
temperature/pressure marks all environmental channels unavailable. Subsequent
successful reads recover after transient I2C failures. Initialization failure or
a reset/power loss of the BME280 requires an ESP32 restart. Soil remains independent.

Acquisition runs once per second without conversion wait/retry loops. The first
second allows conversion. I2C transfers are synchronous with a 10 ms timeout;
they run on the application task, never in BLE callbacks. The existing BLE send
budget, state synchronization, and coalescing behavior are unchanged.

## Build and connect

From `firmware/soil-monitor`:

```sh
pio run
pio run -t upload
pio device monitor --baud 115200
```

In Android scan/connect to PhysicalLink, then open your dashboard. Four channels
should appear automatically. Value, Gauge, Chart, and Range widgets are retained.
For other examples or independent installation see the [firmware README](../../README.md).
`PhysicalLink startup: 0` on Serial means startup succeeded.

The factory-eFuse board UUID, channel order, sensor IDs, units, and manifest name
match the working real-sensor firmware before consolidation. Only session epoch
changes on restart. Android keeps its `v2:<UUID>` layout identity and migrations.

## Hardware regression checklist

The user reported real-sensor/dashboard verification before this refactor. No
upload or physical hardware verification was performed during consolidation.

1. Flash only when requested. Confirm plausible Serial readings and four
   automatically discovered measurements in Android; compare to reference inputs.
2. Verify live updates, chart history/freshness, and Value/Gauge/Chart/Range widgets.
   Customize/save the layout; restart ESP32 and app, reconnect, and verify the same
   saved widget bindings and device UUID with a new session epoch.
3. Boot without BME280: BLE and soil continue, three environmental channels are
   unavailable. Reconnect BME280 and restart to initialize. Interrupt I2C after
   successful readings: the next acquisition must mark unavailable, not repeat
   previous values as current. Restore wiring and verify recovery.
4. Disconnect/reconnect repeatedly, checking advertising and state synchronization.
   Device Diagnostics should remain accessible with correct ages/sequences.
5. For independent BLE inspection see the [developer guide](../../test/metadata_transport/LIVE_TRANSPORT.md).
   This example has a 96-byte state image and four 20-byte records. Check initial
   unavailable state, subscribed live records, capture request 1, and unavailable
   status during acquisition failure. RF spacing needs hardware instrumentation.
