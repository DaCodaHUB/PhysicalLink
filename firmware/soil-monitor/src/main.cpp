#include <Arduino.h>
#include <Wire.h>
#include <Adafruit_BME280.h>
#include <NimBLEDevice.h>

// Hardware
constexpr int SOIL_SENSOR_PIN = 34;
constexpr int SDA_PIN = 21;
constexpr int SCL_PIN = 22;
constexpr uint8_t BME_ADDRESS = 0x76;

// Temporary V1 BLE contract
#define SERVICE_UUID          "1234"
#define SOIL_CHAR_UUID        "5678"
#define TEMPERATURE_CHAR_UUID "5679"
#define HUMIDITY_CHAR_UUID    "567A"
#define PRESSURE_CHAR_UUID    "567B"

Adafruit_BME280 bme;

NimBLECharacteristic* soilCharacteristic;
NimBLECharacteristic* temperatureCharacteristic;
NimBLECharacteristic* humidityCharacteristic;
NimBLECharacteristic* pressureCharacteristic;

void setup() {
    Serial.begin(115200);
    delay(1000);

    Serial.println("Starting PhysicalLink...");

    // -------------------------
    // BME280
    // -------------------------

    Wire.begin(SDA_PIN, SCL_PIN);

    if (!bme.begin(BME_ADDRESS, &Wire)) {
        Serial.println("Failed to initialize BME280");
        while (true) {
            delay(1000);
        }
    }

    Serial.println("BME280 initialized");

    // -------------------------
    // BLE
    // -------------------------

    NimBLEDevice::init("PhysicalLink Soil");

    NimBLEServer* server = NimBLEDevice::createServer();

    NimBLEService* service =
        server->createService(SERVICE_UUID);

    soilCharacteristic =
        service->createCharacteristic(
            SOIL_CHAR_UUID,
            NIMBLE_PROPERTY::READ |
            NIMBLE_PROPERTY::NOTIFY
        );

    temperatureCharacteristic =
        service->createCharacteristic(
            TEMPERATURE_CHAR_UUID,
            NIMBLE_PROPERTY::READ |
            NIMBLE_PROPERTY::NOTIFY
        );

    humidityCharacteristic =
        service->createCharacteristic(
            HUMIDITY_CHAR_UUID,
            NIMBLE_PROPERTY::READ |
            NIMBLE_PROPERTY::NOTIFY
        );

    pressureCharacteristic =
        service->createCharacteristic(
            PRESSURE_CHAR_UUID,
            NIMBLE_PROPERTY::READ |
            NIMBLE_PROPERTY::NOTIFY
        );

    service->start();

    NimBLEAdvertising* advertising =
        NimBLEDevice::getAdvertising();

    advertising->addServiceUUID(SERVICE_UUID);
    advertising->setName("PhysicalLink Soil");
    advertising->enableScanResponse(true);
    advertising->start();

    Serial.println("BLE advertising started");
}

void loop() {
    // -------------------------
    // Read sensors
    // -------------------------

    int soil = analogRead(SOIL_SENSOR_PIN);

    float temperature = bme.readTemperature();
    float humidity = bme.readHumidity();

    // Bosch/Adafruit gives pressure in Pa.
    // Convert to hPa for easier human inspection.
    float pressure = bme.readPressure() / 100.0F;

    // -------------------------
    // Update GATT state
    // -------------------------

    soilCharacteristic->setValue(
        std::to_string(soil)
    );

    temperatureCharacteristic->setValue(
        std::to_string(temperature)
    );

    humidityCharacteristic->setValue(
        std::to_string(humidity)
    );

    pressureCharacteristic->setValue(
        std::to_string(pressure)
    );

    // -------------------------
    // Notify subscribers
    // -------------------------

    soilCharacteristic->notify();
    temperatureCharacteristic->notify();
    humidityCharacteristic->notify();
    pressureCharacteristic->notify();

    // -------------------------
    // Serial debugging
    // -------------------------

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

    delay(1000);
}