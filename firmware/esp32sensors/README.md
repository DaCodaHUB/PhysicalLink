# PhysicalLink for ESP32

Install the library → register your sensors → upload → connect Android → see
your customizable dashboard. PhysicalLink supports 1–16 numeric sensors over
BLE. The sketch owns sensor acquisition; the library owns identity, metadata,
state synchronization, live notifications, and safe BLE shutdown.

## Use this project

From `firmware/esp32sensors`, with PlatformIO installed:

```sh
pio run
pio run -t upload
pio device monitor --baud 115200
```

The default `real-sensors` application uses ESP32-WROOM-32, BME280 at I2C 0x76
(SDA21/SCL22), and a capacitive soil probe on GPIO34. See the
[wiring and acceptance guide](examples/real_sensors/README.md).
In Android, scan for PhysicalLink, connect, then open and customize your dashboard
with Value, Gauge, Chart, and Range widgets. Device Diagnostics remains available
from the connected device screen.

| Example | Build | Upload |
| --- | --- | --- |
| Default real sensors | `pio run` | `pio run -t upload` |
| [Beginner synthetic sensors](examples/basic/main.cpp) | `pio run -e basic` | `pio run -e basic -t upload` |
| [16-sensor stress test](examples/stress_test/main.cpp) | `pio run -e stress-test` | `pio run -e stress-test -t upload` |

Each environment includes exactly one entry point. Older firmware is available
in Git history; there is one supported production implementation. Future examples
should become the default only after they work and pass validation.
Append `--upload-port COM5` if needed (replace COM5 with the port reported by
`pio device list`). No firmware was uploaded during this consolidation.

On Windows without `pio` on PATH, use:

```powershell
$pio = "$env:USERPROFILE/.platformio/penv/Scripts/pio.exe"
& $pio run
# Replace pio with & $pio in the commands above.
```

## Use PhysicalLink in your own project

The self-contained library is [lib/PhysicalLink](lib/PhysicalLink/README.md).
Copy that folder into your project's `lib/PhysicalLink` and pin its BLE dependency
in `platformio.ini`, or install a local library archive as described in its README.
No repository include paths, special build flags, sensor libraries, or Android
sources are required. The library declares its dependency in `library.json`.
It has not been published to a registry.

```cpp
#include <PhysicalLink.h>
PhysicalLink device("My Greenhouse");

void setup() {
    // Check each return value in a complete sketch (see examples/basic).
    device.addSensor("temperature", "Temperature", "C");
    device.addSensor("humidity", "Humidity", "%");
    device.begin();
}

void loop() {
    // On your own acquisition schedule:
    // device.update("temperature", readTemperature());
    // device.unavailable("humidity"); // A failed/missing measurement.
    device.poll();
}
```

Keep sensor IDs stable. The library preserves the existing factory-eFuse-derived
board UUID and changes only the session epoch on restart. Android layout keys
such as `v2:<UUID>` are deliberately unchanged; no storage migration is required.
The basic and real-sensor examples therefore share identity when flashed onto
the same board; they demonstrate alternate sensor registrations for that board,
not separately provisioned projects. Different boards have different UUIDs.
Android retains absent sensors' saved widgets and restores them if their IDs
return, while displaying only sensors in the validated manifest.

## Validate changes

```sh
cmake -S test/sensor_registry -B .pio/registry-tests
cmake --build .pio/registry-tests --config Release
ctest --test-dir .pio/registry-tests -C Release --output-on-failure
pio run
pio run -e basic -e stress-test
python test/standalone/prepare_consumer.py
pio run -d .pio/standalone-consumer
git diff --check
```

Run PlatformIO builds sequentially, especially when switching configurations.
From the repository root run Android validation with
`./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:assembleRelease`
(Windows: `gradlew.bat`).

Protocol version **2**, BLE UUIDs, binary fixtures, commands, sensor/channel
limits, and persistent identifiers are unchanged. Advanced details live in the
[transport developer guide](test/metadata_transport/LIVE_TRANSPORT.md) and
[codec test guide](test/protocol_v2/README.md).
The user reported successful real-sensor/dashboard hardware integration before
this refactor. This consolidation still needs the hardware acceptance steps in
the real-sensor guide; host tests and builds do not verify the radio or wiring.
