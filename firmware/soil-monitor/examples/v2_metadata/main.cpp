#include <Arduino.h>
#include <ExperimentalV2Metadata.h>
using physical_link::SensorRegistry;
using physical_link::v2::ExperimentalMetadata;
SensorRegistry sensors;
ExperimentalMetadata metadata;
void setup() {
    Serial.begin(115200);
    // Deterministic multi-page test data, not acquired telemetry. Provision a
    // distinct ID on each test board; this sketch does not implement NVS identity.
    for (int i = 0; i < 16; ++i) {
        const auto id = std::string("sensor.") + std::to_string(i);
        const auto name = std::string("Sensor ") + std::to_string(i);
        if (sensors.addSensor(id.c_str(), name.c_str(), "raw") != SensorRegistry::Result::Ok) {
            Serial.println("Registration failed"); return;
        }
    }
    const auto result = metadata.begin(sensors, "710abdfc-9d05-46de-a790-7fe484816eea",
                                       "Garden", UINT64_C(0xb398ca802d871ae0));
    Serial.printf("Experimental metadata startup: %d\n", static_cast<int>(result));
    Serial.println("Use service UUID discovery; no state/live characteristic exists.");
}
void loop() {
    static bool paused = false;
    if (Serial.available()) {
        const auto command = Serial.read();
        if (command == 'p') paused = true;
        if (command == 'r') paused = false;
        if (command == 'e') Serial.printf("Shutdown: %d\n", static_cast<int>(metadata.end()));
    }
    if (!paused && metadata.isRunning()) {
        const auto result = metadata.poll();
        if (result != ExperimentalMetadata::Result::Ok) {
            Serial.printf("Experimental transport error: %d\n", static_cast<int>(result));
            metadata.end();
        }
    }
    delay(1); // Maker scheduling only; the library poll never delays.
}
