#include "MetadataSession.h"
#include <cstdio>
#include <cstring>
#include <limits>
#include <string>
#include <vector>
using namespace physical_link;
using namespace physical_link::protocol;
using A = MetadataSession::Access;
using Buffer = std::array<std::uint8_t, MaxPageSize>;
const char* deviceId = "710abdfc-9d05-46de-a790-7fe484816eea";
constexpr std::uint64_t epoch = UINT64_C(0xb398ca802d871ae0);
#define CHECK(x) do { if (!(x)) { std::fprintf(stderr,"%d: %s\n",__LINE__,#x); return false; } } while(false)
bool prepare(MetadataSession& session, int count = 16, std::uint32_t now = 0) {
    SensorRegistry registry;
    for (int i=0; i<count; ++i) CHECK(registry.addSensor(("sensor."+std::to_string(i)).c_str(),
        ("Sensor "+std::to_string(i)).c_str(),"C")==SensorRegistry::Result::Ok);
    CHECK(session.prepare(registry,deviceId,"Garden",epoch,now)==Status::Ok);
    return true;
}
A capture(MetadataSession& session, std::uint16_t request, std::uint16_t handle=7) {
    const std::uint8_t bytes[]={2,static_cast<std::uint8_t>(request),static_cast<std::uint8_t>(request>>8)};
    return session.command(handle,bytes,3);
}
bool image(MetadataSession& session, Image& out, std::uint16_t handle=7) {
    Buffer bytes{}; std::size_t length=0;
    CHECK(session.readState(handle,0,bytes.data(),bytes.size(),length)==A::Ok);
    CHECK(length<=MaxImageSize);
    CHECK(Codec::decodeImage(bytes.data(),length,out)==Status::Ok);
    CHECK(length==ImageHeaderSize+RecordSize*out.count);
    return true;
}
bool initial() {
    for (int count=1; count<=16; ++count) {
        MetadataSession session; CHECK(prepare(session,count));
        CHECK(session.update("sensor.0",12,true,10)==Status::Ok);
        CHECK(session.connect(7)); Image out; CHECK(image(session,out));
        CHECK(out.count==count && out.requestId==0 && out.epoch==epoch);
        for(int i=0;i<count;++i) CHECK(!out.records[i].available && out.records[i].sequence==0 &&
            out.records[i].age==UnavailableAge && out.records[i].value==0);
        MetadataSession::Notification notification;
        CHECK(!session.notification(10,notification));
        session.subscribe(8,true); CHECK(!session.notification(10,notification));
        session.subscribe(7,true); CHECK(session.notification(10,notification));
        CHECK(notification.bytes.size()==20);
        Record record; CHECK(Codec::decodeRecord(notification.bytes.data(),20,count,record)==Status::Ok);
        CHECK(record.sequence==1 && record.value==12 && record.age==0);
    }
    return true;
}
bool consistency() {
    MetadataSession session; CHECK(prepare(session)); CHECK(session.connect(7)); session.subscribe(7,true);
    CHECK(session.update("sensor.0",23.5,true,10)==Status::Ok);
    CHECK(session.update("sensor.1",45,true,20)==Status::Ok);
    CHECK(capture(session,1)==A::Ok); Image old; CHECK(image(session,old) && old.requestId==0);
    CHECK(session.poll(100)); Image out; CHECK(image(session,out));
    CHECK(out.requestId==1 && out.records[0].age==90 && out.records[1].age==80);
    CHECK(out.records[0].value==23.5 && !out.records[2].available);
    CHECK(session.update("sensor.0",99,true,110)==Status::Ok);
    MetadataSession::Notification notification; CHECK(session.notification(120,notification)); session.sent(notification);
    CHECK(image(session,old) && old.requestId==1 && old.records[0].value==23.5 && old.records[0].age==90);
    CHECK(capture(session,257)==A::Ok && session.poll(130));
    CHECK(image(session,out) && out.requestId==257 && out.records[0].sequence==2 && out.records[0].age==20);
    return true;
}
bool commands() {
    const std::vector<std::vector<std::uint8_t>> invalid={ {},{0},{1},{1,0},{1,2},{1,1,0},
        {2},{2,1},{2,0,0},{2,2,0},{2,1,0,0},{3,1,0} };
    for(const auto& bytes:invalid) {
        MetadataSession session; CHECK(prepare(session)); CHECK(session.connect(7)); session.subscribe(7,true);
        CHECK(session.command(7,bytes.data(),bytes.size())==A::InvalidCommand && session.faulted());
        CHECK(!session.poll()); Image out; CHECK(image(session,out) && out.requestId==0);
    }
    for(const auto request:{std::uint16_t(0),std::uint16_t(1),std::uint16_t(256)}) {
        MetadataSession session; CHECK(prepare(session)); CHECK(session.connect(7)); session.subscribe(7,true);
        CHECK(capture(session,1)==A::Ok && session.poll()); Image out; CHECK(image(session,out));
        CHECK(capture(session,257)==A::Ok && session.poll()); CHECK(image(session,out));
        CHECK(capture(session,request)==A::InvalidCommand && session.faulted());
    }
    MetadataSession session; CHECK(prepare(session)); CHECK(session.connect(7)); session.subscribe(7,true);
    CHECK(capture(session,1,8)==A::WrongConnection && !session.faulted());
    CHECK(capture(session,1)==A::Ok && session.poll()); Image out; CHECK(image(session,out));
    CHECK(capture(session,65535)==A::Ok && session.poll()); CHECK(image(session,out) && out.requestId==65535);
    CHECK(capture(session,1)==A::InvalidCommand);
    session.disconnect(7); CHECK(session.connect(7)); session.subscribe(7,true);
    CHECK(capture(session,1)==A::Ok);
    const std::uint8_t page[]={1,1}; CHECK(session.command(7,page,2)==A::Busy && !session.poll());
    return true;
}
bool subscription() {
    MetadataSession session; CHECK(prepare(session)); CHECK(session.connect(7));
    CHECK(capture(session,1)==A::InvalidCommand && session.faulted());
    session.disconnect(7); CHECK(session.connect(7)); session.subscribe(7,true);
    CHECK(capture(session,1)==A::Ok); session.subscribe(7,false);
    CHECK(!session.poll() && session.faulted());
    session.disconnect(7); CHECK(session.connect(7)); session.subscribe(7,true);
    CHECK(capture(session,1)==A::Ok && session.poll());
    Buffer bytes{}; std::size_t length=0;
    CHECK(session.read(7,0,bytes.data(),bytes.size(),length)==A::Ok);
    CHECK(capture(session,2)==A::Busy); // Metadata read cannot acknowledge capture.
    session.disconnect(7); CHECK(session.connect(7)); session.subscribe(7,true);
    const std::uint8_t page[]={1,1}; CHECK(session.command(7,page,2)==A::Ok && session.poll());
    Image out; CHECK(image(session,out)); CHECK(capture(session,1)==A::Busy); // Vice versa.
    return true;
}
bool pinning() {
    MetadataSession session; CHECK(prepare(session)); CHECK(session.connect(7)); session.subscribe(7,true);
    Buffer zero{},one{},copy{}; std::size_t length=0,n=0;
    CHECK(session.readState(7,22,copy.data(),copy.size(),n)==A::InvalidOffset);
    CHECK(session.readState(7,0,zero.data(),zero.size(),length)==A::Ok && length==336);
    CHECK(session.readState(7,0,copy.data(),1,n)==A::BufferTooSmall);
    CHECK(session.update("sensor.0",10,true,0)==Status::Ok);
    CHECK(capture(session,1)==A::Ok && session.poll());
    for(std::size_t offset=1;offset<=length;++offset) {
        CHECK(session.readState(7,offset,copy.data(),copy.size(),n)==A::Ok && n==length);
        CHECK(std::memcmp(copy.data(),zero.data(),length)==0);
    }
    CHECK(session.readState(7,length+1,copy.data(),copy.size(),n)==A::InvalidOffset);
    CHECK(session.readState(7,0,one.data(),one.size(),length)==A::Ok && one!=zero);
    CHECK(session.update("sensor.0",20,true,0)==Status::Ok);
    CHECK(capture(session,2)==A::Ok && session.poll());
    CHECK(session.readState(7,22,copy.data(),copy.size(),n)==A::Ok && copy==one);
    CHECK(session.readState(7,0,copy.data(),copy.size(),n)==A::Ok && copy!=one);
    // Unsupported overlapping commands cannot cycle into still-pinned storage.
    CHECK(capture(session,3)==A::Ok && session.poll());
    CHECK(capture(session,4)==A::Busy && session.faulted());
    return true;
}
bool coalescing() {
    MetadataSession session; CHECK(prepare(session)); CHECK(session.connect(7)); session.subscribe(7,true);
    for(int sequence=1;sequence<=100;++sequence) for(int channel=0;channel<16;++channel)
        CHECK(session.update(("sensor."+std::to_string(channel)).c_str(),sequence,true,0)==Status::Ok);
    for(int channel=0;channel<16;++channel) {
        MetadataSession::Notification next; CHECK(session.notification(channel*50,next));
        CHECK(next.channel==channel && next.sequence==100 && next.bytes.size()==20);
        Record record; CHECK(Codec::decodeRecord(next.bytes.data(),20,16,record)==Status::Ok);
        CHECK(record.value==100 && record.age==static_cast<std::uint32_t>(channel*50));
        session.sent(next);
    }
    MetadataSession::Notification next; CHECK(!session.notification(800,next));
    CHECK(session.update("sensor.0",std::numeric_limits<double>::quiet_NaN(),true,800)==Status::InvalidValue);
    CHECK(session.update("missing",1,true,800)==Status::UnknownSensor);
    CHECK(session.notification(800,next) && next.sequence==101); session.sent(next);
    Record record; CHECK(Codec::decodeRecord(next.bytes.data(),20,16,record)==Status::Ok);
    CHECK(!record.available && record.age==UnavailableAge && record.value==0);
    CHECK(session.update("sensor.0",25,true,850)==Status::Ok);
    CHECK(session.notification(850,next) && next.sequence==102); // Failed send: do not acknowledge.
    CHECK(session.update("sensor.0",26,true,900)==Status::Ok);
    CHECK(session.notification(900,next) && next.sequence==103); session.sent(next);
    CHECK(!session.notification(950,next));
    CHECK(session.update("sensor.0",27,true,950)==Status::Ok);
    CHECK(session.notification(950,next) && next.sequence==104);
    CHECK(session.update("sensor.0",28,true,951)==Status::Ok);
    session.sent(next); // Acquisition raced with host send: acknowledge only 104.
    CHECK(session.notification(1000,next) && next.sequence==105);
    session.sent(next);
    return true;
}
bool rate() {
    for(const auto start:{std::uint32_t(0),std::uint32_t(0xfffffff0u)}) {
        MetadataSession session; CHECK(prepare(session,16,start)); CHECK(session.connect(7)); session.subscribe(7,true);
        int attempts=0; std::uint32_t previous=0;
        for(std::uint32_t elapsed=0;elapsed<2000;++elapsed) {
            const auto now=start+elapsed;
            CHECK(session.update("sensor.0",elapsed,true,now)==Status::Ok);
            MetadataSession::Notification next;
            for(int poll=0;poll<10;++poll) if(session.notification(now,next)) {
                if(attempts) CHECK(elapsed-previous>=50);
                previous=elapsed; ++attempts;
                // Deliberate send failures consume the same budget.
            }
            if(elapsed==1000) {session.disconnect(7); CHECK(session.connect(7)); session.subscribe(7,true);}
        }
        CHECK(attempts==40);
        MetadataSession::Notification next;
        CHECK(session.notification(start+10000,next));
        CHECK(!session.notification(start+10000,next)); // Slow polling never earns burst credit.
    }
    return true;
}
bool reconnect() {
    MetadataSession session; CHECK(prepare(session)); CHECK(session.connect(7)); session.subscribe(7,true);
    CHECK(session.update("sensor.0",25,true,10)==Status::Ok);
    CHECK(capture(session,1)==A::Ok); session.disconnect(7);
    CHECK(!session.poll(20)); CHECK(session.connect(7));
    Image out; CHECK(image(session,out) && out.requestId==0 && out.records[0].sequence==0);
    MetadataSession::Notification next; CHECK(!session.notification(100,next));
    session.subscribe(7,true); CHECK(capture(session,1)==A::Ok && session.poll(100));
    CHECK(image(session,out) && out.records[0].sequence==1 && out.records[0].age==90);
    CHECK(session.notification(100,next)); session.sent(next);
    session.disconnect(7); CHECK(session.connect(7)); session.subscribe(7,true);
    CHECK(capture(session,1)==A::Ok && session.poll(150));
    session.disconnect(7); CHECK(session.connect(7));
    Buffer bytes{}; std::size_t length=0;
    CHECK(session.readState(7,22,bytes.data(),bytes.size(),length)==A::InvalidOffset);
    session.disconnect(7); session.reset(); CHECK(prepare(session,1,200)); CHECK(session.connect(7));
    CHECK(image(session,out) && out.count==1 && out.requestId==0);
    CHECK(session.update("sensor.0",1,true,199)==Status::InvalidTime && session.faulted());
    CHECK(!session.notification(200,next));
    return true;
}
int main(int argc,char** argv) {
    if(argc!=2)return 2;
    const std::string name=argv[1];
    if(name=="initial")return initial()?0:1;
    if(name=="consistency")return consistency()?0:1;
    if(name=="commands")return commands()?0:1;
    if(name=="subscription")return subscription()?0:1;
    if(name=="pinning")return pinning()?0:1;
    if(name=="coalescing")return coalescing()?0:1;
    if(name=="rate")return rate()?0:1;
    if(name=="reconnect")return reconnect()?0:1;
    return 2;
}
