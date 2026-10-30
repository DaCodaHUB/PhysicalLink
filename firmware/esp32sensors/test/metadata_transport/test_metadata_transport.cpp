#include "MetadataSession.h"
#include "BleDriver.h"
#include <array>
#include <cstdio>
#include <cstring>
#include <string>
using namespace physical_link;
using namespace physical_link::protocol;
using A = MetadataSession::Access;
using R = BleTransport::Result;
#define CHECK(x) do { if(!(x)){std::fprintf(stderr,"%d: %s\n",__LINE__,#x);return false;} }while(false)
const char* id="710abdfc-9d05-46de-a790-7fe484816eea";
const std::uint64_t epoch=UINT64_C(0xb398ca802d871ae0);
using Buffer=std::array<std::uint8_t,512>;
bool registry(SensorRegistry& sensors,int count) {
    if(count==1)return sensors.addSensor("temperature.indoor","Indoor","C")==SensorRegistry::Result::Ok;
    for(int i=0;i<count;++i)CHECK(sensors.addSensor(("sensor."+std::to_string(i)).c_str(),("Sensor "+std::to_string(i)).c_str(),"raw")==SensorRegistry::Result::Ok);
    return true;
}
bool single() {
    MetadataSession session;SensorRegistry sensors;CHECK(registry(sensors,1));
    Buffer output{};std::size_t length=123;
    CHECK(session.read(1,0,output.data(),512,length)==A::NotReady && length==123);
    CHECK(!session.connect(1));
    CHECK(session.prepare(sensors,id,"Garden",epoch)==Status::Ok);
    CHECK(session.prepare(sensors,id,"Garden",epoch)==Status::InvalidConfiguration);
    CHECK(session.connect(1) && !session.connect(2));
    CHECK(session.read(2,0,output.data(),512,length)==A::WrongConnection);
    CHECK(session.read(1,22,output.data(),512,length)==A::InvalidOffset);
    CHECK(session.read(1,0,output.data(),1,length)==A::BufferTooSmall && length==123);
    CHECK(session.read(1,0,output.data(),512,length)==A::Ok && length==191);
    PageView page;CHECK(Metadata::decodePage(output.data(),length,page)==Status::Ok && page.index==0 && page.count==1);
    CHECK(sensors.addSensor("later","Ignored","")==SensorRegistry::Result::Ok);
    Buffer again{};std::size_t second=0;
    CHECK(session.read(1,0,again.data(),512,second)==A::Ok && second==length && output==again);
    session.disconnect(2);CHECK(session.connected());
    session.disconnect(1);CHECK(!session.connected());
    CHECK(session.read(1,0,again.data(),512,second)==A::WrongConnection);
    session.reset();CHECK(!session.connect(1));
    return true;
}
bool pinning() {
    SensorRegistry sensors;CHECK(registry(sensors,16));MetadataSession session;
    CHECK(session.prepare(sensors,id,"Garden",epoch)==Status::Ok && session.connect(7));
    Buffer zero{},one{},old{};std::size_t z=0,o=0,n=0;
    CHECK(session.read(7,0,zero.data(),512,z)==A::Ok && z==512);
    std::uint8_t command[]={1,1};
    CHECK(session.command(7,command,2)==A::Ok);
    command[1]=0; // Callback input is borrowed; mailbox must own its copy.
    CHECK(session.read(7,0,old.data(),512,n)==A::Ok && old==zero);
    CHECK(session.poll() && session.selectedPage()==1);
    CHECK(!session.poll());
    // Model ble_gatts_val_access slicing AFTER the raw access callback. Its
    // callback gets real offsets; our callback must supply the full generation.
    std::string reconstructed(reinterpret_cast<const char*>(zero.data()),22);
    for(std::size_t offset=22;offset<z;offset+=22) {
        CHECK(session.read(7,offset,old.data(),512,n)==A::Ok && n==z && old==zero);
        const auto take=z-offset>22?22:z-offset;
        reconstructed.append(reinterpret_cast<const char*>(old.data()+offset),take);
    }
    CHECK(reconstructed==std::string(reinterpret_cast<const char*>(zero.data()),z));
    for(std::size_t offset=1;offset<=z;++offset) {
        CHECK(session.read(7,offset,old.data(),512,n)==A::Ok && n==z && old==zero);
    }
    CHECK(session.read(7,z,old.data(),512,n)==A::Ok); // Empty final blob is legal.
    CHECK(session.read(7,z+1,old.data(),512,n)==A::InvalidOffset);
    CHECK(session.read(7,0,one.data(),512,o)==A::Ok && one[3]==1 && o==436);
    CHECK(session.command(7,command,2)==A::Ok && session.poll());
    CHECK(session.read(7,22,old.data(),512,n)==A::Ok && n==o);
    CHECK(std::memcmp(old.data(),one.data(),o)==0);
    CHECK(session.read(7,0,old.data(),512,n)==A::Ok && n==z && old==zero);
    return true;
}
bool commands() {
    SensorRegistry sensors;CHECK(registry(sensors,16));
    const std::uint8_t valid[]={1,1};
    const std::uint8_t bad[][3]={{2,1,0},{1,2,0},{1,0,0},{0,1,0}};
    for(const auto& command:bad) {
        MetadataSession session;CHECK(session.prepare(sensors,id,"Garden",epoch)==Status::Ok && session.connect(1));
        CHECK(session.command(1,command,2)==A::InvalidCommand && session.faulted());
        CHECK(!session.poll() && session.selectedPage()==0);
    }
    for(std::size_t length:{std::size_t(0),std::size_t(1),std::size_t(3),std::size_t(512)}) {
        MetadataSession session;CHECK(session.prepare(sensors,id,"Garden",epoch)==Status::Ok && session.connect(1));
        CHECK(session.command(1,valid,length)==A::InvalidCommand && !session.poll());
    }
    MetadataSession session;CHECK(session.prepare(sensors,id,"Garden",epoch)==Status::Ok && session.connect(1));
    CHECK(session.command(2,valid,2)==A::WrongConnection && !session.faulted());
    CHECK(session.command(1,valid,2)==A::Ok);
    CHECK(session.command(1,valid,2)==A::Busy && session.faulted());
    CHECK(!session.poll() && session.selectedPage()==0);
    session.disconnect(1);CHECK(session.connect(1)); // Recycled handle, new state.
    CHECK(session.command(1,valid,2)==A::Ok && session.poll());
    const std::uint8_t back[]={1,0};
    CHECK(session.command(1,back,2)==A::Busy); // No matching page read yet.
    session.disconnect(1);CHECK(session.connect(9));
    CHECK(session.selectedPage()==0 && !session.faulted());
    CHECK(session.command(9,valid,2)==A::Ok);
    session.disconnect(9);CHECK(!session.poll());
    CHECK(session.connect(9) && session.selectedPage()==0 && !session.poll());
    return true;
}
int starts=0,stops=0,destroyed=0;
bool startWorks=true,stopWorks=true,allocate=true;
MetadataSession* bound=nullptr;
class Driver : public MetadataDriver {
public:
    ~Driver()override{++destroyed;bound=nullptr;}
    R start(MetadataSession& session)override{++starts;bound=&session;session.connect(7);return startWorks?R::Ok:R::StartFailed;}
    R poll()override{bound->poll();return R::Ok;}
    Status update(const char* sensor,double value,bool available)override{return bound->update(sensor,value,available,0);}
    bool stop()override{++stops;return stopWorks;}
};
bool lifecycle() {
    SensorRegistry empty,sensors;CHECK(registry(sensors,16));
    BleTransport transport;
    CHECK(transport.poll()==R::NotRunning);
    CHECK(transport.begin(empty,id,"Garden",epoch)==R::InvalidMetadata && starts==0);
    allocate=false;CHECK(transport.begin(sensors,id,"Garden",epoch)==R::OutOfMemory);allocate=true;
    startWorks=false;
    CHECK(transport.begin(sensors,id,"Garden",epoch)==R::StartFailed && stops==1 && destroyed==1);
    startWorks=true;
    CHECK(transport.begin(sensors,id,"Garden",epoch)==R::Ok && transport.isRunning());
    CHECK(transport.begin(sensors,id,"Garden",epoch)==R::AlreadyRunning);
    BleTransport other;
    CHECK(other.begin(sensors,id,"Garden",epoch)==R::Busy);
    const std::uint8_t command[]={1,1};CHECK(bound->command(7,command,2)==A::Ok);
    CHECK(transport.poll()==R::Ok && bound->selectedPage()==1);
    stopWorks=false;
    CHECK(transport.end()==R::StopFailed && !transport.isRunning());
    CHECK(other.begin(sensors,id,"Garden",epoch)==R::Busy);
    stopWorks=true;CHECK(transport.end()==R::Ok && bound==nullptr);
    const auto count=stops;CHECK(transport.end()==R::Ok && stops==count);
    CHECK(other.begin(sensors,id,"Garden",epoch)==R::Ok && bound->selectedPage()==0);
    return true;
}
bool teardown() {
    SensorRegistry sensors;CHECK(registry(sensors,1));
    {BleTransport transport;CHECK(transport.begin(sensors,id,"Garden",epoch)==R::Ok);}
    CHECK(stops==1 && destroyed==1 && bound==nullptr);
    BleTransport next;CHECK(next.begin(sensors,id,"Garden",epoch)==R::Ok);
    return true;
}
bool emit() {
    SensorRegistry sensors;CHECK(registry(sensors,16));MetadataSession session;
    CHECK(session.prepare(sensors,id,"Garden",epoch)==Status::Ok && session.connect(1));
    for(int i=0;i<2;++i) {
        if(i) {const std::uint8_t command[]={1,1};CHECK(session.command(1,command,2)==A::Ok && session.poll());}
        Buffer bytes{};std::size_t length=0;CHECK(session.read(1,0,bytes.data(),512,length)==A::Ok);
        std::printf("page.sixteen.%d\t",i);for(std::size_t j=0;j<length;++j)std::printf("%02x",bytes[j]);std::puts("");
    }
    return true;
}
namespace physical_link {namespace protocol {MetadataDriver* createMetadataDriver(){return allocate?new Driver:nullptr;} std::uint32_t metadataMillis(){return 0;}}}
int main(int argc,char** argv) {
    if(argc!=2)return 2;
    const std::string name=argv[1];
    if(name=="single")return single()?0:1;
    if(name=="pinning")return pinning()?0:1;
    if(name=="commands")return commands()?0:1;
    if(name=="lifecycle")return lifecycle()?0:1;
    if(name=="teardown")return teardown()?0:1;
    if(name=="emit")return emit()?0:1;
    return 2;
}
