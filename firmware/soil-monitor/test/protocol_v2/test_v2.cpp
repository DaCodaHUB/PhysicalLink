#include "V2Codec.h"
#include "V2Metadata.h"
#include "V2SensorState.h"
#include <array>
#include <cmath>
#include <cstdio>
#include <cstring>
#include <limits>
#include <string>
using namespace physical_link;
using namespace physical_link::v2;
#define CHECK(x) do { if (!(x)) { std::fprintf(stderr,"%d: %s\n",__LINE__,#x); return false; } } while(false)
const char* uuid = "710abdfc-9d05-46de-a790-7fe484816eea";
const std::uint64_t epoch = UINT64_C(0xb398ca802d871ae0);
using Bytes = std::array<std::uint8_t, 1024>;
std::string hex(const std::uint8_t* bytes, std::size_t size) {
    const char* digits = "0123456789abcdef";
    std::string result;
    for (std::size_t i=0;i<size;++i) { result+=digits[bytes[i]>>4]; result+=digits[bytes[i]&15]; }
    return result;
}
bool numeric() {
    Record record; record.available=true; record.sequence=42; record.age=10; record.value=23.5;
    Bytes bytes{};
    CHECK(Codec::encodeRecord(record,1,bytes.data(),bytes.size()).status==Status::Ok);
    CHECK(hex(bytes.data(),20)=="200000002a0000000a0000000000000000803740");
    Record decoded;
    CHECK(Codec::decodeRecord(bytes.data(),20,1,decoded)==Status::Ok && decoded.value==23.5);
    for (std::size_t cap=0;cap<20;++cap) {
        bytes.fill(0xa5);
        auto result=Codec::encodeRecord(record,1,bytes.data(),cap);
        CHECK(result.status==Status::BufferTooSmall && result.requiredCapacity==20 && !result.bytesWritten);
        for(auto b:bytes) CHECK(b==0xa5);
    }
    CHECK(Codec::encodeRecord(record,1,nullptr,20).status==Status::InvalidBuffer);
    CHECK(Codec::encodeRecord(record,1,nullptr,0).status==Status::BufferTooSmall);
    CHECK(Codec::encodeRecord(record,0,bytes.data(),20).status==Status::InvalidChannel);
    CHECK(Codec::encodeRecord(record,17,bytes.data(),20).status==Status::InvalidChannel);
    record.channel=1;
    CHECK(Codec::encodeRecord(record,1,bytes.data(),20).status==Status::InvalidChannel);
    record.channel=0;
    for(double value:{0.0,-0.0,-23.5,std::numeric_limits<double>::denorm_min(),
                      std::numeric_limits<double>::min(),std::numeric_limits<double>::max()}) {
        record.value=value;
        CHECK(Codec::encodeRecord(record,1,bytes.data(),20).status==Status::Ok);
        CHECK(Codec::decodeRecord(bytes.data(),20,1,decoded)==Status::Ok);
        CHECK(std::memcmp(&value,&decoded.value,8)==0);
    }
    for(double value:{std::numeric_limits<double>::infinity(),-std::numeric_limits<double>::infinity(),
                      std::numeric_limits<double>::quiet_NaN()}) {
        record.value=value;
        CHECK(Codec::encodeRecord(record,1,bytes.data(),20).status==Status::InvalidRecord);
    }
    record.value=1;record.sequence=0;
    CHECK(Codec::encodeRecord(record,1,bytes.data(),20).status==Status::InvalidRecord);
    record.sequence=1;record.age=UnavailableAge;
    CHECK(Codec::encodeRecord(record,1,bytes.data(),20).status==Status::InvalidRecord);
    record=Record{}; record.channel=1;record.sequence=7;
    CHECK(Codec::encodeRecord(record,2,bytes.data(),20).status==Status::Ok);
    CHECK(hex(bytes.data(),20)=="2001010007000000ffffffff0000000000000000");
    record.value=-0.0;
    CHECK(Codec::encodeRecord(record,2,bytes.data(),20).status==Status::InvalidRecord);
    return true;
}
bool corrupt() {
    Record r;r.available=true;r.sequence=1;r.age=0;r.value=1;
    Bytes good{};CHECK(Codec::encodeRecord(r,1,good.data(),20).status==Status::Ok);
    Record out;out.sequence=77;
    for(std::size_t len=0;len<40;++len) if(len!=20)
        CHECK(Codec::decodeRecord(good.data(),len,1,out)==Status::InvalidLength && out.sequence==77);
    CHECK(Codec::decodeRecord(nullptr,20,1,out)==Status::InvalidBuffer);
    for(auto offset:{0,1,2,3}) {
        auto bad=good;bad[static_cast<std::size_t>(offset)]=0xff;
        CHECK(Codec::decodeRecord(bad.data(),20,1,out)!=Status::Ok && out.sequence==77);
    }
    auto bad=good;for(int i=4;i<8;++i)bad[static_cast<std::size_t>(i)]=0;
    CHECK(Codec::decodeRecord(bad.data(),20,1,out)==Status::InvalidRecord);
    bad=good;for(int i=8;i<12;++i)bad[static_cast<std::size_t>(i)]=0xff;
    CHECK(Codec::decodeRecord(bad.data(),20,1,out)==Status::InvalidRecord);
    // +Inf, -Inf, quiet/signaling NaNs supplied as wire bits, not float literals.
    const char* values[]={"000000000000f07f","000000000000f0ff","000000000000f87f","010000000000f07f"};
    for(const auto* value:values) {
        bad=good;
        for(int i=0;i<8;++i) {unsigned int b=0;std::sscanf(value+i*2,"%2x",&b);bad[12+static_cast<std::size_t>(i)]=static_cast<std::uint8_t>(b);}
        CHECK(Codec::decodeRecord(bad.data(),20,1,out)==Status::InvalidRecord);
    }
    bad=good;bad[2]=1;for(int i=8;i<12;++i)bad[static_cast<std::size_t>(i)]=0xff;
    CHECK(Codec::decodeRecord(bad.data(),20,1,out)==Status::InvalidRecord);
    return true;
}
bool state() {
    SensorState cache;
    CHECK(cache.tick(0)==Status::NotStarted);
    CHECK(cache.start(epoch,0)==Status::InvalidConfiguration);
    CHECK(cache.addSensor("temperature.indoor","Indoor","C")==Status::Ok);
    CHECK(cache.addSensor("temperature.indoor",nullptr,"C")==Status::DuplicateId);
    CHECK(cache.addSensor("Bad",nullptr,"")==Status::InvalidId);
    CHECK(cache.start(epoch,0xfffffff0u)==Status::Ok);
    CHECK(cache.addSensor("extra",nullptr,"")==Status::RegistrationClosed);
    Record r;
    CHECK(cache.reading(0,0xfffffff0u,r)==Status::Ok && !r.available && r.sequence==0);
    CHECK(cache.update("missing",1,0)==Status::UnknownSensor);
    CHECK(cache.update("temperature.indoor",23.5,0xfffffff5u)==Status::Ok);
    CHECK(cache.reading(0,5,r)==Status::Ok && r.age==16 && r.sequence==1);
    CHECK(cache.reading(0,4,r)==Status::InvalidTime);
    CHECK(cache.update("temperature.indoor",23.5,6)==Status::Ok);
    CHECK(cache.reading(0,6,r)==Status::Ok && r.sequence==2 && r.age==0);
    CHECK(cache.update("temperature.indoor",std::numeric_limits<double>::quiet_NaN(),7)==Status::InvalidValue);
    CHECK(cache.reading(0,7,r)==Status::Ok && !r.available && r.sequence==3 && r.value==0 && r.age==UnavailableAge);
    CHECK(cache.registry().getSensor("temperature.indoor")->value==23.5);
    CHECK(cache.unavailable("temperature.indoor",8)==Status::Ok);
    CHECK(cache.update("temperature.indoor",-2,9)==Status::Ok);
    CHECK(cache.reading(0,9,r)==Status::Ok && r.available && r.sequence==5);
    Image image;
    CHECK(cache.capture(1,10,image)==Status::Ok && image.records[0].age==1 && image.epoch==epoch);
    CHECK(cache.capture(0,10,image)==Status::InvalidImage);
    CHECK(cache.start(epoch,10)==Status::InvalidConfiguration);
    CHECK(cache.start(epoch+1,0)==Status::Ok);
    CHECK(cache.capture(0,0,image)==Status::Ok && image.records[0].sequence==0);
    CHECK(cache.update("temperature.indoor",1,0)==Status::Ok);
    CHECK(cache.tick(0x7fffffffu)==Status::Ok);
    CHECK(cache.tick(0xfffffffeu)==Status::Ok);
    CHECK(cache.reading(0,0xffffffffu,r)==Status::Ok && r.age==MaxAge);
    CHECK(cache.reading(0,0,r)==Status::Ok && r.age==MaxAge);
    SensorState small(2);
    CHECK(small.addSensor("a",nullptr,"")==Status::Ok);
    CHECK(small.start(1,0)==Status::Ok);
    CHECK(small.update("a",1,0)==Status::Ok);
    CHECK(small.unavailable("a",0)==Status::Ok);
    CHECK(small.update("a",2,0)==Status::SequenceExhausted && small.exhausted());
    CHECK(small.reading(0,0,r)==Status::SequenceExhausted);
    CHECK(small.capture(1,0,image)==Status::SequenceExhausted);
    CHECK(small.start(2,0)==Status::Ok && !small.exhausted());
    CHECK(small.update("a",2,0)==Status::Ok);
    return true;
}
bool images() {
    Bytes bytes{};
    for(std::size_t count=1;count<=16;++count) {
        Image image;image.count=static_cast<std::uint8_t>(count);image.requestId=1;image.epoch=epoch;
        for(std::size_t i=0;i<count;++i)image.records[i].channel=static_cast<std::uint8_t>(i);
        const auto size=16+20*count;
        for(std::size_t capacity=0;capacity<size;++capacity) {
            bytes.fill(0xa5);
            CHECK(Codec::encodeImage(image,bytes.data(),capacity).status==Status::BufferTooSmall);
            for(auto b:bytes)CHECK(b==0xa5);
        }
        CHECK(Codec::encodeImage(image,bytes.data(),size).bytesWritten==size);
        Image decoded;
        CHECK(Codec::decodeImage(bytes.data(),size,decoded)==Status::Ok && decoded.count==count && decoded.epoch==epoch);
        CHECK(Codec::decodeImage(bytes.data(),size-1,decoded)==Status::InvalidLength);
        auto bad=bytes;bad[12]=1;decoded.epoch=1;
        CHECK(Codec::decodeImage(bad.data(),size,decoded)==Status::InvalidImage && decoded.epoch==1);
        bad=bytes;bad[0]=0x20;CHECK(Codec::decodeImage(bad.data(),size,decoded)==Status::InvalidImage);
        bad=bytes;bad[1]=17;CHECK(Codec::decodeImage(bad.data(),size,decoded)==Status::InvalidImage);
        bad=bytes;bad[17]=1;CHECK(Codec::decodeImage(bad.data(),size,decoded)==Status::InvalidImage);
        bad=bytes;for(int i=4;i<12;++i)bad[static_cast<std::size_t>(i)]=0;
        CHECK(Codec::decodeImage(bad.data(),size,decoded)==Status::InvalidImage);
        image.records[count-1].available=true;image.records[count-1].sequence=1;image.records[count-1].age=0;
        image.requestId=0;
        CHECK(Codec::encodeImage(image,bytes.data(),size).status==Status::InvalidImage);
    }
    SensorState cache;
    for(int i=0;i<16;++i)CHECK(cache.addSensor(("sensor."+std::to_string(i)).c_str(),nullptr,"")==Status::Ok);
    CHECK(cache.start(epoch,0)==Status::Ok);
    for(int i=0;i<16;++i)CHECK(cache.update(("sensor."+std::to_string(i)).c_str(),i,static_cast<std::uint32_t>(i))==Status::Ok);
    Image captured;CHECK(cache.capture(7,100,captured)==Status::Ok);
    for(std::size_t i=0;i<16;++i)CHECK(captured.records[i].age==100-i);
    CHECK(cache.update("sensor.0",99,100)==Status::Ok && captured.records[0].value==0);
    return true;
}
bool makeRegistry(SensorRegistry& registry,const std::string& mode) {
    if(mode=="one")return registry.addSensor("temperature.indoor","Indoor","C")==SensorRegistry::Result::Ok;
    if(mode=="sixteen") {
        for(int i=0;i<16;++i)CHECK(registry.addSensor(("sensor."+std::to_string(i)).c_str(),("Sensor "+std::to_string(i)).c_str(),"raw")==SensorRegistry::Result::Ok);
        return true;
    }
    if(mode=="utf8") {
        // Tune a UTF-8 name to begin a two-byte scalar at byte offset 495.
        for(int i=0;i<7;++i)CHECK(registry.addSensor(("s"+std::to_string(i)).c_str(),std::string(i==0?11:20,'A').c_str(),"")==SensorRegistry::Result::Ok);
        return registry.addSensor("last","\xc3\xa9","")==SensorRegistry::Result::Ok;
    }
    if(mode=="max") {
        for(int i=0;i<16;++i) {
            std::string name(64,'A'),unit(16,'B');
            for(int j=0;j<64;++j)if(i*80+j<1254)name[static_cast<std::size_t>(j)]='\1';
            for(int j=0;j<16;++j)if(i*80+64+j<1254)unit[static_cast<std::size_t>(j)]='\1';
            CHECK(registry.addSensor(("s"+std::to_string(i)).c_str(),name.c_str(),unit.c_str())==SensorRegistry::Result::Ok);
        }
        return true;
    }
    if(mode=="b495" || mode=="b496" || mode=="b497") {
        const int first=std::stoi(mode.substr(1))-447;
        for(int i=0;i<6;++i)CHECK(registry.addSensor(("s"+std::to_string(i)).c_str(),std::string(i==0?first:20,'A').c_str(),"")==SensorRegistry::Result::Ok);
        return registry.addSensor("last","x","")==SensorRegistry::Result::Ok;
    }
    return false;
}
bool metadata() {
    Metadata metadata;
    Bytes bytes{};
    CHECK(metadata.page(0,bytes.data(),512).status==Status::NotStarted);
    SensorRegistry registry;CHECK(makeRegistry(registry,"one"));
    CHECK(metadata.build(registry,uuid,"Garden",epoch).status==Status::Ok);
    const std::string original(metadata.data(),metadata.size());
    for(const char* id:{"", "bad", "710ABDFC-9d05-46de-a790-7fe484816eea"})
        CHECK(metadata.build(registry,id,"Garden",epoch).status==Status::InvalidMetadata);
    CHECK(metadata.build(registry,nullptr,"Garden",epoch).status==Status::InvalidMetadata);
    CHECK(metadata.build(registry,uuid,"",epoch).status==Status::InvalidMetadata);
    CHECK(metadata.build(registry,uuid,"\xc0\xaf",epoch).status==Status::InvalidMetadata);
    CHECK(metadata.build(registry,uuid,std::string(65,'x').c_str(),epoch).status==Status::InvalidMetadata);
    CHECK(metadata.build(registry,uuid,"Garden",0).status==Status::InvalidMetadata);
    CHECK(std::string(metadata.data(),metadata.size())==original);
    CHECK(metadata.page(1,bytes.data(),512).status==Status::InvalidPage);
    auto page=metadata.page(0,bytes.data(),512); CHECK(page.status==Status::Ok);
    PageView view;CHECK(Metadata::decodePage(bytes.data(),page.bytesWritten,view)==Status::Ok && view.epoch==epoch);
    for(std::size_t len=0;len<16;++len)CHECK(Metadata::decodePage(bytes.data(),len,view)==Status::InvalidLength);
    for(std::size_t capacity=0;capacity<page.bytesWritten;++capacity) {
        Bytes sentinel;sentinel.fill(0xa5);
        CHECK(metadata.page(0,sentinel.data(),capacity).status==Status::BufferTooSmall);
        for(auto b:sentinel)CHECK(b==0xa5);
    }
    CHECK(metadata.page(0,nullptr,1).status==Status::InvalidBuffer);
    CHECK(metadata.page(0,nullptr,0).requiredCapacity==page.bytesWritten);
    for(auto offset:{0,1,2,3,4,5,6,7}) {
        auto bad=bytes;bad[static_cast<std::size_t>(offset)]=0xff;
        CHECK(Metadata::decodePage(bad.data(),page.bytesWritten,view)!=Status::Ok);
    }
    auto bad=bytes;for(int i=8;i<16;++i)bad[static_cast<std::size_t>(i)]=0;
    CHECK(Metadata::decodePage(bad.data(),page.bytesWritten,view)==Status::InvalidPage);
    for(const auto* mode:{"sixteen","utf8","b495","b496","b497","max"}) {
        SensorRegistry many;CHECK(makeRegistry(many,mode));
        CHECK(metadata.build(many,uuid,std::string(mode)=="max"?"Gardenxxxx":"Garden",epoch).status==Status::Ok);
        std::string reconstructed;
        const auto count=(metadata.size()+495)/496;
        if(std::string(mode)=="max")CHECK(metadata.size()==8192 && count==17);
        if(std::string(mode)=="utf8")CHECK(static_cast<unsigned char>(metadata.data()[495])==0xc3 && static_cast<unsigned char>(metadata.data()[496])==0xa9);
        if(mode[0]=='b')CHECK(metadata.size()==static_cast<std::size_t>(std::stoi(std::string(mode).substr(1))));
        for(std::size_t i=0;i<count;++i) {
            page=metadata.page(i,bytes.data(),512);CHECK(page.status==Status::Ok && page.bytesWritten<=512);
            CHECK(Metadata::decodePage(bytes.data(),page.bytesWritten,view)==Status::Ok && view.index==i);
            reconstructed.append(reinterpret_cast<const char*>(view.payload),view.payloadSize);
        }
        CHECK(reconstructed==metadata.data());
    }
    SensorRegistry exactMax; CHECK(makeRegistry(exactMax,"max"));
    CHECK(metadata.build(exactMax,uuid,"Gardenxxxx",epoch).status==Status::Ok && metadata.size()==8192);
    const std::string savedMax(metadata.data(),metadata.size());
    CHECK(metadata.build(exactMax,uuid,"Gardenxxxxx",epoch).status==Status::MetadataTooLarge);
    CHECK(std::string(metadata.data(),metadata.size())==savedMax);
    SensorRegistry escaped;
    CHECK(escaped.addSensor("quote","A\"\\\n\xc3\xa9","\t")==SensorRegistry::Result::Ok);
    CHECK(metadata.build(escaped,uuid,"G\"\\\n",epoch).status==Status::Ok);
    CHECK(std::string(metadata.data()).find("A\\\"\\\\\\u000a")!=std::string::npos);
    // Reach exactly 8192 encoded bytes through valid metadata, then exceed it.
    SensorRegistry maximum;
    for(int i=0;i<16;++i) {
        const std::string name(64,'\1');const std::string unit(16,'\1');
        CHECK(maximum.addSensor(("s"+std::to_string(i)).c_str(),name.c_str(),unit.c_str())==SensorRegistry::Result::Ok);
    }
    CHECK(metadata.build(maximum,uuid,"Garden",epoch).status==Status::MetadataTooLarge);
    CHECK(metadata.size()<8192); // Last valid document remains intact.
    return true;
}
bool emit() {
    Bytes bytes{};
    Record r;r.available=true;r.sequence=42;r.age=10;r.value=23.5;
    CHECK(Codec::encodeRecord(r,16,bytes.data(),20).status==Status::Ok);
    std::printf("record.valid\t%s\n",hex(bytes.data(),20).c_str());
    r=Record{};r.channel=1;r.sequence=7;
    CHECK(Codec::encodeRecord(r,16,bytes.data(),20).status==Status::Ok);
    std::printf("record.unavailable\t%s\n",hex(bytes.data(),20).c_str());
    r=Record{};r.available=true;r.sequence=1;r.age=0;r.value=-0.;
    CHECK(Codec::encodeRecord(r,16,bytes.data(),20).status==Status::Ok);
    std::printf("record.negative_zero\t%s\n",hex(bytes.data(),20).c_str());
    SensorState state;
    CHECK(state.addSensor("a",nullptr,"")==Status::Ok && state.start(epoch,0xfffffff0u)==Status::Ok);
    Image initial;CHECK(state.capture(0,0xfffffff0u,initial)==Status::Ok);
    auto initialBytes=Codec::encodeImage(initial,bytes.data(),bytes.size());CHECK(initialBytes.status==Status::Ok);
    std::printf("image.initial\t%s\n",hex(bytes.data(),initialBytes.bytesWritten).c_str());
    CHECK(state.update("a",23.5,0xfffffff5u)==Status::Ok);
    CHECK(state.reading(0,5,r)==Status::Ok);
    CHECK(Codec::encodeRecord(r,1,bytes.data(),20).status==Status::Ok);
    std::printf("state.wrap\t%s\n",hex(bytes.data(),20).c_str());
    CHECK(state.update("a",23.5,6)==Status::Ok);
    CHECK(state.update("a",std::numeric_limits<double>::quiet_NaN(),7)==Status::InvalidValue);
    CHECK(state.unavailable("a",8)==Status::Ok);
    CHECK(state.update("a",-2,9)==Status::Ok);
    CHECK(state.reading(0,10,r)==Status::Ok);
    CHECK(Codec::encodeRecord(r,1,bytes.data(),20).status==Status::Ok);
    std::printf("state.recovered\t%s\n",hex(bytes.data(),20).c_str());
    for(const auto* mode:{"one","sixteen","utf8","b495","b496","b497","max"}) {
        SensorRegistry registry;CHECK(makeRegistry(registry,mode));Metadata m;
        CHECK(m.build(registry,uuid,std::string(mode)=="max"?"Gardenxxxx":"Garden",epoch).status==Status::Ok);
        std::printf("manifest.%s\t%s\n",mode,hex(reinterpret_cast<const std::uint8_t*>(m.data()),m.size()).c_str());
        for(std::size_t i=0;i<(m.size()+495)/496;++i) {
            const auto result=m.page(i,bytes.data(),512);CHECK(result.status==Status::Ok);
            std::printf("page.%s.%u\t%s\n",mode,static_cast<unsigned int>(i),hex(bytes.data(),result.bytesWritten).c_str());
        }
    }
    for(int n:{1,16}) {
        Image image;image.epoch=epoch;image.requestId=1;image.count=static_cast<std::uint8_t>(n);
        for(int i=0;i<n;++i) {auto& rec=image.records[static_cast<std::size_t>(i)];rec.channel=static_cast<std::uint8_t>(i);rec.available=true;rec.sequence=1;rec.age=static_cast<std::uint32_t>(100-i);rec.value=i+0.5;}
        const auto result=Codec::encodeImage(image,bytes.data(),bytes.size());CHECK(result.status==Status::Ok);
        std::printf("image.%d\t%s\n",n,hex(bytes.data(),result.bytesWritten).c_str());
    }
    return true;
}
int main(int argc,char** argv) {
    if(argc==3 && std::string(argv[1])=="check-record") {
        const std::string encoded=argv[2];
        if(encoded.size()%2 || encoded.size()/2>1024)return 2;
        Bytes bytes{};
        for(std::size_t i=0;i<encoded.size()/2;++i) {
            unsigned int value=0;
            if(std::sscanf(encoded.c_str()+i*2,"%2x",&value)!=1)return 2;
            bytes[i]=static_cast<std::uint8_t>(value);
        }
        Record output;
        return Codec::decodeRecord(bytes.data(),encoded.size()/2,16,output)==Status::Ok?0:1;
    }
    if(argc!=2)return 2;
    const std::string name=argv[1];
    if(name=="numeric")return numeric()?0:1;
    if(name=="corrupt")return corrupt()?0:1;
    if(name=="state")return state()?0:1;
    if(name=="images")return images()?0:1;
    if(name=="metadata")return metadata()?0:1;
    if(name=="emit")return emit()?0:1;
    return 2;
}
