#if defined(PHYSICAL_LINK_EXPERIMENTAL_V2_METADATA) && PHYSICAL_LINK_EXPERIMENTAL_V2_METADATA
#include "ExperimentalV2Metadata.h"
#include "ExperimentalMetadataDriver.h"
#include <new>
namespace physical_link { namespace v2 {
namespace { void* owner = nullptr; }
struct ExperimentalMetadata::Impl {
    MetadataSession session;
    MetadataDriver* driver = nullptr;
    bool running = false;
};
ExperimentalMetadata::ExperimentalMetadata() : impl_(new (std::nothrow) Impl) {}
ExperimentalMetadata::~ExperimentalMetadata() {
    // Preserve callback storage if host stop fails. Explicit end permits retry.
    if (end() == Result::Ok) delete impl_;
}
bool ExperimentalMetadata::isRunning() const { return impl_ && impl_->running; }
ExperimentalMetadata::Result ExperimentalMetadata::begin(const SensorRegistry& sensors,
    const char* id, const char* name, std::uint64_t epoch) {
    if (!impl_) return Result::OutOfMemory;
    if (impl_->running) return Result::AlreadyRunning;
    if (impl_->driver || owner) return Result::Busy;
    impl_->session.reset();
    if (impl_->session.prepare(sensors, id, name, epoch) != Status::Ok) return Result::InvalidMetadata;
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
ExperimentalMetadata::Result ExperimentalMetadata::poll() {
    if (!isRunning()) return Result::NotRunning;
    return impl_->driver->poll();
}
ExperimentalMetadata::Result ExperimentalMetadata::end() {
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
#endif
