#include "NimbleMetadataTestPort.h"
#include "ExperimentalV2Metadata.h"
#include <cstdio>
#include <cstdlib>
#include <string>
using namespace physical_link;
using namespace physical_link::v2;
using R=ExperimentalMetadata::Result;
#define CHECK(x) do {if(!(x)){std::fprintf(stderr,"%d: %s\n",__LINE__,#x);return false;}}while(false)
namespace {
bool initialized=false,stopWorks=true;
int stage=0,failStage=0,stopCount=0,clearCount=0,terminated=-1;
bool advertising=false;
std::string deviceName,scanName;
std::size_t advertisingSize=0,scanResponseSize=0;
bool completeService=false,completeName=false;
std::uint32_t nowMs=0;
bool notifyWorks=true,mbufWorks=true;
std::vector<std::vector<std::uint8_t>> notifications;
std::vector<std::uint32_t> notificationTimes;
int attempts=0;
ble_gatt_svc_def* definitions=nullptr;
int (*gapCallback)(ble_gap_event*,void*)=nullptr;
void* gapArgument=nullptr;
ble_npl_event* queued=nullptr;
ble_npl_eventq queue;
int step(){return ++stage==failStage?1:0;}
void connect(std::uint16_t handle) {advertising=false;ble_gap_event e;e.type=BLE_GAP_EVENT_CONNECT;e.connect.conn_handle=handle;gapCallback(&e,gapArgument);}
void disconnect(std::uint16_t handle) {ble_gap_event e;e.type=BLE_GAP_EVENT_DISCONNECT;e.disconnect.conn.conn_handle=handle;gapCallback(&e,gapArgument);}
void subscribe(std::uint16_t handle,bool enabled,std::uint16_t attribute=42) {ble_gap_event e;e.type=BLE_GAP_EVENT_SUBSCRIBE;e.subscribe.conn_handle=handle;e.subscribe.attr_handle=attribute;e.subscribe.cur_notify=enabled;gapCallback(&e,gapArgument);}
void hostEvent() {if(queued){auto* event=queued;queued=nullptr;event->queued=false;event->callback(event);}}
int readState(std::uint16_t handle,std::uint16_t offset,os_mbuf& mbuf) {
    ble_gatt_access_ctxt context;context.op=BLE_GATT_ACCESS_OP_READ_CHR;context.offset=offset;
    context.chr=&definitions[0].characteristics[2];context.om=&mbuf;
    return context.chr->access_cb(handle,42,&context,context.chr->arg);
}
int read(std::uint16_t handle,std::uint16_t offset,os_mbuf& mbuf) {
    ble_gatt_access_ctxt context;context.op=BLE_GATT_ACCESS_OP_READ_CHR;context.offset=offset;
    context.chr=&definitions[0].characteristics[0];context.om=&mbuf;
    return context.chr->access_cb(handle,1,&context,context.chr->arg);
}
int write(std::uint16_t handle,std::vector<std::uint8_t> bytes) {
    os_mbuf mbuf;mbuf.bytes=bytes;ble_gatt_access_ctxt context;context.op=BLE_GATT_ACCESS_OP_WRITE_CHR;
    context.chr=&definitions[0].characteristics[1];context.om=&mbuf;
    return context.chr->access_cb(handle,2,&context,context.chr->arg);
}
bool registry(SensorRegistry& sensors) {
    for(int i=0;i<16;++i)CHECK(sensors.addSensor(("sensor."+std::to_string(i)).c_str(),("Sensor "+std::to_string(i)).c_str(),"raw")==SensorRegistry::Result::Ok);
    return true;
}
R begin(ExperimentalMetadata& device,SensorRegistry& sensors){return device.begin(sensors,"710abdfc-9d05-46de-a790-7fe484816eea","Garden",UINT64_C(0xb398ca802d871ae0));}
bool startup() {
    SensorRegistry sensors;CHECK(registry(sensors));
    {
        initialized=true;ExperimentalMetadata device;const auto stopped=stopCount;
        CHECK(begin(device,sensors)==R::Busy && initialized && stopCount==stopped);
        initialized=false;
    }
    for(int failure=1;failure<=9;++failure) {
        stage=0;failStage=failure;ExperimentalMetadata device;
        CHECK(begin(device,sensors)==R::StartFailed);
        CHECK(!device.isRunning() && !initialized && !advertising && queued==nullptr);
    }
    stage=0;failStage=0;
    ExperimentalMetadata device;CHECK(begin(device,sensors)==R::Ok);
    CHECK(deviceName=="PhysicalLink V2" && scanName==deviceName && completeName);
    CHECK(completeService && advertisingSize==21 && scanResponseSize==17);
    CHECK(advertisingSize<=31 && scanResponseSize<=31);
    CHECK(definitions[0].type==BLE_GATT_SVC_TYPE_PRIMARY && definitions[1].type==0);
    CHECK(definitions[0].characteristics[0].flags==BLE_GATT_CHR_F_READ);
    CHECK(definitions[0].characteristics[1].flags==BLE_GATT_CHR_F_WRITE);
    CHECK(definitions[0].characteristics[2].flags==(BLE_GATT_CHR_F_READ|BLE_GATT_CHR_F_NOTIFY));
    CHECK(std::string(definitions[0].uuid->text)=="b89e2000-7c42-4f9d-9d25-8a639eab5120");
    CHECK(std::string(definitions[0].characteristics[2].uuid->text)=="b89e2003-7c42-4f9d-9d25-8a639eab5120");
    CHECK(definitions[0].characteristics[3].uuid==nullptr);
    CHECK(advertising && ble_hs_cfg.gatts_register_cb==nullptr);
    CHECK(device.end()==R::Ok && !initialized && !advertising);
    return true;
}
bool offsets() {
    SensorRegistry sensors;CHECK(registry(sensors));ExperimentalMetadata device;CHECK(begin(device,sensors)==R::Ok);
    connect(7);os_mbuf initial;CHECK(read(7,0,initial)==0 && initial.bytes.size()==512);
    connect(8);CHECK(terminated==8 && !advertising);
    os_mbuf peer;CHECK(read(8,0,peer)==BLE_ATT_ERR_UNLIKELY);
    disconnect(8);CHECK(!advertising);terminated=-1;
    CHECK(write(7,{1,1})==0);
    os_mbuf delayed;CHECK(read(7,0,delayed)==0 && delayed.bytes==initial.bytes);
    CHECK(device.poll()==R::Ok);
    for(std::uint16_t offset=22;offset<512;offset=static_cast<std::uint16_t>(offset+22)) {
        os_mbuf blob;CHECK(read(7,offset,blob)==0 && blob.bytes==initial.bytes);
    }
    os_mbuf newer;CHECK(read(7,0,newer)==0 && newer.bytes[3]==1 && newer.bytes.size()==436);
    os_mbuf invalid;CHECK(read(7,437,invalid)==BLE_ATT_ERR_INVALID_OFFSET && invalid.bytes.empty());
    CHECK(write(7,{1,0})==0);
    disconnect(7);connect(7);CHECK(device.poll()==R::Ok);
    os_mbuf reset;CHECK(read(7,0,reset)==0 && reset.bytes==initial.bytes);
    CHECK(write(7,{2,1,0})==BLE_ATT_ERR_UNLIKELY); // Capture requires subscription.
    CHECK(device.poll()==R::Ok && queued!=nullptr && terminated==-1);
    disconnect(7);connect(7); // Recycled handle before queued old fault runs.
    queued->queued=false;auto* event=queued;queued=nullptr;event->callback(event);
    CHECK(terminated==-1);
    CHECK(write(7,{1,255})!=0 && device.poll()==R::Ok && queued);
    queued->queued=false;event=queued;queued=nullptr;event->callback(event);
    CHECK(terminated==7);
    CHECK(device.end()==R::Ok && queued==nullptr);
    return true;
}
bool cleanup() {
    SensorRegistry sensors;CHECK(registry(sensors));ExperimentalMetadata device;CHECK(begin(device,sensors)==R::Ok);
    connect(3);CHECK(write(3,{1,255})!=0 && device.poll()==R::Ok && queued);
    stopWorks=false;const int cleared=clearCount;
    CHECK(device.end()==R::StopFailed && clearCount==cleared && queued==nullptr);
    CHECK(definitions!=nullptr);
    os_mbuf stoppedRead;CHECK(read(3,0,stoppedRead)==BLE_ATT_ERR_UNLIKELY && stoppedRead.bytes.empty());
    stopWorks=true;CHECK(device.end()==R::Ok && clearCount==cleared+1);
    CHECK(device.end()==R::Ok && !initialized);
    return true;
}
bool live() {
    nowMs=1000;
    SensorRegistry sensors;CHECK(registry(sensors));ExperimentalMetadata device;
    CHECK(device.update("sensor.0",1)==Status::NotStarted);
    CHECK(begin(device,sensors)==R::Ok);connect(7);
    CHECK(device.update("sensor.0",23.5)==Status::Ok);
    CHECK(device.poll()==R::Ok);hostEvent();CHECK(notifications.empty());
    subscribe(7,true,43);CHECK(device.poll()==R::Ok);hostEvent();CHECK(notifications.empty());
    subscribe(8,true);CHECK(device.poll()==R::Ok);hostEvent();CHECK(notifications.empty());
    subscribe(7,true);CHECK(notifications.empty());
    os_mbuf zero;CHECK(readState(7,0,zero)==0 && zero.bytes.size()==336);
    CHECK(write(7,{2,1,0})==0);
    os_mbuf pending;CHECK(readState(7,0,pending)==0 && pending.bytes==zero.bytes);
    nowMs=1010;CHECK(device.poll()==R::Ok);hostEvent();
    CHECK(notifications.size()==1 && notifications[0].size()==20);
    Record record;CHECK(Codec::decodeRecord(notifications[0].data(),20,16,record)==Status::Ok);
    CHECK(record.channel==0 && record.value==23.5 && record.age==10 && record.sequence==1);
    os_mbuf pinned;CHECK(readState(7,22,pinned)==0 && pinned.bytes==zero.bytes);
    os_mbuf captured;CHECK(readState(7,0,captured)==0);
    Image image;CHECK(Codec::decodeImage(captured.bytes.data(),captured.bytes.size(),image)==Status::Ok);
    CHECK(image.requestId==1 && image.records[0].value==23.5 && image.records[0].age==10);
    nowMs=1020;CHECK(device.update("sensor.0",30)==Status::Ok);
    for(nowMs=1060;nowMs<=1760;nowMs+=50) {CHECK(device.poll()==R::Ok);hostEvent();}
    CHECK(notifications.size()==16);
    for(std::size_t i=1;i<16;++i) {
        CHECK(Codec::decodeRecord(notifications[i].data(),20,16,record)==Status::Ok);
        CHECK(record.channel==i && !record.available && record.sequence==0);
    }
    mbufWorks=false;nowMs=1810;CHECK(device.poll()==R::Ok);hostEvent();CHECK(notifications.size()==16);
    mbufWorks=true;nowMs=1860;CHECK(device.poll()==R::Ok);hostEvent();
    CHECK(Codec::decodeRecord(notifications.back().data(),20,16,record)==Status::Ok && record.sequence==2);
    nowMs=1910;CHECK(device.update("sensor.0",31)==Status::Ok);notifyWorks=false;
    CHECK(device.poll()==R::Ok);hostEvent();CHECK(notifications.size()==17);
    nowMs=1960;CHECK(device.update("sensor.0",32)==Status::Ok);notifyWorks=true;
    CHECK(device.poll()==R::Ok);hostEvent();
    CHECK(Codec::decodeRecord(notifications.back().data(),20,16,record)==Status::Ok && record.sequence==4);
    os_mbuf unchanged;CHECK(readState(7,0,unchanged)==0 && unchanged.bytes==captured.bytes);
    for(std::size_t i=1;i<notificationTimes.size();++i)CHECK(notificationTimes[i]-notificationTimes[i-1]>=50);
    subscribe(7,false);nowMs=2010;CHECK(device.poll()==R::Ok);hostEvent();CHECK(notifications.size()==18);
    CHECK(write(7,{2,2,0})!=0);CHECK(device.poll()==R::Ok);hostEvent();CHECK(terminated==7);
    disconnect(7);connect(7);CHECK(device.poll()==R::Ok);hostEvent();CHECK(notifications.size()==18);
    subscribe(7,true);CHECK(write(7,{2,1,0})==0);
    CHECK(device.poll()==R::Ok); // Disconnect with capture published and host event still queued.
    disconnect(7);connect(7);hostEvent();CHECK(notifications.size()==18);
    os_mbuf reset;CHECK(readState(7,22,reset)==BLE_ATT_ERR_INVALID_OFFSET);
    CHECK(readState(7,0,reset)==0 && reset.bytes==zero.bytes);
    subscribe(7,true);CHECK(write(7,{2,1,0})==0);nowMs=2060;
    CHECK(device.poll()==R::Ok);hostEvent();os_mbuf current;CHECK(readState(7,0,current)==0);
    CHECK(Codec::decodeImage(current.bytes.data(),current.bytes.size(),image)==Status::Ok);
    CHECK(image.requestId==1 && image.records[0].sequence==4 && image.records[0].age==100);
    CHECK(device.end()==R::Ok && !queued && !initialized);
    CHECK(device.unavailable("sensor.0")==Status::NotStarted);
    nowMs=3000;CHECK(begin(device,sensors)==R::Ok);connect(7);subscribe(7,true);
    CHECK(write(7,{2,1,0})==0 && device.poll()==R::Ok);hostEvent();
    os_mbuf restarted;CHECK(readState(7,0,restarted)==0);
    CHECK(Codec::decodeImage(restarted.bytes.data(),restarted.bytes.size(),image)==Status::Ok);
    CHECK(image.records[0].sequence==0 && !image.records[0].available);
    return true;
}
bool liveRate() {
    SensorRegistry sensors;CHECK(registry(sensors));ExperimentalMetadata device;
    CHECK(begin(device,sensors)==R::Ok);connect(7);subscribe(7,true);
    for(nowMs=0;nowMs<2000;++nowMs) {
        CHECK(device.update("sensor.0",nowMs)==Status::Ok);
        for(int poll=0;poll<5;++poll) {CHECK(device.poll()==R::Ok);hostEvent();}
    }
    CHECK(notifications.size()==40 && attempts==40);
    for(std::size_t i=1;i<notificationTimes.size();++i)CHECK(notificationTimes[i]-notificationTimes[i-1]>=50);
    nowMs=5000;CHECK(device.poll()==R::Ok);hostEvent();
    CHECK(notifications.size()==41); // No accumulated catch-up burst.
    return true;
}
}
ble_hs_cfg_type ble_hs_cfg;
bool NimBLEDevice::isInitialized(){return initialized;}
void* NimBLEDevice::getServer(){return nullptr;}
bool NimBLEDevice::init(const char* name){if(step())return false;deviceName=name;initialized=true;return true;}
bool NimBLEDevice::deinit(bool clear){if(!clear){++stopCount;if(!stopWorks)return false;initialized=false;}else{++clearCount;definitions=nullptr;gapArgument=nullptr;}return true;}
int os_mbuf_append(os_mbuf* m,const void* data,std::uint16_t length){const auto* b=static_cast<const std::uint8_t*>(data);m->bytes.insert(m->bytes.end(),b,b+length);return 0;}
int os_mbuf_copydata(os_mbuf* m,int offset,int length,void* output){if(offset<0||length<0||static_cast<std::size_t>(offset+length)>m->bytes.size())return 1;std::memcpy(output,m->bytes.data()+offset,static_cast<std::size_t>(length));return 0;}
int ble_gatts_reset(){return step();}void ble_svc_gap_init(){}void ble_svc_gatt_init(){}
int ble_gatts_count_cfg(ble_gatt_svc_def*){return step();}int ble_gatts_add_svcs(ble_gatt_svc_def* d){definitions=d;*d[0].characteristics[2].val_handle=42;return step();}int ble_gatts_start(){return step();}
int ble_gap_adv_set_fields(ble_hs_adv_fields* fields){advertisingSize=(fields->flags?3:0)+18*fields->num_uuids128;completeService=fields->uuids128 && fields->num_uuids128==1 && fields->uuids128_is_complete && !fields->name;return step();}
int ble_gap_adv_rsp_set_fields(const ble_hs_adv_fields* fields){scanName.assign(reinterpret_cast<const char*>(fields->name),fields->name_len);scanResponseSize=2+fields->name_len;completeName=fields->name_is_complete!=0;return step();}
int ble_hs_id_infer_auto(int,std::uint8_t* t){*t=0;return step();}
int ble_gap_adv_start(std::uint8_t,const void*,int,ble_gap_adv_params*,int (*cb)(ble_gap_event*,void*),void* arg){if(step())return 1;gapCallback=cb;gapArgument=arg;advertising=true;return 0;}
int ble_gap_adv_stop(){if(!initialized){std::fprintf(stderr,"Raw GAP called before host initialization\n");std::abort();}advertising=false;return 0;}int ble_gap_terminate(std::uint16_t handle,int){terminated=handle;return 0;}
void ble_npl_event_init(ble_npl_event* e,void (*cb)(ble_npl_event*),void* arg){e->callback=cb;e->arg=arg;}
void* ble_npl_event_get_arg(ble_npl_event* e){return e->arg;}
ble_npl_eventq* nimble_port_get_dflt_eventq(){return &queue;}
void ble_npl_eventq_put(ble_npl_eventq*,ble_npl_event* e){e->queued=true;queued=e;}
void ble_npl_eventq_remove(ble_npl_eventq*,ble_npl_event* e){if(queued==e)queued=nullptr;e->queued=false;}
std::uint32_t millis(){return nowMs;}
os_mbuf* ble_hs_mbuf_from_flat(const void* data,std::uint16_t length){if(!mbufWorks)return nullptr;auto* m=new os_mbuf;os_mbuf_append(m,data,length);return m;}
int ble_gatts_notify_custom(std::uint16_t handle,std::uint16_t attribute,os_mbuf* m){++attempts;const bool valid=handle==7 && attribute==42 && notifyWorks;if(valid){notifications.push_back(m->bytes);notificationTimes.push_back(nowMs);}delete m;return valid?0:1;}
int main(int argc,char** argv){if(argc!=2)return 2;const std::string name=argv[1];if(name=="startup")return startup()?0:1;if(name=="offsets")return offsets()?0:1;if(name=="cleanup")return cleanup()?0:1;if(name=="live")return live()?0:1;if(name=="live_rate")return liveRate()?0:1;return 2;}
