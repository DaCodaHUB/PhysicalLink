# PhysicalLink Sprint 5D: real sensors over v2

Default/latest firmware for ESP32-WROOM-32. The legacy v1 sketch, library
transport/codecs, Android application, and sixteen-channel synthetic example
are unchanged. `PhysicalLink.h` currently supports only v1, so this sketch uses
the existing public experimental `ExperimentalV2Metadata.h` facade and
`SensorRegistry.h`; it does not access transport internals or write BLE packets.

## Wiring and channels

Use a common ground and 3.3 V compatible sensor power/signals.

| Channel | Stable ID | Unit | Connection |
| --- | --- | --- | --- |
| 0 | temperature | C (degrees Celsius) | BME280 at 0x76, SDA21/SCL22 |
| 1 | humidity | % | same BME280 |
| 2 | pressure | hPa | same BME280, Pa divided by 100 |
| 3 | soil | raw | capacitive probe analog output to GPIO34 (ADC1 channel 6) |

Soil is a 12-bit raw ADC reading, 0..4095, with no moisture-percentage
calibration. An unplugged analog probe can float to a valid ADC code: software
cannot reliably identify physical disconnection from this single input.
ADC setup/read errors are unavailable. Reliable probe-presence detection would
require additional hardware; rail values alone are not treated as failures.

The board device UUID uses a fixed namespace prefix and its factory eFuse MAC.
The four IDs and channel order are fixed across boots. No random/NVS identity is
created. Only the session epoch changes on restart. Keep this UUID mapping and
sensor IDs stable when updating firmware to retain Android layout associations.
The advertised GAP name remains **PhysicalLink V2**; discover by service UUID
`b89e2000-7c42-4f9d-9d25-8a639eab5120`.

## Acquisition and failure behavior

The example-local BME280 helper uses checked I2C calibration and burst reads,
normal mode with x1 temperature/pressure/humidity oversampling and 1 s standby.
Its floating-point compensation follows the
[Bosch BME280 reference algorithm](https://github.com/boschsensortec/BME280_SensorAPI/blob/master/bme280.c).
This avoids the installed Adafruit driver's unchecked register-read return
values, which cannot reliably distinguish a short transfer from a numeric sample.
There are no sensor-driver changes in PhysicalLink itself.

Sampling runs once per second using wrap-safe millis subtraction. The initial
one-second interval permits conversion before the first read. Acquisition and
Serial logging run on the application task, never BLE callbacks. There are no
conversion waits, retry loops, or catch-up bursts. I2C calls are synchronous with
a 10 ms transaction timeout; this is bounded acquisition, not hard realtime.
`poll()` runs each loop, and the existing host-task scheduler still attempts at
most one 20-byte notification per 50 ms (20/s aggregate), coalescing latest values.

All four channels begin unavailable. Missing/wrong BME280, invalid calibration,
failed/short I2C transfers, reset/sleep configuration, disabled-channel raw
sentinels, nonfinite results, or results outside BME280 temperature/pressure
operating ranges mark all three environmental channels unavailable. No previous
sample is sent as current; v2 encodes unavailable status with zero value bits.
Subsequent successful reads recover after transient bus failures. Initialization
failure or a sensor reset requires an ESP32 restart to initialize again. Soil
continues independently. A transport poll failure stops BLE and is logged.

## Build, upload, and serial

Run from `firmware/soil-monitor`. PowerShell commands using this machine's
installed PlatformIO (replace `COM5` with the port from `device list`):

```powershell
$pio = "$env:USERPROFILE/.platformio/penv/Scripts/pio.exe"
& $pio device list
& $pio run
& $pio run -t upload --upload-port COM5
& $pio device monitor --port COM5 --baud 115200
```

Close the monitor before another upload. Serial prints the stable device UUID,
startup result (`0` is success), BME initialization result, and acquired values.
No upload or physical sensor/BLE/Android acceptance was performed for this change.

See the [firmware README](../../README.md) for explicit legacy v1 and synthetic
v2 build/upload commands. The compatibility configuration with
`-c platformio-v2-real-sensors.ini -e v2-real-sensors` remains supported.

## Automated validation

```powershell
cmake -S test/sensor_registry -B .pio/registry-tests
cmake --build .pio/registry-tests --config Release
ctest --test-dir .pio/registry-tests -C Release --output-on-failure
& $pio run -e esp32dev
& $pio run -c platformio-v2-metadata.ini -e v2-metadata-experimental
& $pio run -c platformio-v2-real-sensors.ini -e v2-real-sensors
```

60/60 native tests passed, including the original 59 regressions and a checked
BME280 suite: reference temperature/pressure, independent humidity oracle,
missing chip, bad calibration, bus/short-read failures, recovery, sensor reset,
and disabled-channel sentinels. The fake Wire port exercises production helper
code, not electrical behavior. All three ESP32 firmware builds passed.

## Hardware acceptance checklist (pending)

1. Flash with sensors connected. Confirm startup success and plausible readings
   on Serial; compare against a reference instrument. Change soil probe input
   and warm/breathe near the BME280 to observe real changes.
2. In the current v2-capable Android app, scan/connect and verify four different
   measurements appear automatically with the IDs/units above. Confirm live
   updates, charts accumulating samples, and gauges tracking the values. Add
   chart/gauge widgets, save a layout, restart the ESP32, and reconnect. Verify
   UUID/IDs are unchanged, epoch changes, and saved widget bindings survive.
3. Boot with BME280 disconnected: BLE and soil must work, with all three
   environmental measurements unavailable. Restore wiring and restart to recover.
   Interrupt I2C after good readings: the next acquisition must report unavailable,
   never repeat an old value as a successful new sample. Restore I2C and check
   recovery (restart if the sensor lost power/reset).
4. Disconnect/reconnect Android repeatedly, confirming advertising resumes and
   fresh state/live values synchronize. Test restarting both the app and ESP32.
5. For independent BLE inspection, use the existing
   [State/Live procedure](../../test/metadata_transport/LIVE_TRANSPORT.md).
   This example has four metadata entries and a **96-byte** state image, not
   sixteen entries/336 bytes. Subscribe to `b89e2003-7c42-4f9d-9d25-8a639eab5120`,
   write `02 01 00` to Control after subscribing, and read until capture ID 1.
   Check four ordered records and 20-byte live notifications; failures use status
   `01`, age `ffffffff`, zero value bits. Initial request-0 state is unavailable.
   Notification limits remain unchanged; RF spacing requires instrumentation.

These are acceptance steps, not claims that charts, discovery, or persistence
have been verified on physical hardware.
