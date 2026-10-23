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
ble_gatt_svc_def* definitions=nullptr;
int (*gapCallback)(ble_gap_event*,void*)=nullptr;
void* gapArgument=nullptr;
ble_npl_event* queued=nullptr;
ble_npl_eventq queue;
int step(){return ++stage==failStage?1:0;}
void connect(std::uint16_t handle) {advertising=false;ble_gap_event e;e.type=BLE_GAP_EVENT_CONNECT;e.connect.conn_handle=handle;gapCallback(&e,gapArgument);}
void disconnect(std::uint16_t handle) {ble_gap_event e;e.type=BLE_GAP_EVENT_DISCONNECT;e.disconnect.conn.conn_handle=handle;gapCallback(&e,gapArgument);}
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
    for(int failure=1;failure<=8;++failure) {
        stage=0;failStage=failure;ExperimentalMetadata device;
        CHECK(begin(device,sensors)==R::StartFailed);
        CHECK(!device.isRunning() && !initialized && !advertising && queued==nullptr);
    }
    stage=0;failStage=0;
    ExperimentalMetadata device;CHECK(begin(device,sensors)==R::Ok);
    CHECK(definitions[0].type==BLE_GATT_SVC_TYPE_PRIMARY && definitions[1].type==0);
    CHECK(definitions[0].characteristics[0].flags==BLE_GATT_CHR_F_READ);
    CHECK(definitions[0].characteristics[1].flags==BLE_GATT_CHR_F_WRITE);
    CHECK(definitions[0].characteristics[2].uuid==nullptr); // No live/state.
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
    CHECK(write(7,{2,1,0})==BLE_ATT_ERR_INVALID_ATTR_VALUE_LEN);
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
}
ble_hs_cfg_type ble_hs_cfg;
bool NimBLEDevice::isInitialized(){return initialized;}
void* NimBLEDevice::getServer(){return nullptr;}
bool NimBLEDevice::init(const char*){if(step())return false;initialized=true;return true;}
bool NimBLEDevice::deinit(bool clear){if(!clear){++stopCount;if(!stopWorks)return false;initialized=false;}else{++clearCount;definitions=nullptr;gapArgument=nullptr;}return true;}
int os_mbuf_append(os_mbuf* m,const void* data,std::uint16_t length){const auto* b=static_cast<const std::uint8_t*>(data);m->bytes.insert(m->bytes.end(),b,b+length);return 0;}
int os_mbuf_copydata(os_mbuf* m,int offset,int length,void* output){if(offset<0||length<0||static_cast<std::size_t>(offset+length)>m->bytes.size())return 1;std::memcpy(output,m->bytes.data()+offset,static_cast<std::size_t>(length));return 0;}
int ble_gatts_reset(){return step();}void ble_svc_gap_init(){}void ble_svc_gatt_init(){}
int ble_gatts_count_cfg(ble_gatt_svc_def*){return step();}int ble_gatts_add_svcs(ble_gatt_svc_def* d){definitions=d;return step();}int ble_gatts_start(){return step();}
int ble_gap_adv_set_fields(ble_hs_adv_fields*){return step();}int ble_hs_id_infer_auto(int,std::uint8_t* t){*t=0;return step();}
int ble_gap_adv_start(std::uint8_t,const void*,int,ble_gap_adv_params*,int (*cb)(ble_gap_event*,void*),void* arg){if(step())return 1;gapCallback=cb;gapArgument=arg;advertising=true;return 0;}
int ble_gap_adv_stop(){if(!initialized){std::fprintf(stderr,"Raw GAP called before host initialization\n");std::abort();}advertising=false;return 0;}int ble_gap_terminate(std::uint16_t handle,int){terminated=handle;return 0;}
void ble_npl_event_init(ble_npl_event* e,void (*cb)(ble_npl_event*),void* arg){e->callback=cb;e->arg=arg;}
void* ble_npl_event_get_arg(ble_npl_event* e){return e->arg;}
ble_npl_eventq* nimble_port_get_dflt_eventq(){return &queue;}
void ble_npl_eventq_put(ble_npl_eventq*,ble_npl_event* e){e->queued=true;queued=e;}
void ble_npl_eventq_remove(ble_npl_eventq*,ble_npl_event* e){if(queued==e)queued=nullptr;e->queued=false;}
int main(int argc,char** argv){if(argc!=2)return 2;const std::string name=argv[1];if(name=="startup")return startup()?0:1;if(name=="offsets")return offsets()?0:1;if(name=="cleanup")return cleanup()?0:1;return 2;}
