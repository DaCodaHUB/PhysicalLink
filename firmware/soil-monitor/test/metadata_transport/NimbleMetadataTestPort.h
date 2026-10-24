#ifndef PHYSICAL_LINK_NIMBLE_METADATA_TEST_PORT_H
#define PHYSICAL_LINK_NIMBLE_METADATA_TEST_PORT_H
// Native adapter test double. Does not emulate the NimBLE host or radio.
#include <cstddef>
#include <cstdint>
#include <cstring>
#include <vector>
#define NIMBLE_CPP_VERSION_VAL(a,b,c) (((a)<<16)|((b)<<8)|(c))
#define NIMBLE_CPP_VERSION NIMBLE_CPP_VERSION_VAL(2,5,1)
#define MYNEWT_VAL(x) 0
struct portMUX_TYPE {};
#define portMUX_INITIALIZER_UNLOCKED {}
#define portENTER_CRITICAL(x) (void)(x)
#define portEXIT_CRITICAL(x) (void)(x)
struct ble_uuid_t { int type = 128; const char* text = nullptr; };
struct ble_uuid128_t { ble_uuid_t u; std::uint8_t value[16]{}; };
class NimBLEUUID {
    ble_uuid128_t uuid_;
public:
    explicit NimBLEUUID(const char* text) { uuid_.u.text = text; }
    const ble_uuid_t* getBase() const { return &uuid_.u; }
};
class NimBLEDevice {
public:
    static bool isInitialized(); static void* getServer();
    static bool init(const char*); static bool deinit(bool);
};
struct os_mbuf { std::vector<std::uint8_t> bytes; };
#define OS_MBUF_PKTLEN(m) ((m)->bytes.size())
int os_mbuf_append(os_mbuf*, const void*, std::uint16_t);
int os_mbuf_copydata(os_mbuf*, int, int, void*);
struct ble_gatt_access_ctxt;
struct ble_gatt_chr_def {
    const ble_uuid_t* uuid = nullptr;
    int flags = 0;
    int (*access_cb)(std::uint16_t,std::uint16_t,ble_gatt_access_ctxt*,void*) = nullptr;
    void* arg = nullptr;
    std::uint16_t* val_handle = nullptr;
};
struct ble_gatt_svc_def { int type=0; const ble_uuid_t* uuid=nullptr; const ble_gatt_chr_def* characteristics=nullptr; };
struct ble_gatt_access_ctxt { std::uint8_t op=0; os_mbuf* om=nullptr; const ble_gatt_chr_def* chr=nullptr; std::uint16_t offset=0; };
struct ble_gap_adv_params { int conn_mode=0,disc_mode=0; };
struct ble_hs_adv_fields { int flags=0; const ble_uuid128_t* uuids128=nullptr; int num_uuids128=0,uuids128_is_complete=0; const std::uint8_t* name=nullptr; std::uint8_t name_len=0; int name_is_complete=0; };
struct ble_gap_event {
    int type=0;
    struct {int status=0;std::uint16_t conn_handle=0;} connect;
    struct {struct {std::uint16_t conn_handle=0;} conn;} disconnect;
    struct {std::uint16_t conn_handle=0,attr_handle=0;int cur_notify=0;} subscribe;
};
struct ble_npl_event {void (*callback)(ble_npl_event*)=nullptr;void* arg=nullptr;bool queued=false;};
struct ble_npl_eventq {};
struct ble_hs_cfg_type {void* gatts_register_cb=nullptr;void* gatts_register_arg=nullptr;};
extern ble_hs_cfg_type ble_hs_cfg;
int ble_gatts_reset(); void ble_svc_gap_init(); void ble_svc_gatt_init();
int ble_gatts_count_cfg(ble_gatt_svc_def*);int ble_gatts_add_svcs(ble_gatt_svc_def*);int ble_gatts_start();
int ble_gap_adv_set_fields(ble_hs_adv_fields*);int ble_hs_id_infer_auto(int,std::uint8_t*);
int ble_gap_adv_rsp_set_fields(const ble_hs_adv_fields*);
int ble_gap_adv_start(std::uint8_t,const void*,int,ble_gap_adv_params*,int (*)(ble_gap_event*,void*),void*);
int ble_gap_adv_stop();int ble_gap_terminate(std::uint16_t,int);
std::uint32_t millis();
os_mbuf* ble_hs_mbuf_from_flat(const void*, std::uint16_t);
int ble_gatts_notify_custom(std::uint16_t, std::uint16_t, os_mbuf*);
void ble_npl_event_init(ble_npl_event*,void (*)(ble_npl_event*),void*);
void* ble_npl_event_get_arg(ble_npl_event*);
ble_npl_eventq* nimble_port_get_dflt_eventq();
void ble_npl_eventq_put(ble_npl_eventq*,ble_npl_event*);void ble_npl_eventq_remove(ble_npl_eventq*,ble_npl_event*);
constexpr int BLE_ATT_ERR_UNLIKELY=14,BLE_ATT_ERR_INVALID_OFFSET=7,BLE_ATT_ERR_INSUFFICIENT_RES=17,BLE_ATT_ERR_INVALID_ATTR_VALUE_LEN=13;
constexpr int BLE_GATT_ACCESS_OP_READ_CHR=0,BLE_GATT_ACCESS_OP_WRITE_CHR=1;
constexpr int BLE_GATT_CHR_F_READ=2,BLE_GATT_CHR_F_WRITE=8,BLE_GATT_CHR_F_NOTIFY=16,BLE_GATT_SVC_TYPE_PRIMARY=1;
constexpr int BLE_GAP_CONN_MODE_UND=2,BLE_GAP_DISC_MODE_GEN=2,BLE_HS_FOREVER=0x7fffffff,BLE_HS_EALREADY=2;
constexpr int BLE_GAP_EVENT_CONNECT=0,BLE_GAP_EVENT_DISCONNECT=1,BLE_GAP_EVENT_SUBSCRIBE=2,BLE_ERR_REM_USER_CONN_TERM=19;
constexpr int BLE_HS_ADV_F_DISC_GEN=2,BLE_HS_ADV_F_BREDR_UNSUP=4;
#endif
