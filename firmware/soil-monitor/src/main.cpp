#include <Arduino.h>
#include <Wire.h>
#include <Adafruit_BME280.h>
#include <PhysicalLink.h>
#include <limits>

PhysicalLink dashboard("PhysicalLink Soil");
bool bmeAvailable = false;

// Hardware
constexpr int SOIL_SENSOR_PIN = 34;
constexpr int SDA_PIN = 21;
constexpr int SCL_PIN = 22;
constexpr uint8_t BME_ADDRESS = 0x76;

Adafruit_BME280 bme;

void updateReading(const char* id, double value) {
    const auto result = dashboard.update(id, value);
    if (result != PLResult::Ok) {
        Serial.printf("Reading rejected for %s: %d\n", id, static_cast<int>(result));
        // InvalidValue already marks the channel unavailable in the public API.
    }
}

void setup() {
    Serial.begin(115200);
    delay(1000);

    Serial.println("Starting PhysicalLink...");

    // -------------------------
    // BME280
    // -------------------------

    Wire.begin(SDA_PIN, SCL_PIN);

    bmeAvailable = bme.begin(BME_ADDRESS, &Wire);
    Serial.println(bmeAvailable ? "BME280 initialized" : "Failed to initialize BME280");

    if (dashboard.addSensor("soil", "Soil", "raw") != PLResult::Ok ||
        dashboard.addSensor("temperature", "Temperature", "C") != PLResult::Ok ||
        dashboard.addSensor("humidity", "Humidity", "%") != PLResult::Ok ||
        dashboard.addSensor("pressure", "Pressure", "hPa") != PLResult::Ok) {
        Serial.println("Sensor registration failed");
        return;
    }
    const auto result = dashboard.begin();
    if (result != PLResult::Ok) {
        Serial.printf("PhysicalLink startup failed: %d\n", static_cast<int>(result));
        return;
    }
    Serial.println("BLE advertising started");
}

void loop() {
    if (!dashboard.isRunning()) return;
    const auto pollResult = dashboard.poll();
    if (pollResult != PLResult::Ok) Serial.println("BLE publication pending retry");
    static unsigned long lastSample = millis() - 1000;
    const unsigned long now = millis();
    if (now - lastSample < 1000) return;
    lastSample = now;
    // -------------------------
    // Read sensors
    // -------------------------

    int soil = analogRead(SOIL_SENSOR_PIN);

    float temperature = bmeAvailable ? bme.readTemperature() : std::numeric_limits<float>::quiet_NaN();
    float humidity = bmeAvailable ? bme.readHumidity() : std::numeric_limits<float>::quiet_NaN();

    // Bosch/Adafruit gives pressure in Pa.
    // Convert to hPa for easier human inspection.
    float pressure = bmeAvailable ? bme.readPressure() / 100.0F : std::numeric_limits<float>::quiet_NaN();

    // -------------------------
    // Update GATT state
    // -------------------------

    updateReading("soil", soil);
    updateReading("temperature", temperature);
    updateReading("humidity", humidity);
    updateReading("pressure", pressure);

    if (dashboard.poll() != PLResult::Ok) Serial.println("BLE publication pending retry");

    Serial.println("--- PhysicalLink ---");

    Serial.print("Soil: ");
    Serial.println(soil);

    Serial.print("Temperature: ");
    Serial.print(temperature);
    Serial.println(" C");

    Serial.print("Humidity: ");
    Serial.print(humidity);
    Serial.println(" %");

    Serial.print("Pressure: ");
    Serial.print(pressure);
    Serial.println(" hPa");

    Serial.println();

}
