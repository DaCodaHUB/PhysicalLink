#pragma once
#include <cstdint>
#include <cstdio>
namespace physical_link {
// Internal platform seam; output includes terminating NUL. Never change this mapping.
inline void formatBoardDeviceId(std::uint64_t mac, char (&output)[37]) {
    std::snprintf(output, sizeof(output), "504c0000-0000-4000-8000-%012llx",
                  static_cast<unsigned long long>(mac & 0xffffffffffffULL));
}
void boardDeviceId(char (&output)[37]);
std::uint64_t freshEpoch();
}
