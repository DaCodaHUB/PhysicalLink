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