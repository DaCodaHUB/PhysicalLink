#include "CapabilitySerializer.h"

#include <array>
#include <cstdio>
#include <cstring>
#include <limits>
#include <string>

using physical_link::CapabilitySerializer;
using physical_link::SensorRegistry;
using Status = CapabilitySerializer::Status;
using Result = SensorRegistry::Result;
using Buffer = std::array<char, CapabilitySerializer::MaxBufferSize>;

#define CHECK(expression) do { \
    if (!(expression)) { \
        std::fprintf(stderr, "%s:%d: %s\n", __FILE__, __LINE__, #expression); \
        return false; \
    } \
} while (false)

bool render(const SensorRegistry& registry, std::string& json) {
    Buffer buffer{};
    const auto result = CapabilitySerializer::serialize(registry, buffer.data(), buffer.size());
    CHECK(result.status == Status::Ok);
    CHECK(result.bytesWritten == std::strlen(buffer.data()));
    CHECK(result.requiredCapacity == result.bytesWritten + 1);
    json.assign(buffer.data(), result.bytesWritten);
    return true;
}

std::string escapedMetadata() {
    std::string text;
    for (char c = 1; c < 32; ++c) text += c;
    text += "\"\\/\xc3\xa9\xe2\x82\xac\xf0\x9f\x8c\xa1";
    return text;
}

bool fillMaximum(SensorRegistry& registry) {
    const std::string name(SensorRegistry::MaxNameLength, '\x01');
    const std::string unit(SensorRegistry::MaxUnitLength, '\x1f');
    for (std::size_t i = 0; i < SensorRegistry::MaxSensors; ++i) {
        std::string id(SensorRegistry::MaxIdLength, 'a');
        id.back() = static_cast<char>('a' + i);
        CHECK(registry.addSensor(id.c_str(), name.c_str(), unit.c_str()) == Result::Ok);
    }
    return true;
}

bool empty() {
    SensorRegistry registry;
    std::string json;
    CHECK(render(registry, json));
    CHECK(json == "{\"schemaVersion\":1,\"sensors\":[]}");
    return true;
}

bool one_sensor() {
    SensorRegistry registry;
    CHECK(registry.addSensor("temperature", "Temperature", "C") == Result::Ok);
    std::string json;
    CHECK(render(registry, json));
    CHECK(json == "{\"schemaVersion\":1,\"sensors\":[{\"id\":\"temperature\","
                  "\"name\":\"Temperature\",\"unit\":\"C\",\"type\":\"number\"}]}");
    return true;
}

bool multiple_order() {
    SensorRegistry registry;
    CHECK(registry.addSensor("z.outdoor", "Temperature", "C") == Result::Ok);
    CHECK(registry.addSensor("a.indoor", "Temperature", "C") == Result::Ok);
    CHECK(registry.addSensor("middle", nullptr, nullptr) == Result::Ok);
    std::string json;
    CHECK(render(registry, json));
    CHECK(json == "{\"schemaVersion\":1,\"sensors\":["
        "{\"id\":\"z.outdoor\",\"name\":\"Temperature\",\"unit\":\"C\",\"type\":\"number\"},"
        "{\"id\":\"a.indoor\",\"name\":\"Temperature\",\"unit\":\"C\",\"type\":\"number\"},"
        "{\"id\":\"middle\",\"name\":\"middle\",\"unit\":\"\",\"type\":\"number\"}]}");
    return true;
}

bool escaping() {
    SensorRegistry registry;
    CHECK(registry.addSensor("probe", "A\"B\\C/D", "\"\\\n") == Result::Ok);
    std::string json;
    CHECK(render(registry, json));
    CHECK(json == "{\"schemaVersion\":1,\"sensors\":[{\"id\":\"probe\","
        "\"name\":\"A\\\"B\\\\C/D\",\"unit\":\"\\\"\\\\\\u000a\",\"type\":\"number\"}]}");
    return true;
}

bool control_characters() {
    SensorRegistry registry;
    std::string controls;
    std::string expected;
    for (unsigned int c = 1; c < 32; ++c) {
        controls += static_cast<char>(c);
        char escape[7];
        std::snprintf(escape, sizeof(escape), "\\u%04x", c);
        expected += escape;
    }
    CHECK(registry.addSensor("control", controls.c_str(), "\b\f\r\t") == Result::Ok);
    std::string json;
    CHECK(render(registry, json));
    CHECK(json.find("\"name\":\"" + expected + "\"") != std::string::npos);
    CHECK(json.find("\"unit\":\"\\u0008\\u000c\\u000d\\u0009\"") != std::string::npos);
    for (unsigned char byte : json) CHECK(byte >= 0x20);
    return true;
}

bool max_sensors() {
    SensorRegistry registry;
    for (std::size_t i = 0; i < SensorRegistry::MaxSensors; ++i) {
        const std::string id = "sensor." + std::to_string(i);
        CHECK(registry.addSensor(id.c_str(), "Sensor", "C") == Result::Ok);
    }
    CHECK(registry.addSensor("extra", "Extra", "C") == Result::CapacityExceeded);
    std::string json;
    CHECK(render(registry, json));
    std::size_t count = 0;
    std::size_t offset = 0;
    while ((offset = json.find("\"type\":\"number\"", offset)) != std::string::npos) {
        ++count;
        ++offset;
    }
    CHECK(count == SensorRegistry::MaxSensors);
    CHECK(json.find("sensor.15") != std::string::npos);
    CHECK(json.find("extra") == std::string::npos);
    return true;
}

bool max_fields() {
    SensorRegistry registry;
    CHECK(fillMaximum(registry));
    std::string json;
    CHECK(render(registry, json));
    CHECK(json.size() + 1 == CapabilitySerializer::MaxBufferSize);
    const auto query = CapabilitySerializer::serialize(registry, nullptr, 0);
    CHECK(query.requiredCapacity == CapabilitySerializer::MaxBufferSize);
    Buffer buffer;
    buffer.fill('!');
    const Buffer original = buffer;
    const auto failure = CapabilitySerializer::serialize(registry, buffer.data(), buffer.size() - 1);
    CHECK(failure.status == Status::BufferTooSmall);
    CHECK(failure.bytesWritten == 0 && buffer == original);
    // Also preserve multibyte UTF-8 at the exact byte-length boundary.
    SensorRegistry unicode;
    std::string name;
    std::string unit;
    for (unsigned int i = 0; i < 32; ++i) name += "\xc3\xa9";
    for (unsigned int i = 0; i < 8; ++i) unit += "\xc2\xb0";
    CHECK(unicode.addSensor("utf8", name.c_str(), unit.c_str()) == Result::Ok);
    CHECK(render(unicode, json));
    CHECK(json.find(name) != std::string::npos && json.find(unit) != std::string::npos);
    return true;
}

bool capacity_boundaries() {
    SensorRegistry registry;
    CHECK(registry.addSensor("probe", "Quote\"\\\n", "C") == Result::Ok);
    const auto query = CapabilitySerializer::serialize(registry, nullptr, 0);
    CHECK(query.status == Status::BufferTooSmall && query.bytesWritten == 0);
    Buffer buffer;
    buffer.fill('!');
    const Buffer original = buffer;
    // Every insufficient capacity, including missing space for only the NUL.
    for (std::size_t capacity = 0; capacity < query.requiredCapacity; ++capacity) {
        const auto result = CapabilitySerializer::serialize(registry, buffer.data(), capacity);
        CHECK(result.status == Status::BufferTooSmall);
        CHECK(result.bytesWritten == 0);
        CHECK(result.requiredCapacity == query.requiredCapacity);
        CHECK(buffer == original);
    }
    const auto result = CapabilitySerializer::serialize(registry, buffer.data(), query.requiredCapacity);
    CHECK(result.status == Status::Ok);
    CHECK(result.bytesWritten + 1 == query.requiredCapacity);
    CHECK(buffer[result.bytesWritten] == '\0');
    CHECK(buffer[query.requiredCapacity] == '!'); // one-past-capacity guard
    CHECK(result.bytesWritten == std::strlen(buffer.data()));
    return true;
}

bool invalid_buffers() {
    SensorRegistry registry;
    const auto query = CapabilitySerializer::serialize(registry, nullptr, 0);
    CHECK(query.status == Status::BufferTooSmall);
    CHECK(query.requiredCapacity == std::strlen("{\"schemaVersion\":1,\"sensors\":[]}") + 1);
    for (auto capacity : {std::size_t(1), query.requiredCapacity,
                          std::numeric_limits<std::size_t>::max()}) {
        const auto result = CapabilitySerializer::serialize(registry, nullptr, capacity);
        CHECK(result.status == Status::InvalidBuffer);
        CHECK(result.bytesWritten == 0 && result.requiredCapacity == query.requiredCapacity);
    }
    char zeroCapacity = '!';
    CHECK(CapabilitySerializer::serialize(registry, &zeroCapacity, 0).status == Status::BufferTooSmall);
    CHECK(zeroCapacity == '!');
    return true;
}

bool determinism() {
    SensorRegistry registry;
    CHECK(registry.addSensor("probe", escapedMetadata().c_str(), "\xc2\xb0" "C") == Result::Ok);
    std::string first;
    CHECK(render(registry, first));
    std::string second;
    CHECK(render(registry, second));
    CHECK(second == first);
    SensorRegistry equivalent;
    CHECK(equivalent.addSensor("probe", escapedMetadata().c_str(), "\xc2\xb0" "C") == Result::Ok);
    CHECK(render(equivalent, second));
    CHECK(second == first);
    CHECK(registry.update("probe", 999.125) == Result::Ok);
    CHECK(render(registry, second));
    CHECK(second == first);
    CHECK(registry.unavailable("probe") == Result::Ok);
    CHECK(render(registry, second));
    CHECK(second == first); // no reading or availability in metadata
    CHECK(second.find("value") == std::string::npos);
    CHECK(second.find("available") == std::string::npos);
    return true;
}

bool preservation() {
    SensorRegistry registry;
    char name[] = "Probe \"one\"";
    char unit[] = "\xc2\xb0" "C";
    CHECK(registry.addSensor("probe", name, unit) == Result::Ok);
    CHECK(registry.update("probe", 23.5) == Result::Ok);
    name[0] = 'X';
    unit[0] = 'X';
    const auto* view = registry.getSensor("probe");
    const SensorRegistry::Sensor before = *view;
    std::string json;
    CHECK(render(registry, json));
    CHECK(json.find("Probe \\\"one\\\"") != std::string::npos);
    CHECK(json.find("\xc2\xb0" "C") != std::string::npos);
    CHECK(registry.getSensorAt(0) == view && registry.size() == 1);
    CHECK(std::strcmp(view->id, before.id) == 0);
    CHECK(std::strcmp(view->name, before.name) == 0);
    CHECK(std::strcmp(view->unit, before.unit) == 0);
    CHECK(view->value == before.value && view->available == before.available);
    return true;
}

bool registry_access() {
    SensorRegistry registry;
    CHECK(registry.getSensorAt(0) == nullptr);
    CHECK(registry.addSensor("z", "Last alphabetically", "") == Result::Ok);
    CHECK(registry.addSensor("a", "First alphabetically", "") == Result::Ok);
    const SensorRegistry& readOnly = registry;
    CHECK(readOnly.getSensorAt(0) == registry.getSensor("z"));
    CHECK(readOnly.getSensorAt(1) == registry.getSensor("a"));
    CHECK(readOnly.getSensorAt(2) == nullptr);
    CHECK(readOnly.getSensorAt(SensorRegistry::MaxSensors) == nullptr);
    CHECK(readOnly.getSensorAt(std::numeric_limits<std::size_t>::max()) == nullptr);
    return true;
}

bool emit(const SensorRegistry& registry) {
    std::string json;
    CHECK(render(registry, json));
    CHECK(std::fwrite(json.data(), 1, json.size(), stdout) == json.size());
    std::putchar('\n');
    return true;
}

bool jsonFixtures() {
    SensorRegistry registry;
    CHECK(emit(registry));
    CHECK(registry.addSensor("temperature", "Temperature", "C") == Result::Ok);
    CHECK(emit(registry));
    CHECK(registry.addSensor("a.escaped", escapedMetadata().c_str(), "\"\\\b\f\n\r\t") == Result::Ok);
    CHECK(registry.addSensor("optional", nullptr, nullptr) == Result::Ok);
    CHECK(registry.update("temperature", 12345) == Result::Ok);
    CHECK(emit(registry));
    SensorRegistry maximum;
    CHECK(fillMaximum(maximum));
    CHECK(emit(maximum));
    return true;
}

int main(int argc, char** argv) {
    if (argc == 2 && std::strcmp(argv[1], "--json-fixtures") == 0) return jsonFixtures() ? 0 : 1;
    struct Test { const char* name; bool (*run)(); };
    const Test tests[] = {
        {"empty", empty}, {"one_sensor", one_sensor}, {"multiple_order", multiple_order},
        {"escaping", escaping}, {"control_characters", control_characters},
        {"max_sensors", max_sensors}, {"max_fields", max_fields},
        {"capacity_boundaries", capacity_boundaries}, {"invalid_buffers", invalid_buffers},
        {"determinism", determinism}, {"preservation", preservation}, {"registry_access", registry_access}
    };
    unsigned int executed = 0;
    for (const auto& test : tests) {
        if (argc > 1 && std::strcmp(argv[1], test.name) != 0) continue;
        if (!test.run()) return 1;
        std::printf("PASS %s\n", test.name);
        ++executed;
    }
    if (executed == 0) return 1;
    std::printf("%u serializer tests passed; max buffer = %zu bytes\n",
                executed, CapabilitySerializer::MaxBufferSize);
    return 0;
}
