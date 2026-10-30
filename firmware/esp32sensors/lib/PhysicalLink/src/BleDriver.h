#ifndef PHYSICAL_LINK_BLE_DRIVER_H
#define PHYSICAL_LINK_BLE_DRIVER_H
#include "BleTransport.h"
#include "MetadataSession.h"
namespace physical_link { namespace protocol {
// Private test seam; the real driver owns host, raw GATT definitions and events.
class MetadataDriver {
public:
    virtual ~MetadataDriver() = default;
    virtual BleTransport::Result start(MetadataSession&) = 0;
    virtual BleTransport::Result poll() = 0;
    virtual Status update(const char* id, double value, bool available) = 0;
    virtual bool stop() = 0;
};
MetadataDriver* createMetadataDriver();
std::uint32_t metadataMillis(); // Backend clock, also used to initialize cache.
} }
#endif
