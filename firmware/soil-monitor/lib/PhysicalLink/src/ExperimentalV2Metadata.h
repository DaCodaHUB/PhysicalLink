#ifndef PHYSICAL_LINK_EXPERIMENTAL_V2_METADATA_H
#define PHYSICAL_LINK_EXPERIMENTAL_V2_METADATA_H
#if !defined(PHYSICAL_LINK_EXPERIMENTAL_V2_METADATA) || !PHYSICAL_LINK_EXPERIMENTAL_V2_METADATA
#error "Enable PHYSICAL_LINK_EXPERIMENTAL_V2_METADATA only in the separate experimental build"
#endif
#include "SensorRegistry.h"
#include "V2Codec.h"
#include <cstdint>
namespace physical_link { namespace v2 {
class ExperimentalMetadata {
public:
    enum class Result { Ok, InvalidMetadata, AlreadyRunning, NotRunning, Busy, OutOfMemory, StartFailed, StopFailed };
    ExperimentalMetadata();
    ~ExperimentalMetadata();
    ExperimentalMetadata(const ExperimentalMetadata&) = delete;
    ExperimentalMetadata& operator=(const ExperimentalMetadata&) = delete;
    Result begin(const SensorRegistry&, const char* deviceId, const char* name, std::uint64_t epoch);
    Result poll();
    // One owning application task, like begin/poll/end. Time is sampled by the
    // backend under the session lock; callbacks never call maker sensor code.
    Status update(const char* id, double value);
    Status unavailable(const char* id);
    Result end();
    bool isRunning() const;
private:
    struct Impl;
    Impl* impl_;
};
} }
#endif
