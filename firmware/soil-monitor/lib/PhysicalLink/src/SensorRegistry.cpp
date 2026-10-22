#include "SensorRegistry.h"

#include <cmath>
#include <cstring>

namespace physical_link {

// Definitions also allow C++11 callers to odr-use the public constants.
constexpr std::size_t SensorRegistry::MaxSensors;
constexpr std::size_t SensorRegistry::MaxIdLength;
constexpr std::size_t SensorRegistry::MaxNameLength;
constexpr std::size_t SensorRegistry::MaxUnitLength;

namespace {

// Never scan beyond the field limit plus its required terminator.
std::size_t boundedLength(const char* text, std::size_t limit) {
    std::size_t length = 0;
    while (length <= limit && text[length] != '\0') {
        ++length;
    }
    return length;
}

bool validUtf8(const char* text, std::size_t length) {
    const auto* bytes = reinterpret_cast<const unsigned char*>(text);
    std::size_t i = 0;
    while (i < length) {
        const unsigned char first = bytes[i++];
        if (first < 0x80) {
            continue;
        }
        unsigned int codepoint;
        unsigned int minimum;
        std::size_t remaining;
        if (first >= 0xc2 && first <= 0xdf) {
            codepoint = first & 0x1f;
            minimum = 0x80;
            remaining = 1;
        } else if (first >= 0xe0 && first <= 0xef) {
            codepoint = first & 0x0f;
            minimum = 0x800;
            remaining = 2;
        } else if (first >= 0xf0 && first <= 0xf4) {
            codepoint = first & 0x07;
            minimum = 0x10000;
            remaining = 3;
        } else {
            return false;
        }
        if (remaining > length - i) {
            return false;
        }
        while (remaining-- > 0) {
            const unsigned char next = bytes[i++];
            if ((next & 0xc0) != 0x80) {
                return false;
            }
            codepoint = (codepoint << 6) | (next & 0x3f);
        }
        if (codepoint < minimum || codepoint > 0x10ffff ||
            (codepoint >= 0xd800 && codepoint <= 0xdfff)) {
            return false;
        }
    }
    return true;
}

} // namespace

bool SensorRegistry::validId(const char* id) {
    if (id == nullptr) {
        return false;
    }
    const std::size_t length = boundedLength(id, MaxIdLength);
    if (length == 0 || length > MaxIdLength || id[0] < 'a' || id[0] > 'z') {
        return false;
    }
    for (std::size_t i = 1; i < length; ++i) {
        const char c = id[i];
        if (!((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') ||
              c == '_' || c == '.' || c == '-')) {
            return false;
        }
    }
    return true;
}

std::size_t SensorRegistry::findIndex(const char* id) const {
    for (std::size_t i = 0; i < size_; ++i) {
        if (std::strcmp(sensors_[i].id, id) == 0) {
            return i;
        }
    }
    return size_;
}

SensorRegistry::Result SensorRegistry::addSensor(
    const char* id, const char* name, const char* unit) {
    if (!validId(id)) {
        return Result::InvalidId;
    }
    if (name == nullptr || name[0] == '\0') {
        name = id;
    }
    if (unit == nullptr) {
        unit = "";
    }
    const std::size_t nameLength = boundedLength(name, MaxNameLength);
    if (nameLength > MaxNameLength || !validUtf8(name, nameLength)) {
        return Result::InvalidName;
    }
    const std::size_t unitLength = boundedLength(unit, MaxUnitLength);
    if (unitLength > MaxUnitLength || !validUtf8(unit, unitLength)) {
        return Result::InvalidUnit;
    }
    if (findIndex(id) != size_) {
        return Result::DuplicateId;
    }
    if (size_ == MaxSensors) {
        return Result::CapacityExceeded;
    }

    Sensor& sensor = sensors_[size_];
    std::memcpy(sensor.id, id, std::strlen(id) + 1); // ID already bounded
    std::memcpy(sensor.name, name, nameLength + 1);
    std::memcpy(sensor.unit, unit, unitLength + 1);
    ++size_;
    return Result::Ok;
}

SensorRegistry::Result SensorRegistry::update(const char* id, double value) {
    if (!validId(id)) {
        return Result::InvalidId;
    }
    const std::size_t index = findIndex(id);
    if (index == size_) {
        return Result::UnknownSensor;
    }
    if (!std::isfinite(value)) {
        return Result::InvalidValue;
    }
    sensors_[index].value = value;
    sensors_[index].available = true;
    return Result::Ok;
}

SensorRegistry::Result SensorRegistry::unavailable(const char* id) {
    if (!validId(id)) {
        return Result::InvalidId;
    }
    const std::size_t index = findIndex(id);
    if (index == size_) {
        return Result::UnknownSensor;
    }
    sensors_[index].available = false;
    return Result::Ok;
}

const SensorRegistry::Sensor* SensorRegistry::getSensor(const char* id) const {
    if (!validId(id)) {
        return nullptr;
    }
    const std::size_t index = findIndex(id);
    return index == size_ ? nullptr : &sensors_[index];
}

const SensorRegistry::Sensor* SensorRegistry::getSensorAt(std::size_t index) const {
    return index < size_ ? &sensors_[index] : nullptr;
}

} // namespace physical_link
