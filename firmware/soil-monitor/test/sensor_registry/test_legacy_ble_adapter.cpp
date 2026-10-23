#include "LegacyBleAdapter.h"
#include <cstdio>
#include <cstring>
#include <limits>
#include <string>

using physical_link::SensorRegistry;
using physical_link::LegacyBleAdapter;
using Status = LegacyBleAdapter::Status;
using Result = SensorRegistry::Result;
#define CHECK(x) do { if (!(x)) { std::fprintf(stderr, "%d: %s\n", __LINE__, #x); return 1; } } while (false)

int main() {
    SensorRegistry registry;
    char output[513] = "unchanged";
    CHECK(LegacyBleAdapter::serialize(registry, output, sizeof(output)).status == Status::InvalidConfiguration);
    CHECK(registry.addSensor("soil", "Soil", "raw") == Result::Ok);
    CHECK(registry.addSensor("temperature", "Temperature", "C") == Result::Ok);
    CHECK(registry.addSensor("humidity", "Humidity", "%") == Result::Ok);
    CHECK(registry.addSensor("pressure", "Pressure", "hPa") == Result::Ok);
    CHECK(registry.addSensor("soil", "Duplicate", "raw") == Result::DuplicateId);
    const std::string expected = R"({"version":1,"device":"PhysicalLink Soil","capabilities":[{"id":"soil","uuid":"5678","unit":"raw","type":"number"},{"id":"temperature","uuid":"5679","unit":"C","type":"number"},{"id":"humidity","uuid":"567a","unit":"%","type":"number"},{"id":"pressure","uuid":"567b","unit":"hPa","type":"number"}]})";
    const auto result = LegacyBleAdapter::serialize(registry, output, sizeof(output));
    CHECK(result.status == Status::Ok);
    CHECK(result.bytesWritten == 298 && result.requiredCapacity == 299);
    CHECK(output == expected);
    for (std::size_t capacity = 0; capacity < 299; ++capacity) {
        std::memset(output, 'x', sizeof(output));
        const auto small = LegacyBleAdapter::serialize(registry, output, capacity);
        CHECK(small.status == Status::BufferTooSmall && small.bytesWritten == 0);
        CHECK(small.requiredCapacity == 299);
        for (char c : output) CHECK(c == 'x');
    }
    CHECK(LegacyBleAdapter::serialize(registry, output, 299).status == Status::Ok);
    CHECK(LegacyBleAdapter::serialize(registry, nullptr, 0).status == Status::BufferTooSmall);
    CHECK(LegacyBleAdapter::serialize(registry, nullptr, 1).status == Status::InvalidBuffer);
    CHECK(LegacyBleAdapter::metadataFits(512));
    CHECK(!LegacyBleAdapter::metadataFits(513));
    CHECK(!LegacyBleAdapter::metadataFits(std::numeric_limits<std::size_t>::max()));
    CHECK(LegacyBleAdapter::uuidFor(nullptr) == nullptr);
    CHECK(LegacyBleAdapter::uuidFor("temperature.indoor") == nullptr);

    SensorRegistry reverse;
    CHECK(reverse.addSensor("pressure", "Ignored name", "hPa") == Result::Ok);
    CHECK(reverse.addSensor("humidity", nullptr, "%") == Result::Ok);
    CHECK(reverse.addSensor("temperature", nullptr, "C") == Result::Ok);
    CHECK(reverse.addSensor("soil", nullptr, "raw") == Result::Ok);
    CHECK(LegacyBleAdapter::serialize(reverse, output, sizeof(output)).status == Status::Ok);
    const std::string reversed(output);
    CHECK(reversed.find("pressure") < reversed.find("humidity"));
    CHECK(reversed.find("humidity") < reversed.find("temperature"));
    CHECK(reversed.find("temperature") < reversed.find("soil"));

    SensorRegistry wrong;
    CHECK(wrong.addSensor("temperature", nullptr, "F") == Result::Ok);
    CHECK(LegacyBleAdapter::serialize(wrong, output, sizeof(output)).status == Status::IncompatibleUnit);
    CHECK(registry.addSensor("light", nullptr, "lux") == Result::Ok);
    std::memcpy(output, "unchanged", sizeof("unchanged"));
    CHECK(LegacyBleAdapter::serialize(registry, output, sizeof(output)).status == Status::UnsupportedId);
    CHECK(std::strcmp(output, "unchanged") == 0);

    std::string reading = "old";
    CHECK(LegacyBleAdapter::formatReading(reverse, "soil", reading) == Status::Unavailable && reading.empty());
    CHECK(reverse.update("soil", 2048) == Result::Ok);
    CHECK(LegacyBleAdapter::formatReading(reverse, "soil", reading) == Status::Ok && reading == "2048");
    CHECK(reverse.update("soil", 0) == Result::Ok);
    CHECK(LegacyBleAdapter::formatReading(reverse, "soil", reading) == Status::Ok && reading == "0");
    CHECK(reverse.update("soil", 1.5) == Result::Ok);
    CHECK(LegacyBleAdapter::formatReading(reverse, "soil", reading) == Status::InvalidReading && reading.empty());
    CHECK(reverse.update("soil", 4096) == Result::Ok);
    CHECK(LegacyBleAdapter::formatReading(reverse, "soil", reading) == Status::InvalidReading);
    const char* ids[] = {"temperature", "humidity", "pressure"};
    const double values[] = {-12.5, 45.25, 1013.125};
    const char* texts[] = {"-12.500000", "45.250000", "1013.125000"};
    for (int i = 0; i < 3; ++i) {
        CHECK(reverse.update(ids[i], values[i]) == Result::Ok);
        CHECK(LegacyBleAdapter::formatReading(reverse, ids[i], reading) == Status::Ok && reading == texts[i]);
        CHECK(reverse.update(ids[i], std::numeric_limits<double>::quiet_NaN()) == Result::InvalidValue);
        CHECK(reverse.unavailable(ids[i]) == Result::Ok); // Firmware's failure path.
        CHECK(LegacyBleAdapter::formatReading(reverse, ids[i], reading) == Status::Unavailable && reading.empty());
        CHECK(reverse.getSensor(ids[i])->value == values[i]);
        CHECK(reverse.update(ids[i], values[i]) == Result::Ok);
        CHECK(LegacyBleAdapter::formatReading(reverse, ids[i], reading) == Status::Ok && reading == texts[i]);
    }
    CHECK(LegacyBleAdapter::formatReading(wrong, "temperature", reading) == Status::IncompatibleUnit);
    CHECK(LegacyBleAdapter::formatReading(wrong, "soil", reading) == Status::InvalidConfiguration);
    CHECK(LegacyBleAdapter::formatReading(reverse, "light", reading) == Status::UnsupportedId);
    std::puts(expected.c_str());
    return 0;
}
