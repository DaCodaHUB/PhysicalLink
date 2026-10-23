#include "PhysicalLink.h"
#include "LegacyBleAdapter.h"
#include "LegacyBleTransport.h"
#include <cmath>
#include <cstring>
#include <new>

using physical_link::SensorRegistry;
using physical_link::LegacyBleAdapter;
using physical_link::LegacyBleTransport;
namespace {
PLResult translate(SensorRegistry::Result result) {
    switch (result) {
    case SensorRegistry::Result::Ok: return PLResult::Ok;
    case SensorRegistry::Result::InvalidId: return PLResult::InvalidId;
    case SensorRegistry::Result::InvalidName: return PLResult::InvalidName;
    case SensorRegistry::Result::InvalidUnit: return PLResult::InvalidUnit;
    case SensorRegistry::Result::DuplicateId: return PLResult::DuplicateId;
    case SensorRegistry::Result::CapacityExceeded: return PLResult::CapacityExceeded;
    case SensorRegistry::Result::UnknownSensor: return PLResult::UnknownSensor;
    case SensorRegistry::Result::InvalidValue: return PLResult::InvalidValue;
    }
    return PLResult::InvalidValue;
}
void* owner = nullptr; // Single owning application task, including all instances.
}
struct PhysicalLink::Impl {
    SensorRegistry sensors;
    LegacyBleAdapter adapter;
    char projectName[65] = {};
    bool validProject = false;
    bool running = false;
    bool dirty[SensorRegistry::MaxSensors] = {};
    LegacyBleTransport* transport = nullptr;
    void mark(const char* id) {
        for (std::size_t i = 0; i < sensors.size(); ++i)
            if (std::strcmp(sensors.getSensorAt(i)->id, id) == 0) dirty[i] = true;
    }
};
PhysicalLink::PhysicalLink(const char* name) : impl_(new (std::nothrow) Impl) {
    if (!impl_ || !name) return;
    // Reuse bounded UTF-8 validation without exposing the registry publicly.
    SensorRegistry validation;
    if (name[0] && validation.addSensor("project", name, "") == SensorRegistry::Result::Ok) {
        std::memcpy(impl_->projectName, name, std::strlen(name) + 1);
        impl_->validProject = true;
    }
}
PhysicalLink::~PhysicalLink() {
    // If host shutdown fails, retain transport/callback lifetime and ownership.
    // The caller should use end() to observe/retry failure before destruction.
    if (end() == PLResult::Ok) delete impl_;
}
bool PhysicalLink::isRunning() const { return impl_ && impl_->running; }
PLResult PhysicalLink::addSensor(const char* id, const char* name, const char* unit) {
    if (!impl_) return PLResult::OutOfMemory;
    if (impl_->running || impl_->transport) return PLResult::Busy;
    return translate(impl_->sensors.addSensor(id, name, unit));
}
PLResult PhysicalLink::begin() {
    if (!impl_) return PLResult::OutOfMemory;
    if (impl_->running) return PLResult::AlreadyRunning;
    if (impl_->transport || owner) return PLResult::Busy;
    if (!impl_->validProject) return PLResult::InvalidProjectName;
    char metadata[LegacyBleAdapter::AttributeLimit + 1];
    const auto result = impl_->adapter.serialize(impl_->sensors, metadata, sizeof(metadata));
    if (result.status == LegacyBleAdapter::Status::MetadataTooLarge) return PLResult::MetadataTooLarge;
    if (result.status != LegacyBleAdapter::Status::Ok) return PLResult::UnsupportedProfile;
    impl_->transport = physical_link::createLegacyBleTransport();
    if (!impl_->transport) return PLResult::OutOfMemory;
    owner = impl_;
    const auto started = impl_->transport->start(impl_->sensors, metadata);
    if (started != PLResult::Ok) {
        if (end() != PLResult::Ok) return PLResult::BleStopFailed;
        return started;
    }
    for (std::size_t i = 0; i < impl_->sensors.size(); ++i) {
        impl_->sensors.unavailable(impl_->sensors.getSensorAt(i)->id);
        impl_->dirty[i] = false;
    }
    impl_->running = true;
    return PLResult::Ok;
}
PLResult PhysicalLink::update(const char* id, double value) {
    if (!isRunning()) return PLResult::NotRunning;
    const auto* sensor = impl_->sensors.getSensor(id);
    if (!sensor) return translate(impl_->sensors.update(id, value));
    // All v1 samples must fit a notification at the minimum MTU (20 bytes).
    bool valid = std::isfinite(value);
    if (valid && std::strcmp(id, "soil") == 0)
        valid = value >= 0 && value <= 4095 && std::floor(value) == value;
    if (valid) valid = std::to_string(value).size() <= 20;
    if (!valid) {
        impl_->sensors.unavailable(id);
        impl_->mark(id);
        return PLResult::InvalidValue;
    }
    const auto result = translate(impl_->sensors.update(id, value));
    if (result == PLResult::Ok) impl_->mark(id);
    return result;
}
PLResult PhysicalLink::unavailable(const char* id) {
    if (!isRunning()) return PLResult::NotRunning;
    const auto result = translate(impl_->sensors.unavailable(id));
    if (result == PLResult::Ok) impl_->mark(id);
    return result;
}
PLResult PhysicalLink::poll() {
    if (!isRunning()) return PLResult::NotRunning;
    PLResult result = PLResult::Ok;
    for (std::size_t i = 0; i < impl_->sensors.size(); ++i) {
        if (!impl_->dirty[i]) continue;
        std::string text;
        const auto status = impl_->adapter.formatReading(impl_->sensors, impl_->sensors.getSensorAt(i)->id, text);
        if (impl_->transport->publish(i, text, status == LegacyBleAdapter::Status::Ok))
            impl_->dirty[i] = false;
        else result = PLResult::SendFailed;
    }
    return result;
}
PLResult PhysicalLink::end() {
    if (!impl_) return PLResult::Ok;
    impl_->running = false;
    if (impl_->transport) {
        if (!impl_->transport->stop()) return PLResult::BleStopFailed;
        delete impl_->transport;
        impl_->transport = nullptr;
    }
    if (owner == impl_) owner = nullptr;
    return PLResult::Ok;
}
