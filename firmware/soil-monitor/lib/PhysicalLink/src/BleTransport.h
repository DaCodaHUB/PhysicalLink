#ifndef PHYSICAL_LINK_BLE_TRANSPORT_H
#define PHYSICAL_LINK_BLE_TRANSPORT_H
#include "SensorRegistry.h"
#include "ProtocolCodec.h"
#include <cstdint>
namespace physical_link { namespace protocol {
class BleTransport {
public:
    enum class Result { Ok, InvalidMetadata, AlreadyRunning, NotRunning, Busy, OutOfMemory, StartFailed, StopFailed };
    BleTransport();
    ~BleTransport();
    BleTransport(const BleTransport&) = delete;
    BleTransport& operator=(const BleTransport&) = delete;
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
