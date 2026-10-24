#include <Arduino.h>
#include <ExperimentalV2Metadata.h>
#include <esp_system.h>
#include <cmath>
using physical_link::SensorRegistry;
using physical_link::v2::ExperimentalMetadata;
using physical_link::v2::Status;
SensorRegistry sensors;
ExperimentalMetadata metadata;
bool paused = false, fast = false;
unsigned long lastSample = 0;
void startTransport() {
    // Test identity only: provision a distinct ID per board. NVS identity is
    // outside this experiment. Each begin uses a fresh nonzero sequence epoch.
    std::uint64_t epoch;
    do { epoch = (std::uint64_t(esp_random()) << 32) | esp_random(); } while (!epoch);
    const auto result = metadata.begin(sensors, "710abdfc-9d05-46de-a790-7fe484816eea", "Synthetic zones", epoch);
    Serial.printf("Experimental v2 startup: %d\n", static_cast<int>(result));
    lastSample = millis();
}
void setup() {
    Serial.begin(115200);
    // Synthetic temperatures only; no BME280, ADC or physical acquisition.
    // Sixteen zones exercise multi-page metadata and the 336-byte state image.
    for (int i = 0; i < 16; ++i) {
        const auto id = std::string("zone.") + std::to_string(i) + ".temperature";
        const auto name = std::string("Zone ") + std::to_string(i) + " temperature";
        if (sensors.addSensor(id.c_str(), name.c_str(), "C") != SensorRegistry::Result::Ok) {
            Serial.println("Registration failed"); return;
        }
    }
    startTransport();
    Serial.println("Synthetic readings, 1 Hz/channel; zone 15 unavailable at seconds 20..29 of each minute.");
    Serial.println("Discover by v2 service UUID. Serial: f=100 Hz stress, n=1 Hz, p/r=pause/resume poll, e=end, b=begin.");
}
void loop() {
    if (Serial.available()) {
        switch (Serial.read()) {
        case 'p': paused = true; break;
        case 'r': paused = false; break;
        case 'f': fast = true; break;
        case 'n': fast = false; break;
        case 'e': Serial.printf("Shutdown: %d\n", static_cast<int>(metadata.end())); break;
        case 'b': if (!metadata.isRunning()) startTransport(); break;
        }
    }
    const auto now = millis();
    if (metadata.isRunning() && now - lastSample >= (fast ? 10UL : 1000UL)) {
        lastSample = now;
        for (int i = 0; i < 16; ++i) {
            const auto id = std::string("zone.") + std::to_string(i) + ".temperature";
            const bool failed = i == 15 && (now / 1000) % 60 >= 20 && (now / 1000) % 60 < 30;
            const auto result = failed ? metadata.unavailable(id.c_str())
                : metadata.update(id.c_str(), 20.0 + 0.3 * i + 2.0 * std::sin(now / 12500.0 + i));
            if (result != Status::Ok) Serial.printf("Synthetic update failed: %d\n", static_cast<int>(result));
        }
    }
    if (!paused && metadata.isRunning() && metadata.poll() != ExperimentalMetadata::Result::Ok) {
        Serial.println("Transport error; stopping");
        metadata.end();
    }
    delay(1); // Maker scheduling only; the library never delays for radio work.
}
