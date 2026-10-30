#include "PhysicalLink.h"
#include "BleTransport.h"
#include "DeviceIdentity.h"
#include <cstring>
#include <new>
using physical_link::SensorRegistry;
using physical_link::protocol::BleTransport;
using physical_link::protocol::Status;
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
PLResult translate(BleTransport::Result result) {
    switch (result) {
    case BleTransport::Result::Ok: return PLResult::Ok;
    case BleTransport::Result::NotRunning: return PLResult::NotRunning;
    case BleTransport::Result::AlreadyRunning: return PLResult::AlreadyRunning;
    case BleTransport::Result::InvalidMetadata: return PLResult::InvalidMetadata;
    case BleTransport::Result::Busy: return PLResult::Busy;
    case BleTransport::Result::OutOfMemory: return PLResult::OutOfMemory;
    case BleTransport::Result::StartFailed: return PLResult::BleStartFailed;
    case BleTransport::Result::StopFailed: return PLResult::BleStopFailed;
    }
    return PLResult::BleStartFailed;
}
PLResult translate(Status result) {
    switch (result) {
    case Status::Ok: return PLResult::Ok;
    case Status::NotStarted: return PLResult::NotRunning;
    case Status::InvalidId: return PLResult::InvalidId;
    case Status::UnknownSensor: return PLResult::UnknownSensor;
    case Status::InvalidValue: return PLResult::InvalidValue;
    case Status::InvalidTime: return PLResult::InvalidTime;
    case Status::SequenceExhausted: return PLResult::SequenceExhausted;
    case Status::MetadataTooLarge: return PLResult::MetadataTooLarge;
    default: return PLResult::InvalidMetadata;
    }
}
}
struct PhysicalLink::Impl {
    SensorRegistry sensors;
    BleTransport transport;
    char projectName[65] = {};
    bool validProject = false;
    bool cleanupPending = false;
};
PhysicalLink::PhysicalLink(const char* name) : impl_(new (std::nothrow) Impl) {
    if (!impl_ || !name || !name[0]) return;
    SensorRegistry validation;
    if (validation.addSensor("project", name, "") == SensorRegistry::Result::Ok) {
        std::memcpy(impl_->projectName, name, std::strlen(name) + 1);
        impl_->validProject = true;
    }
}
PhysicalLink::~PhysicalLink() {
    // BleTransport retains live callback storage if host shutdown fails.
    delete impl_;
}
bool PhysicalLink::isRunning() const { return impl_ && impl_->transport.isRunning(); }
PLResult PhysicalLink::addSensor(const char* id, const char* name, const char* unit) {
    if (!impl_) return PLResult::OutOfMemory;
    if (isRunning() || impl_->cleanupPending) return PLResult::Busy;
    return translate(impl_->sensors.addSensor(id, name, unit));
}
PLResult PhysicalLink::begin() {
    if (!impl_) return PLResult::OutOfMemory;
    if (isRunning()) return PLResult::AlreadyRunning;
    if (impl_->cleanupPending) return PLResult::Busy;
    if (!impl_->validProject) return PLResult::InvalidProjectName;
    if (impl_->sensors.size() == 0) return PLResult::InvalidMetadata;
    char deviceId[37];
    physical_link::boardDeviceId(deviceId);
    const auto result = impl_->transport.begin(impl_->sensors, deviceId,
                                              impl_->projectName, physical_link::freshEpoch());
    impl_->cleanupPending = result == BleTransport::Result::StopFailed;
    return translate(result);
}
PLResult PhysicalLink::update(const char* id, double value) {
    if (!impl_) return PLResult::OutOfMemory;
    if (!isRunning()) return PLResult::NotRunning;
    if (!impl_->sensors.getSensor(id)) return translate(impl_->sensors.update(id, value));
    return translate(impl_->transport.update(id, value));
}
PLResult PhysicalLink::unavailable(const char* id) {
    if (!impl_) return PLResult::OutOfMemory;
    if (!isRunning()) return PLResult::NotRunning;
    if (!impl_->sensors.getSensor(id)) return translate(impl_->sensors.unavailable(id));
    return translate(impl_->transport.unavailable(id));
}
PLResult PhysicalLink::poll() {
    if (!impl_) return PLResult::OutOfMemory;
    return translate(impl_->transport.poll());
}
PLResult PhysicalLink::end() {
    if (!impl_) return PLResult::Ok;
    const auto result = impl_->transport.end();
    impl_->cleanupPending = result == BleTransport::Result::StopFailed;
    return translate(result);
}
