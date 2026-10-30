#include "BleTransport.h"
#include "BleDriver.h"
#include <new>
namespace physical_link { namespace protocol {
namespace { void* owner = nullptr; }
struct BleTransport::Impl {
    MetadataSession session;
    MetadataDriver* driver = nullptr;
    bool running = false;
};
BleTransport::BleTransport() : impl_(new (std::nothrow) Impl) {}
BleTransport::~BleTransport() {
    // Preserve callback storage if host stop fails. Explicit end permits retry.
    if (end() == Result::Ok) delete impl_;
}
bool BleTransport::isRunning() const { return impl_ && impl_->running; }
BleTransport::Result BleTransport::begin(const SensorRegistry& sensors,
    const char* id, const char* name, std::uint64_t epoch) {
    if (!impl_) return Result::OutOfMemory;
    if (impl_->running) return Result::AlreadyRunning;
    if (impl_->driver || owner) return Result::Busy;
    impl_->session.reset();
    if (impl_->session.prepare(sensors, id, name, epoch, metadataMillis()) != Status::Ok) return Result::InvalidMetadata;
    impl_->driver = createMetadataDriver();
    if (!impl_->driver) return Result::OutOfMemory;
    owner = impl_;
    const auto result = impl_->driver->start(impl_->session);
    if (result != Result::Ok) {
        if (end() != Result::Ok) return Result::StopFailed;
        return result;
    }
    impl_->running = true;
    return Result::Ok;
}
BleTransport::Result BleTransport::poll() {
    if (!isRunning()) return Result::NotRunning;
    return impl_->driver->poll();
}
Status BleTransport::update(const char* id, double value) {
    if (!isRunning()) return Status::NotStarted;
    return impl_->driver->update(id, value, true);
}
Status BleTransport::unavailable(const char* id) {
    if (!isRunning()) return Status::NotStarted;
    return impl_->driver->update(id, 0, false);
}
BleTransport::Result BleTransport::end() {
    if (!impl_) return Result::Ok;
    impl_->running = false;
    if (impl_->driver) {
        if (!impl_->driver->stop()) return Result::StopFailed;
        delete impl_->driver; impl_->driver = nullptr;
    }
    impl_->session.reset();
    if (owner == impl_) owner = nullptr;
    return Result::Ok;
}
} }
