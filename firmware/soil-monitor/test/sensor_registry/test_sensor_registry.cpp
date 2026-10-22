#include "SensorRegistry.h"

#include <cstdio>
#include <cstring>
#include <limits>
#include <string>
#include <type_traits>

using physical_link::SensorRegistry;
using Result = SensorRegistry::Result;

// Unlike assert(), checks remain active in release builds.
#define CHECK(expression) do { \
    if (!(expression)) { \
        std::fprintf(stderr, "%s:%d: %s\n", __FILE__, __LINE__, #expression); \
        return false; \
    } \
} while (false)

static_assert(std::is_same<decltype(SensorRegistry().getSensor("x")),
                           const SensorRegistry::Sensor*>::value,
              "Lookup must not expose mutable registry storage");
static_assert(!std::is_move_constructible<SensorRegistry>::value,
              "Moving the owner would invalidate borrowed views");

bool registration() {
    SensorRegistry registry;
    CHECK(registry.size() == 0);
    CHECK(registry.getSensor("temperature") == nullptr);
    CHECK(registry.addSensor("temperature", "Temperature", "C") == Result::Ok);
    const auto* sensor = registry.getSensor("temperature");
    CHECK(sensor != nullptr);
    CHECK(std::strcmp(sensor->id, "temperature") == 0);
    CHECK(std::strcmp(sensor->name, "Temperature") == 0);
    CHECK(std::strcmp(sensor->unit, "C") == 0);
    CHECK(!sensor->available);
    CHECK(sensor->value == 0.0);
    CHECK(registry.size() == 1);
    return true;
}

bool metadata_ownership() {
    SensorRegistry registry;
    {
        char id[] = "temperature";
        char name[] = "Temperature";
        char unit[] = "C";
        CHECK(registry.addSensor(id, name, unit) == Result::Ok);
        id[0] = 'x';
        name[0] = 'X';
        unit[0] = 'F';
    }
    const auto* sensor = registry.getSensor("temperature");
    CHECK(sensor != nullptr);
    CHECK(std::strcmp(sensor->name, "Temperature") == 0);
    CHECK(std::strcmp(sensor->unit, "C") == 0);
    CHECK(registry.getSensor("xemperature") == nullptr);
    return true;
}

bool optional_metadata() {
    SensorRegistry registry;
    CHECK(registry.addSensor("soil", nullptr, nullptr) == Result::Ok);
    CHECK(std::strcmp(registry.getSensor("soil")->name, "soil") == 0);
    CHECK(std::strcmp(registry.getSensor("soil")->unit, "") == 0);
    CHECK(registry.addSensor("pressure", "", "") == Result::Ok);
    CHECK(std::strcmp(registry.getSensor("pressure")->name, "pressure") == 0);
    return true;
}

bool duplicate_ids() {
    SensorRegistry registry;
    CHECK(registry.addSensor("soil", "Soil", "raw") == Result::Ok);
    CHECK(registry.update("soil", 42) == Result::Ok);
    const auto* sensor = registry.getSensor("soil");
    CHECK(registry.addSensor("soil", "Changed", "percent") == Result::DuplicateId);
    CHECK(registry.size() == 1);
    CHECK(registry.getSensor("soil") == sensor);
    CHECK(std::strcmp(sensor->name, "Soil") == 0);
    CHECK(std::strcmp(sensor->unit, "raw") == 0);
    CHECK(sensor->value == 42 && sensor->available);
    return true;
}

bool invalid_ids() {
    SensorRegistry registry;
    const char* invalid[] = {nullptr, "", "Soil", "1soil", ".soil", " soil",
                             "soil ", "soil/probe", "soil\n", "soil:1", "\xc3\xa9"};
    for (const auto* id : invalid) {
        CHECK(registry.addSensor(id, "Sensor", "C") == Result::InvalidId);
        CHECK(registry.update(id, 1) == Result::InvalidId);
        CHECK(registry.unavailable(id) == Result::InvalidId);
        CHECK(registry.getSensor(id) == nullptr);
        CHECK(registry.size() == 0);
    }
    // Full buffers without a terminator must be rejected with bounded scans.
    char tooLong[SensorRegistry::MaxIdLength + 1];
    std::memset(tooLong, 'a', sizeof(tooLong));
    CHECK(registry.addSensor(tooLong, "Sensor", "C") == Result::InvalidId);
    CHECK(registry.update(tooLong, 1) == Result::InvalidId);
    CHECK(registry.unavailable(tooLong) == Result::InvalidId);
    CHECK(registry.getSensor(tooLong) == nullptr);
    CHECK(registry.addSensor("t.indoor_2-a", "Sensor", "C") == Result::Ok);
    CHECK(registry.addSensor("t", "Sensor", "C") == Result::Ok);
    return true;
}

bool string_boundaries() {
    SensorRegistry registry;
    const std::string id(SensorRegistry::MaxIdLength, 'a');
    const std::string name(SensorRegistry::MaxNameLength, 'n');
    const std::string unit(SensorRegistry::MaxUnitLength, 'u');
    CHECK(registry.addSensor(id.c_str(), name.c_str(), unit.c_str()) == Result::Ok);
    const auto* sensor = registry.getSensor(id.c_str());
    CHECK(std::strcmp(sensor->id, id.c_str()) == 0);
    CHECK(std::strcmp(sensor->name, name.c_str()) == 0);
    CHECK(std::strcmp(sensor->unit, unit.c_str()) == 0);
    CHECK(registry.addSensor((id + "a").c_str(), "N", "U") == Result::InvalidId);
    CHECK(registry.addSensor("name", (name + "n").c_str(), "U") == Result::InvalidName);
    CHECK(registry.addSensor("unit", "N", (unit + "u").c_str()) == Result::InvalidUnit);
    char badName[SensorRegistry::MaxNameLength + 1];
    char badUnit[SensorRegistry::MaxUnitLength + 1];
    std::memset(badName, 'n', sizeof(badName));
    std::memset(badUnit, 'u', sizeof(badUnit));
    CHECK(registry.addSensor("name", badName, "U") == Result::InvalidName);
    CHECK(registry.addSensor("unit", "N", badUnit) == Result::InvalidUnit);
    CHECK(registry.size() == 1);
    CHECK(registry.getSensor("name") == nullptr);
    CHECK(registry.getSensor("unit") == nullptr);
    // Rejected inserts must not consume slots or poison a later valid insert.
    CHECK(registry.addSensor("name", "N", "U") == Result::Ok);
    CHECK(!registry.getSensor("name")->available);
    return true;
}

bool utf8_metadata() {
    SensorRegistry registry;
    CHECK(registry.addSensor("temperature", "Temp\xc3\xa9rature", "\xc2\xb0" "C") == Result::Ok);
    CHECK(std::strcmp(registry.getSensor("temperature")->unit, "\xc2\xb0" "C") == 0);
    CHECK(registry.addSensor("unicode", "\xe2\x82\xac\xf0\x9f\x8c\xa1", "") == Result::Ok);
    const char* malformed[] = {"\x80", "\xc0\xaf", "\xc2", "\xe0\x80\x80",
        "\xed\xa0\x80", "\xf0\x80\x80\x80", "\xf4\x90\x80\x80", "\xf5\x80\x80\x80",
        "\xe2\x28\xa1", "\xff"};
    for (const auto* text : malformed) {
        CHECK(registry.addSensor("bad", text, "C") == Result::InvalidName);
        CHECK(registry.addSensor("bad", "Name", text) == Result::InvalidUnit);
    }
    std::string exact;
    for (std::size_t i = 0; i < SensorRegistry::MaxNameLength / 2; ++i) exact += "\xc3\xa9";
    CHECK(registry.addSensor("boundary", exact.c_str(), "") == Result::Ok);
    CHECK(registry.addSensor("overlong", (exact + "\xc3\xa9").c_str(), "") == Result::InvalidName);
    CHECK(registry.size() == 3);
    return true;
}

bool updates() {
    SensorRegistry registry;
    CHECK(registry.addSensor("temperature", "Temperature", "C") == Result::Ok);
    const auto* sensor = registry.getSensor("temperature");
    const double values[] = {23.5, -40.125, 0.0, -0.0, 42.0,
        std::numeric_limits<double>::max(), std::numeric_limits<double>::lowest(),
        std::numeric_limits<double>::min(), std::numeric_limits<double>::denorm_min()};
    for (double value : values) {
        CHECK(registry.update("temperature", value) == Result::Ok);
        CHECK(sensor->available);
        CHECK(sensor->value == value);
    }
    CHECK(registry.size() == 1);
    CHECK(std::strcmp(sensor->unit, "C") == 0);
    return true;
}

bool invalid_values() {
    SensorRegistry registry;
    CHECK(registry.addSensor("soil", "Soil", "raw") == Result::Ok);
    const auto* sensor = registry.getSensor("soil");
    const double invalid[] = {std::numeric_limits<double>::quiet_NaN(),
        std::numeric_limits<double>::infinity(), -std::numeric_limits<double>::infinity()};
    for (double value : invalid) {
        CHECK(registry.update("soil", value) == Result::InvalidValue);
        CHECK(!sensor->available && sensor->value == 0.0);
    }
    CHECK(registry.update("soil", 123.5) == Result::Ok);
    for (double value : invalid) {
        CHECK(registry.update("soil", value) == Result::InvalidValue);
        CHECK(sensor->available && sensor->value == 123.5);
    }
    CHECK(registry.unavailable("soil") == Result::Ok);
    CHECK(registry.update("soil", invalid[0]) == Result::InvalidValue);
    CHECK(!sensor->available && sensor->value == 123.5);
    CHECK(registry.update("soil", 200) == Result::Ok);
    CHECK(sensor->available && sensor->value == 200);
    return true;
}

bool availability() {
    SensorRegistry registry;
    CHECK(registry.addSensor("soil", "Soil", "raw") == Result::Ok);
    const auto* sensor = registry.getSensor("soil");
    CHECK(registry.unavailable("soil") == Result::Ok);
    CHECK(!sensor->available);
    CHECK(registry.update("soil", 0) == Result::Ok);
    CHECK(sensor->available); // zero is a real reading, not a missing sentinel
    CHECK(registry.update("soil", 321) == Result::Ok);
    CHECK(registry.unavailable("soil") == Result::Ok);
    CHECK(!sensor->available && sensor->value == 321);
    CHECK(registry.unavailable("soil") == Result::Ok);
    CHECK(!sensor->available && sensor->value == 321);
    CHECK(registry.update("soil", 456) == Result::Ok);
    CHECK(sensor->available && sensor->value == 456);
    return true;
}

bool unknown_sensors() {
    SensorRegistry registry;
    CHECK(registry.addSensor("soil", "Soil", "raw") == Result::Ok);
    CHECK(registry.update("soil", 12) == Result::Ok);
    CHECK(registry.update("missing", 4) == Result::UnknownSensor);
    CHECK(registry.unavailable("missing") == Result::UnknownSensor);
    CHECK(registry.getSensor("missing") == nullptr);
    CHECK(registry.size() == 1);
    CHECK(registry.getSensor("soil")->available);
    CHECK(registry.getSensor("soil")->value == 12);
    return true;
}

bool sensor_limit() {
    SensorRegistry registry;
    for (std::size_t i = 0; i < SensorRegistry::MaxSensors; ++i) {
        const std::string id = "sensor." + std::to_string(i);
        CHECK(registry.addSensor(id.c_str(), "Sensor", "C") == Result::Ok);
        CHECK(registry.update(id.c_str(), static_cast<double>(i)) == Result::Ok);
    }
    CHECK(registry.size() == SensorRegistry::MaxSensors);
    CHECK(registry.addSensor("extra", "Extra", "C") == Result::CapacityExceeded);
    CHECK(registry.addSensor("sensor.0", "Changed", "F") == Result::DuplicateId);
    CHECK(registry.getSensor("extra") == nullptr);
    for (std::size_t i = 0; i < SensorRegistry::MaxSensors; ++i) {
        const std::string id = "sensor." + std::to_string(i);
        const auto* sensor = registry.getSensor(id.c_str());
        CHECK(sensor != nullptr && sensor->available);
        CHECK(sensor->value == static_cast<double>(i));
        CHECK(std::strcmp(sensor->unit, "C") == 0);
    }
    CHECK(registry.update("sensor.15", -2) == Result::Ok);
    CHECK(registry.unavailable("sensor.0") == Result::Ok);
    CHECK(registry.size() == SensorRegistry::MaxSensors);
    return true;
}

bool independent_sensors() {
    SensorRegistry registry;
    CHECK(registry.addSensor("temperature.indoor", "Temperature", "C") == Result::Ok);
    CHECK(registry.addSensor("temperature.outdoor", "Temperature", "C") == Result::Ok);
    CHECK(registry.update("temperature.indoor", 23.5) == Result::Ok);
    CHECK(!registry.getSensor("temperature.outdoor")->available);
    CHECK(registry.update("temperature.outdoor", -5) == Result::Ok);
    CHECK(registry.unavailable("temperature.indoor") == Result::Ok);
    CHECK(!registry.getSensor("temperature.indoor")->available);
    CHECK(registry.getSensor("temperature.outdoor")->available);
    CHECK(registry.getSensor("temperature.outdoor")->value == -5);
    return true;
}

bool stable_views() {
    SensorRegistry registry;
    CHECK(registry.addSensor("soil", "Soil", "raw") == Result::Ok);
    const auto* first = registry.getSensor("soil");
    for (std::size_t i = 1; i < SensorRegistry::MaxSensors; ++i) {
        const std::string id = "other." + std::to_string(i);
        CHECK(registry.addSensor(id.c_str(), "Other", "") == Result::Ok);
        CHECK(registry.getSensor("soil") == first);
    }
    CHECK(registry.update("soil", 15) == Result::Ok);
    CHECK(first->value == 15 && first->available);
    const SensorRegistry& readOnly = registry;
    CHECK(readOnly.getSensor("soil") == first);
    return true;
}

int main(int argc, char** argv) {
    struct Test { const char* name; bool (*run)(); };
    const Test tests[] = {
        {"registration", registration}, {"metadata_ownership", metadata_ownership},
        {"optional_metadata", optional_metadata}, {"duplicate_ids", duplicate_ids},
        {"invalid_ids", invalid_ids}, {"string_boundaries", string_boundaries},
        {"utf8_metadata", utf8_metadata}, {"updates", updates},
        {"invalid_values", invalid_values}, {"availability", availability},
        {"unknown_sensors", unknown_sensors}, {"sensor_limit", sensor_limit},
        {"independent_sensors", independent_sensors}, {"stable_views", stable_views}
    };
    unsigned int executed = 0;
    for (const auto& test : tests) {
        if (argc > 1 && std::strcmp(argv[1], test.name) != 0) continue;
        if (!test.run()) return 1;
        std::printf("PASS %s\n", test.name);
        ++executed;
    }
    if (executed == 0) {
        std::fprintf(stderr, "Unknown test name\n");
        return 1;
    }
    std::printf("%u tests passed; sizeof(SensorRegistry) = %zu bytes\n",
                executed, sizeof(SensorRegistry));
    return 0;
}
