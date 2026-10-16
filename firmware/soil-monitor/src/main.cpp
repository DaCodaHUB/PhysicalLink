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
#define CAPABILITIES_UUID     "5670"

Adafruit_BME280 bme;

NimBLECharacteristic* soilCharacteristic;
NimBLECharacteristic* temperatureCharacteristic;
NimBLECharacteristic* humidityCharacteristic;
NimBLECharacteristic* pressureCharacteristic;
NimBLEServer* server = nullptr;

static constexpr char CAPABILITIES_JSON[] = R"json({"version":1,"device":"PhysicalLink Soil","capabilities":[{"id":"soil","uuid":"5678","unit":"raw","type":"number"},{"id":"temperature","uuid":"5679","unit":"C","type":"number"},{"id":"humidity","uuid":"567a","unit":"%","type":"number"},{"id":"pressure","uuid":"567b","unit":"hPa","type":"number"}]})json";

class ServerCallbacks : public NimBLEServerCallbacks {
    void onConnect(
        NimBLEServer* server,
        NimBLEConnInfo& connInfo
    ) override {
        Serial.printf(
            "BLE connected, handle: %d\n",
            connInfo.getConnHandle()
        );
    }

    void onDisconnect(
        NimBLEServer* server,
        NimBLEConnInfo& connInfo,
        int reason
    ) override {
        Serial.printf(
            "BLE disconnected, handle: %d, reason: %d\n",
            connInfo.getConnHandle(),
            reason
        );

        NimBLEDevice::startAdvertising();
    }
};

ServerCallbacks serverCallbacks;

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

    server = NimBLEDevice::createServer();
    server->setCallbacks(&serverCallbacks);

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

    NimBLECharacteristic* capabilitiesCharacteristic =
    service->createCharacteristic(
        CAPABILITIES_UUID,
        NIMBLE_PROPERTY::READ,
        sizeof(CAPABILITIES_JSON)
    );

    capabilitiesCharacteristic->setValue(
        CAPABILITIES_JSON
    );

    Serial.print("JSON length: ");
    Serial.println(strlen(CAPABILITIES_JSON));

    Serial.print("Stored value length: ");
    Serial.println(capabilitiesCharacteristic->getValue().length());

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
    static unsigned long lastDebug = 0;

    if (millis() - lastDebug >= 5000) {
        lastDebug = millis();

        Serial.printf(
            "BLE connected clients: %d\n",
            server->getConnectedCount()
        );

        static bool disconnectRequested = false;
    }

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