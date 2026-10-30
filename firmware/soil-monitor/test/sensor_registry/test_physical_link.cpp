#include "PhysicalLink.h"
#include "BleDriver.h"
#include "DeviceIdentity.h"
#include <cstdio>
#include <cstring>
#include <limits>
#include <string>
using namespace physical_link;
using namespace physical_link::protocol;
using R = BleTransport::Result;
#define CHECK(x) do { if (!(x)) { std::fprintf(stderr,"%d: %s\n",__LINE__,#x); return false; } } while(false)
namespace {
MetadataSession* session = nullptr;
bool allocate = true, startWorks = true, stopWorks = true;
int starts = 0, stops = 0;
std::uint64_t epoch = 10;
std::uint32_t clockMs = 0;
class Driver : public MetadataDriver {
public:
    R start(MetadataSession& s) override { ++starts; session = &s; return startWorks ? R::Ok : R::StartFailed; }
    R poll() override { session->poll(clockMs); return R::Ok; }
    Status update(const char* id, double value, bool valid) override { return session->update(id, value, valid, clockMs); }
    bool stop() override { ++stops; if (stopWorks) session = nullptr; return stopWorks; }
};
bool registration() {
    PhysicalLink empty("Garden");
    CHECK(empty.begin() == PLResult::InvalidMetadata && starts == 0);
    PhysicalLink invalid(nullptr); CHECK(invalid.begin() == PLResult::InvalidProjectName);
    PhysicalLink badUtf8("\xc0\x80"); CHECK(badUtf8.begin() == PLResult::InvalidProjectName);
    PhysicalLink tooLong(std::string(65, 'x').c_str()); CHECK(tooLong.begin() == PLResult::InvalidProjectName);
    PhysicalLink d("Garden");
    CHECK(d.addSensor("Temperature", "Temperature", "C") == PLResult::InvalidId);
    CHECK(d.addSensor("temperature", "Temperature", "C") == PLResult::Ok);
    CHECK(d.addSensor("temperature", "Other", "C") == PLResult::DuplicateId);
    CHECK(d.addSensor("bad", "\xc0\x80", "") == PLResult::InvalidName);
    CHECK(d.addSensor("bad", "Name", "\xc0\x80") == PLResult::InvalidUnit);
    for (int i = 1; i < 16; ++i)
        CHECK(d.addSensor(("sensor." + std::to_string(i)).c_str(), "Sensor", "raw") == PLResult::Ok);
    CHECK(d.addSensor("extra", "Extra", "") == PLResult::CapacityExceeded);
    CHECK(d.begin() == PLResult::Ok);
    CHECK(d.addSensor("later", "Later", "") == PLResult::Busy);
    CHECK(d.begin() == PLResult::AlreadyRunning);
    return true;
}
bool readings() {
    PhysicalLink d("Garden");
    CHECK(d.update("x", 1) == PLResult::NotRunning && d.poll() == PLResult::NotRunning);
    CHECK(d.addSensor("arbitrary.channel", "Custom", "m/s") == PLResult::Ok);
    CHECK(d.begin() == PLResult::Ok && session->connect(7));
    session->subscribe(7, true);
    MetadataSession::Notification n;
    CHECK(session->notification(0, n));
    Record r; CHECK(Codec::decodeRecord(n.bytes.data(), n.bytes.size(), 1, r) == Status::Ok && !r.available);
    session->sent(n);
    CHECK(d.update("missing", 1) == PLResult::UnknownSensor);
    CHECK(d.update("UPPER", 1) == PLResult::InvalidId);
    CHECK(d.update("arbitrary.channel", -12345.75) == PLResult::Ok);
    CHECK(session->notification(50, n));
    CHECK(Codec::decodeRecord(n.bytes.data(), n.bytes.size(), 1, r) == Status::Ok && r.available && r.value == -12345.75);
    session->sent(n);
    clockMs = 50;
    CHECK(d.update("arbitrary.channel", std::numeric_limits<double>::quiet_NaN()) == PLResult::InvalidValue);
    CHECK(session->notification(100, n));
    CHECK(Codec::decodeRecord(n.bytes.data(), n.bytes.size(), 1, r) == Status::Ok && !r.available && r.value == 0);
    session->sent(n);
    clockMs = 100;
    CHECK(d.update("arbitrary.channel", 10) == PLResult::Ok);
    CHECK(d.unavailable("arbitrary.channel") == PLResult::Ok);
    CHECK(d.unavailable("missing") == PLResult::UnknownSensor);
    CHECK(d.update("arbitrary.channel", 25) == PLResult::Ok && d.poll() == PLResult::Ok);
    CHECK(session->notification(150, n));
    CHECK(Codec::decodeRecord(n.bytes.data(), n.bytes.size(), 1, r) == Status::Ok && r.available && r.value == 25);
    return true;
}
bool lifecycle() {
    PhysicalLink d("Garden"), other("Other");
    CHECK(d.addSensor("one", "One", "") == PLResult::Ok);
    CHECK(other.addSensor("one", "One", "") == PLResult::Ok);
    CHECK(d.begin() == PLResult::Ok && other.begin() == PLResult::Busy);
    CHECK(d.update("one", 12) == PLResult::Ok);
    stopWorks = false;
    CHECK(d.end() == PLResult::BleStopFailed && !d.isRunning());
    CHECK(d.begin() == PLResult::Busy && d.addSensor("two", "Two", "") == PLResult::Busy);
    CHECK(other.begin() == PLResult::Busy);
    stopWorks = true; CHECK(d.end() == PLResult::Ok && d.end() == PLResult::Ok);
    CHECK(d.begin() == PLResult::Ok && session->connect(7));
    session->subscribe(7, true); MetadataSession::Notification n;
    CHECK(session->notification(0, n)); Record r;
    CHECK(Codec::decodeRecord(n.bytes.data(), n.bytes.size(), 1, r) == Status::Ok && !r.available && r.sequence == 0);
    CHECK(d.end() == PLResult::Ok && other.begin() == PLResult::Ok);
    return true;
}
bool failures() {
    PhysicalLink d("Garden"); CHECK(d.addSensor("one", "One", "") == PLResult::Ok);
    allocate = false; CHECK(d.begin() == PLResult::OutOfMemory); allocate = true;
    startWorks = false; CHECK(d.begin() == PLResult::BleStartFailed && stops == 1);
    stopWorks = false; CHECK(d.begin() == PLResult::BleStopFailed);
    CHECK(d.begin() == PLResult::Busy);
    stopWorks = true; CHECK(d.end() == PLResult::Ok);
    startWorks = true; CHECK(d.begin() == PLResult::Ok);
    return true;
}
bool identity() {
    char id[37]; boardDeviceId(id); CHECK(std::strcmp(id,"504c0000-0000-4000-8000-112233445566") == 0);
    PhysicalLink d("Garden"); CHECK(d.addSensor("one", "One", "") == PLResult::Ok);
    CHECK(d.begin() == PLResult::Ok && session->connect(7));
    std::uint8_t bytes[512]; std::size_t length = 0;
    CHECK(session->read(7, 0, bytes, sizeof(bytes), length) == MetadataSession::Access::Ok);
    const std::string first(reinterpret_cast<char*>(bytes+16), length-16);
    CHECK(first.find(id) != std::string::npos && first.find("Garden") != std::string::npos);
    CHECK(d.end() == PLResult::Ok && d.begin() == PLResult::Ok && session->connect(7));
    CHECK(session->read(7, 0, bytes, sizeof(bytes), length) == MetadataSession::Access::Ok);
    const std::string second(reinterpret_cast<char*>(bytes+16), length-16);
    CHECK(second.find(id) != std::string::npos && first != second);
    return true;
}
}
namespace physical_link {
void boardDeviceId(char (&out)[37]) { formatBoardDeviceId(UINT64_C(0x112233445566), out); }
std::uint64_t freshEpoch() { return ++epoch; }
namespace protocol {
MetadataDriver* createMetadataDriver() { return allocate ? new Driver : nullptr; }
std::uint32_t metadataMillis() { return clockMs; }
}}
int main(int argc, char** argv) {
    if (argc != 2) return 2;
    const std::string name = argv[1];
    if (name == "registration") return registration() ? 0 : 1;
    if (name == "readings") return readings() ? 0 : 1;
    if (name == "lifecycle") return lifecycle() ? 0 : 1;
    if (name == "failures") return failures() ? 0 : 1;
    if (name == "identity") return identity() ? 0 : 1;
    return 2;
}
