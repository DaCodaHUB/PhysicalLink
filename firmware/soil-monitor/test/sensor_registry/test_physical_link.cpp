#include "PhysicalLink.h"
#include "LegacyBleTransport.h"
#include <cstdio>
#include <cstring>
#include <limits>
#include <string>
#include <vector>

#define CHECK(x) do { if (!(x)) { std::fprintf(stderr, "%d: %s\n", __LINE__, #x); return false; } } while (false)
namespace {
struct Publication { std::size_t index; std::string text; bool notify; };
int starts = 0, stops = 0, destroyed = 0;
bool stopWorks = true, publishWorks = true;
PLResult startResult = PLResult::Ok;
std::string metadata;
std::vector<Publication> publications;
class FakeTransport : public physical_link::LegacyBleTransport {
public:
    ~FakeTransport() override { ++destroyed; }
    PLResult start(const physical_link::SensorRegistry&, const char* json) override {
        ++starts; metadata = json; return startResult;
    }
    bool publish(std::size_t index, const std::string& text, bool notify) override {
        publications.push_back({index, text, notify}); return publishWorks;
    }
    bool stop() override { ++stops; return stopWorks; }
};
bool profile(PhysicalLink& device) {
    CHECK(device.addSensor("soil", "Soil", "raw") == PLResult::Ok);
    CHECK(device.addSensor("temperature", "Temperature", "C") == PLResult::Ok);
    CHECK(device.addSensor("humidity", "Humidity", "%") == PLResult::Ok);
    CHECK(device.addSensor("pressure", "Pressure", "hPa") == PLResult::Ok);
    return true;
}
bool registration() {
    PhysicalLink device("Garden");
    CHECK(!device.isRunning());
    CHECK(device.addSensor(nullptr, "Bad", "C") == PLResult::InvalidId);
    CHECK(device.addSensor("Upper", nullptr, "C") == PLResult::InvalidId);
    CHECK(device.addSensor("soil", std::string(65, 'x').c_str(), "raw") == PLResult::InvalidName);
    CHECK(device.addSensor("soil", nullptr, std::string(17, 'x').c_str()) == PLResult::InvalidUnit);
    CHECK(profile(device));
    CHECK(device.addSensor("soil", nullptr, "raw") == PLResult::DuplicateId);
    CHECK(starts == 0);
    CHECK(device.begin() == PLResult::Ok);
    CHECK(metadata.size() == 298);
    CHECK(metadata.find("PhysicalLink Soil") != std::string::npos);
    CHECK(metadata.find("Garden") == std::string::npos);
    CHECK(device.addSensor("light", nullptr, "lux") == PLResult::Busy);
    CHECK(device.begin() == PLResult::AlreadyRunning && starts == 1);
    PhysicalLink other("Other"); CHECK(profile(other));
    CHECK(other.begin() == PLResult::Busy);
    CHECK(other.end() == PLResult::Ok && device.isRunning());
    CHECK(device.end() == PLResult::Ok && !device.isRunning());
    const int oldStops = stops;
    CHECK(device.end() == PLResult::Ok && stops == oldStops);
    CHECK(other.begin() == PLResult::Ok);
    return true;
}
bool validation() {
    const char* badNames[] = {nullptr, "", "\xc0\xaf"};
    for (const auto* name : badNames) {
        PhysicalLink device(name); CHECK(profile(device));
        CHECK(device.begin() == PLResult::InvalidProjectName);
    }
    PhysicalLink longName(std::string(65, 'x').c_str()); CHECK(profile(longName));
    CHECK(longName.begin() == PLResult::InvalidProjectName);
    PhysicalLink empty("Empty");
    CHECK(empty.begin() == PLResult::UnsupportedProfile);
    CHECK(empty.addSensor("soil", nullptr, "raw") == PLResult::Ok);
    CHECK(empty.begin() == PLResult::UnsupportedProfile);
    PhysicalLink wrong("Wrong");
    CHECK(wrong.addSensor("soil", nullptr, "percent") == PLResult::Ok);
    CHECK(wrong.addSensor("temperature", nullptr, "C") == PLResult::Ok);
    CHECK(wrong.addSensor("humidity", nullptr, "%") == PLResult::Ok);
    CHECK(wrong.addSensor("pressure", nullptr, "hPa") == PLResult::Ok);
    CHECK(wrong.begin() == PLResult::UnsupportedProfile);
    PhysicalLink extra("Extra"); CHECK(profile(extra));
    CHECK(extra.addSensor("light", nullptr, "lux") == PLResult::Ok);
    CHECK(extra.begin() == PLResult::UnsupportedProfile);
    CHECK(starts == 0);
    return true;
}
bool readings() {
    PhysicalLink device("Garden"); CHECK(profile(device));
    CHECK(device.update("soil", 1) == PLResult::NotRunning);
    CHECK(device.unavailable("soil") == PLResult::NotRunning);
    CHECK(device.poll() == PLResult::NotRunning);
    CHECK(device.begin() == PLResult::Ok);
    CHECK(device.poll() == PLResult::Ok && publications.empty());
    CHECK(device.update("missing", 1) == PLResult::UnknownSensor);
    CHECK(device.unavailable("missing") == PLResult::UnknownSensor);
    CHECK(device.update(nullptr, 1) == PLResult::InvalidId);
    CHECK(device.update("soil", 2048) == PLResult::Ok);
    CHECK(device.update("temperature", -12.5) == PLResult::Ok);
    CHECK(publications.empty());
    CHECK(device.poll() == PLResult::Ok && publications.size() == 2);
    CHECK(publications[0].index == 0 && publications[0].text == "2048" && publications[0].notify);
    CHECK(publications[1].index == 1 && publications[1].text == "-12.500000");
    publications.clear();
    CHECK(device.poll() == PLResult::Ok && publications.empty());
    CHECK(device.unavailable("soil") == PLResult::Ok);
    CHECK(device.poll() == PLResult::Ok && publications.size() == 1);
    CHECK(publications.back().text.empty() && !publications.back().notify);
    CHECK(device.update("soil", 0) == PLResult::Ok);
    CHECK(device.poll() == PLResult::Ok && publications.back().text == "0" && publications.back().notify);
    const double bad[] = {std::numeric_limits<double>::quiet_NaN(),
        std::numeric_limits<double>::infinity(), -std::numeric_limits<double>::infinity(),
        std::numeric_limits<double>::max()};
    for (double value : bad) {
        CHECK(device.update("temperature", value) == PLResult::InvalidValue);
        CHECK(device.poll() == PLResult::Ok);
        CHECK(publications.back().text.empty() && !publications.back().notify);
        CHECK(device.update("temperature", 23.5) == PLResult::Ok);
        CHECK(device.poll() == PLResult::Ok && publications.back().text == "23.500000");
    }
    for (double value : {-1.0, 4096.0, 1.5}) CHECK(device.update("soil", value) == PLResult::InvalidValue);
    CHECK(device.end() == PLResult::Ok);
    CHECK(device.begin() == PLResult::Ok);
    publications.clear();
    CHECK(device.poll() == PLResult::Ok && publications.empty());
    CHECK(device.unavailable("temperature") == PLResult::Ok);
    CHECK(device.poll() == PLResult::Ok && publications.back().text.empty());
    return true;
}
bool failures() {
    PhysicalLink device("Test"); CHECK(profile(device));
    startResult = PLResult::BleStartFailed;
    CHECK(device.begin() == PLResult::BleStartFailed);
    CHECK(!device.isRunning() && stops == 1 && destroyed == 1);
    startResult = PLResult::Ok;
    CHECK(device.begin() == PLResult::Ok);
    CHECK(device.update("humidity", 50) == PLResult::Ok);
    publishWorks = false;
    CHECK(device.poll() == PLResult::SendFailed);
    publishWorks = true;
    CHECK(device.poll() == PLResult::Ok && publications.back().text == "50.000000");
    stopWorks = false;
    CHECK(device.end() == PLResult::BleStopFailed && !device.isRunning());
    CHECK(device.begin() == PLResult::Busy);
    PhysicalLink other("Other"); CHECK(profile(other));
    CHECK(other.begin() == PLResult::Busy);
    stopWorks = true;
    CHECK(device.end() == PLResult::Ok);
    CHECK(other.begin() == PLResult::Ok);
    return true;
}
bool cleanup() {
    { PhysicalLink device("Test"); CHECK(profile(device)); CHECK(device.begin() == PLResult::Ok); }
    CHECK(stops == 1 && destroyed == 1);
    PhysicalLink next("Next"); CHECK(profile(next)); CHECK(next.begin() == PLResult::Ok);
    return true;
}
bool order() {
    PhysicalLink device("Reverse");
    CHECK(device.addSensor("pressure", nullptr, "hPa") == PLResult::Ok);
    CHECK(device.addSensor("humidity", nullptr, "%") == PLResult::Ok);
    CHECK(device.addSensor("temperature", nullptr, "C") == PLResult::Ok);
    CHECK(device.addSensor("soil", nullptr, "raw") == PLResult::Ok);
    CHECK(device.begin() == PLResult::Ok);
    CHECK(metadata.find("pressure") < metadata.find("humidity"));
    CHECK(metadata.find("humidity") < metadata.find("temperature"));
    CHECK(device.update("soil", 42) == PLResult::Ok);
    CHECK(device.poll() == PLResult::Ok && publications.back().index == 3);
    return true;
}
}
namespace physical_link { LegacyBleTransport* createLegacyBleTransport() { return new FakeTransport; } }
int main(int argc, char** argv) {
    if (argc != 2) return 2;
    if (!std::strcmp(argv[1], "registration")) return registration() ? 0 : 1;
    if (!std::strcmp(argv[1], "validation")) return validation() ? 0 : 1;
    if (!std::strcmp(argv[1], "readings")) return readings() ? 0 : 1;
    if (!std::strcmp(argv[1], "failures")) return failures() ? 0 : 1;
    if (!std::strcmp(argv[1], "cleanup")) return cleanup() ? 0 : 1;
    if (!std::strcmp(argv[1], "order")) return order() ? 0 : 1;
    return 2;
}
