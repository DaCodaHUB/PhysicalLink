# PhysicalLink firmware

Run PlatformIO commands from this directory (`firmware/soil-monitor`).
The default/latest firmware is **v2-real-sensors** for ESP32-WROOM-32:
BME280 at 0x76 on SDA21/SCL22 and capacitive soil moisture on GPIO34.

| Firmware | Environment | Entry point |
| --- | --- | --- |
| Default/latest: four real sensors over v2 | `v2-real-sensors` | `examples/v2_real_sensors/main.cpp` |
| Legacy v1 regression | `esp32dev` | `src/main.cpp` |
| Synthetic v2 metadata/live stress test, sixteen channels | `v2-metadata-experimental` | `examples/v2_metadata/main.cpp` |

`platformio.ini` defines all three environments. `default_envs` selects only
`v2-real-sensors`; each environment explicitly includes exactly one application
entry point through `build_src_filter`. The shared source root does not include
other examples, tests, or legacy application sources in that environment.
Library dependencies are compiled separately by PlatformIO.

## Default/latest

```sh
pio run
pio run -t upload
pio device monitor --baud 115200
```

Explicit selection is also supported:

```sh
pio run -e v2-real-sensors
pio run -e v2-real-sensors -t upload
```

See [real-sensor wiring, identity, failure behavior, and hardware acceptance](examples/v2_real_sensors/README.md).
BLE UUIDs, sensor IDs, and the factory-eFuse-derived persistent device UUID
are unchanged by default selection. Hardware acceptance remains pending;
successful builds and host tests do not verify real BLE/sensor operation.

## Legacy regression firmware

```sh
pio run -e esp32dev
pio run -e esp32dev -t upload
```

This retains the original v1 application and its Adafruit BME280 dependency.

## Synthetic stress-test firmware

```sh
pio run -e v2-metadata-experimental
pio run -e v2-metadata-experimental -t upload
```

See the [State/Live stress-test guide](test/metadata_transport/LIVE_TRANSPORT.md)
for Serial commands, synthetic values, and BLE inspection steps.
The old explicit configuration commands remain supported:

```sh
pio run -c platformio-v2-metadata.ini -e v2-metadata-experimental
pio run -c platformio-v2-real-sensors.ini -e v2-real-sensors
```

These files now delegate to the root configuration; always include the shown
`-e` when using a compatibility configuration. Add `-t upload` to upload.
Append `--upload-port COM5` to any upload command if automatic port detection
is unsuitable (replace COM5 with the port from `pio device list`). Upload commands
are instructions only; no upload was performed for the default-selection change.

On Windows if `pio` is not on PATH, use:

```powershell
$pio = "$env:USERPROFILE/.platformio/penv/Scripts/pio.exe"
& $pio run
# Use & $pio in place of pio for the other commands above.
```

## Native validation

```sh
cmake -S test/sensor_registry -B .pio/registry-tests
cmake --build .pio/registry-tests --config Release
ctest --test-dir .pio/registry-tests -C Release --output-on-failure
git diff --check
```

Future firmware examples should become the default only after they work and
pass validation. Change `default_envs` deliberately and retain older firmware
as explicitly selectable regression environments.
