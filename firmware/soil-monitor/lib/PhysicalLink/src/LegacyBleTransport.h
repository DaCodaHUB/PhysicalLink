#ifndef PHYSICAL_LINK_LEGACY_BLE_TRANSPORT_H
#define PHYSICAL_LINK_LEGACY_BLE_TRANSPORT_H
#include "PhysicalLink.h"
#include "SensorRegistry.h"
#include <string>
namespace physical_link {
// Private link seam: native tests substitute a recording transport.
class LegacyBleTransport {
public:
    virtual ~LegacyBleTransport() = default;
    virtual PLResult start(const SensorRegistry&, const char* metadata) = 0;
    virtual bool publish(std::size_t index, const std::string&, bool notify) = 0;
    virtual bool stop() = 0;
};
LegacyBleTransport* createLegacyBleTransport();
}
#endif
