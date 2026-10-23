#ifndef PHYSICAL_LINK_EXPERIMENTAL_METADATA_DRIVER_H
#define PHYSICAL_LINK_EXPERIMENTAL_METADATA_DRIVER_H
#include "ExperimentalV2Metadata.h"
#include "V2MetadataSession.h"
namespace physical_link { namespace v2 {
// Private test seam; the real driver owns host, raw GATT definitions and events.
class MetadataDriver {
public:
    virtual ~MetadataDriver() = default;
    virtual ExperimentalMetadata::Result start(MetadataSession&) = 0;
    virtual ExperimentalMetadata::Result poll() = 0;
    virtual bool stop() = 0;
};
MetadataDriver* createMetadataDriver();
} }
#endif
