#include <Arduino.h>
#include <PhysicalLink.h>
#include <driver/adc.h>
#include "Bme280.h"

PhysicalLink dashboard("PhysicalLink real sensors");
Bme280 bme;
bool bmeReady = false;
bool adcReady = false;
uint32_t lastSample = 0;

void publish(const char* id, bool valid, double value = 0) {
    const auto result = valid ? dashboard.update(id, value) : dashboard.unavailable(id);
    if (result != PLResult::Ok) Serial.printf("Update %s failed: %d\n", id, int(result));
}

void setup() {
    Serial.begin(115200);
    Wire.begin(21, 22);
    Wire.setTimeOut(10); // Bound failed I2C transactions; no conversion wait loops.
    bmeReady = bme.begin();
    Serial.println(bmeReady ? "BME280 ready" : "BME280 unavailable (restart to retry initialization)");
    adcReady = adc1_config_width(ADC_WIDTH_BIT_12) == ESP_OK &&
        adc1_config_channel_atten(ADC1_CHANNEL_6, ADC_ATTEN_DB_12) == ESP_OK;
    if (!adcReady) Serial.println("ADC configuration failed");
    if (dashboard.addSensor("temperature", "Temperature", "C") != PLResult::Ok ||
        dashboard.addSensor("humidity", "Humidity", "%") != PLResult::Ok ||
        dashboard.addSensor("pressure", "Pressure", "hPa") != PLResult::Ok ||
        dashboard.addSensor("soil", "Soil moisture", "raw") != PLResult::Ok) {
        Serial.println("Registration failed"); return;
    }
    const auto result = dashboard.begin();
    Serial.printf("PhysicalLink startup: %d\n", int(result));
    lastSample = millis(); // Allow the first normal-mode conversion to finish.
}

void loop() {
    if (!dashboard.isRunning()) { delay(1); return; }
    if (dashboard.poll() != PLResult::Ok) {
        Serial.println("Transport failed; stopping"); dashboard.end(); return;
    }
    const uint32_t now = millis();
    if (uint32_t(now - lastSample) >= 1000) {
        lastSample = now;
        const int soil = adcReady ? adc1_get_raw(ADC1_CHANNEL_6) : -1;
        publish("soil", soil >= 0 && soil <= 4095, soil);
        double temperature = 0, humidity = 0, pressure = 0;
        const bool valid = bmeReady && bme.sample(temperature, humidity, pressure);
        publish("temperature", valid, temperature);
        publish("humidity", valid, humidity);
        publish("pressure", valid, pressure);
        Serial.printf("soil=%d; BME=%s T=%.2f C RH=%.2f %% P=%.2f hPa\n",
                      soil, valid ? "available" : "unavailable", temperature, humidity, pressure);
    }
    delay(1); // Yield only; transport retains its existing 50 ms notification budget.
}
