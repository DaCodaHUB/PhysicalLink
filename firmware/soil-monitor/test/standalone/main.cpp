#include <Arduino.h>
#include <PhysicalLink.h>
PhysicalLink device("Independent consumer");
void setup() {
    Serial.begin(115200);
    if (device.addSensor("custom.wind", "Wind", "m/s") != PLResult::Ok ||
        device.addSensor("custom.light", "Light", "lx") != PLResult::Ok) return;
    Serial.printf("Startup: %d\n", int(device.begin()));
}
void loop() {
    if (!device.isRunning()) return;
    static uint32_t previous = 0;
    const uint32_t now = millis();
    if (uint32_t(now - previous) >= 1000) {
        previous = now;
        device.update("custom.wind", 3.5); // Explicit synthetic test value.
        device.unavailable("custom.light");
    }
    device.poll();
}
