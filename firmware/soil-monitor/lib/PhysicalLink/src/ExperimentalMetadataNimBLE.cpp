#if defined(PHYSICAL_LINK_EXPERIMENTAL_V2_METADATA) && PHYSICAL_LINK_EXPERIMENTAL_V2_METADATA
#include "ExperimentalMetadataDriver.h"
#ifdef PHYSICAL_LINK_METADATA_NATIVE_DRIVER_TEST
#include "NimbleMetadataTestPort.h"
#else
#include <NimBLEDevice.h>
#include "nimble/nimble/host/include/host/ble_hs.h"
#include "nimble/nimble/host/services/gap/include/services/gap/ble_svc_gap.h"
#include "nimble/nimble/host/services/gatt/include/services/gatt/ble_svc_gatt.h"
#include "nimble/porting/nimble/include/nimble/nimble_port.h"
#endif
#include <atomic>
#include <new>
#if NIMBLE_CPP_VERSION != NIMBLE_CPP_VERSION_VAL(2, 5, 1)
#error "Re-audit ATT offset slicing before using another NimBLE version"
#endif
#if MYNEWT_VAL(BLE_EATT_CHAN_NUM) != 0
#error "Experimental metadata pinning supports exactly one ATT bearer; disable EATT"
#endif
namespace physical_link { namespace v2 {
namespace {
constexpr char ServiceUuid[] = "b89e2000-7c42-4f9d-9d25-8a639eab5120";
constexpr char MetadataUuid[] = "b89e2001-7c42-4f9d-9d25-8a639eab5120";
constexpr char ControlUuid[] = "b89e2002-7c42-4f9d-9d25-8a639eab5120";
class NimbleMetadataDriver final : public MetadataDriver {
    MetadataSession* session_ = nullptr;
    portMUX_TYPE mux_ = portMUX_INITIALIZER_UNLOCKED;
    std::atomic<bool> stopping_{true};
    std::atomic<int> error_{0};
    bool ownsStack_ = false, eventReady_ = false;
    NimBLEUUID serviceUuid_{ServiceUuid}, metadataUuid_{MetadataUuid}, controlUuid_{ControlUuid};
    ble_gatt_svc_def services_[2]{};
    ble_gatt_chr_def characteristics_[3]{};
    ble_npl_event event_{};
    std::uint8_t addressType_ = 0;
    static int access(std::uint16_t connection, std::uint16_t,
                      ble_gatt_access_ctxt* context, void* argument) {
        auto* self = static_cast<NimbleMetadataDriver*>(argument);
        if (self->stopping_.load()) return BLE_ATT_ERR_UNLIKELY;
        if (context->op == BLE_GATT_ACCESS_OP_READ_CHR &&
            context->chr == &self->characteristics_[0]) {
            std::uint8_t copy[MaxPageSize];
            std::size_t length = 0;
            portENTER_CRITICAL(&self->mux_);
            const auto result = self->session_->read(connection, context->offset, copy, sizeof(copy), length);
            portEXIT_CRITICAL(&self->mux_);
            if (result == MetadataSession::Access::InvalidOffset) return BLE_ATT_ERR_INVALID_OFFSET;
            if (result != MetadataSession::Access::Ok) return BLE_ATT_ERR_UNLIKELY;
            // Full attribute only: the host slices this after our return.
            // Append an owned copy, never a borrowed pointer into a page buffer.
            return os_mbuf_append(context->om, copy, static_cast<std::uint16_t>(length)) == 0
                       ? 0 : BLE_ATT_ERR_INSUFFICIENT_RES;
        }
        if (context->op == BLE_GATT_ACCESS_OP_WRITE_CHR &&
            context->chr == &self->characteristics_[1]) {
            const auto length = OS_MBUF_PKTLEN(context->om);
            std::uint8_t command[2]{};
            const bool copied = length == sizeof(command) &&
                os_mbuf_copydata(context->om, 0, sizeof(command), command) == 0;
            portENTER_CRITICAL(&self->mux_);
            const auto result = self->session_->command(connection, copied ? command : nullptr, copied ? 2 : 0);
            portEXIT_CRITICAL(&self->mux_);
            if (!copied) return BLE_ATT_ERR_INVALID_ATTR_VALUE_LEN;
            return result == MetadataSession::Access::Ok ? 0 : BLE_ATT_ERR_UNLIKELY;
        }
        return BLE_ATT_ERR_UNLIKELY;
    }
    bool advertise() {
        if (stopping_.load()) return false;
        ble_gap_adv_params parameters{};
        parameters.conn_mode = BLE_GAP_CONN_MODE_UND;
        parameters.disc_mode = BLE_GAP_DISC_MODE_GEN;
        const int result = ble_gap_adv_start(addressType_, nullptr, BLE_HS_FOREVER,
                                             &parameters, gap, this);
        if (result != 0 && result != BLE_HS_EALREADY) { error_.store(result); return false; }
        return true;
    }
    static int gap(ble_gap_event* event, void* argument) {
        auto* self = static_cast<NimbleMetadataDriver*>(argument);
        if (self->stopping_.load()) return 0;
        if (event->type == BLE_GAP_EVENT_CONNECT) {
            if (event->connect.status) { self->advertise(); return 0; }
            portENTER_CRITICAL(&self->mux_);
            const bool accepted = self->session_->connect(event->connect.conn_handle);
            portEXIT_CRITICAL(&self->mux_);
            if (!accepted) ble_gap_terminate(event->connect.conn_handle, BLE_ERR_REM_USER_CONN_TERM);
        } else if (event->type == BLE_GAP_EVENT_DISCONNECT) {
            portENTER_CRITICAL(&self->mux_);
            self->session_->disconnect(event->disconnect.conn.conn_handle);
            const bool connected = self->session_->connected();
            portEXIT_CRITICAL(&self->mux_);
            if (!connected) self->advertise();
        }
        return 0;
    }
    static void lifecycle(ble_npl_event* event) {
        auto* self = static_cast<NimbleMetadataDriver*>(ble_npl_event_get_arg(event));
        if (self->stopping_.load()) return;
        // Runs on the SAME host event task as GAP/access callbacks, so a
        // connection handle cannot be recycled between checking and terminate.
        portENTER_CRITICAL(&self->mux_);
        const bool fault = self->session_->connected() && self->session_->faulted();
        const auto handle = self->session_->handle();
        portEXIT_CRITICAL(&self->mux_);
        if (fault) ble_gap_terminate(handle, BLE_ERR_REM_USER_CONN_TERM);
    }
public:
    ExperimentalMetadata::Result start(MetadataSession& session) override {
        using R = ExperimentalMetadata::Result;
        if (NimBLEDevice::isInitialized() || NimBLEDevice::getServer()) return R::Busy;
        session_ = &session;
        ownsStack_ = true;
        if (!NimBLEDevice::init("PL v2 metadata")) return R::StartFailed;
#ifdef PHYSICAL_LINK_METADATA_TEST_MTU
        if (!NimBLEDevice::setMTU(PHYSICAL_LINK_METADATA_TEST_MTU)) return R::StartFailed;
#endif
        // Raw service registration: NimBLEServer::start/resetGATT would replace
        // these definitions. Deliberately never create a NimBLEServer here.
        if (ble_gatts_reset() != 0) return R::StartFailed;
        ble_svc_gap_init(); ble_svc_gatt_init();
        // A prior high-level server may have left a registration callback with
        // a stack-local argument. This backend has no NimBLEServer objects.
        ble_hs_cfg.gatts_register_cb = nullptr;
        ble_hs_cfg.gatts_register_arg = nullptr;
        characteristics_[0].uuid = metadataUuid_.getBase();
        characteristics_[0].flags = BLE_GATT_CHR_F_READ;
        characteristics_[0].access_cb = access; characteristics_[0].arg = this;
        characteristics_[1].uuid = controlUuid_.getBase();
        characteristics_[1].flags = BLE_GATT_CHR_F_WRITE;
        characteristics_[1].access_cb = access; characteristics_[1].arg = this;
        services_[0].type = BLE_GATT_SVC_TYPE_PRIMARY;
        services_[0].uuid = serviceUuid_.getBase();
        services_[0].characteristics = characteristics_;
        if (ble_gatts_count_cfg(services_) || ble_gatts_add_svcs(services_) || ble_gatts_start()) return R::StartFailed;
        ble_hs_adv_fields fields{};
        fields.flags = BLE_HS_ADV_F_DISC_GEN | BLE_HS_ADV_F_BREDR_UNSUP;
        fields.uuids128 = reinterpret_cast<const ble_uuid128_t*>(serviceUuid_.getBase());
        fields.num_uuids128 = 1; fields.uuids128_is_complete = 1;
        if (ble_gap_adv_set_fields(&fields) || ble_hs_id_infer_auto(0, &addressType_)) return R::StartFailed;
        ble_npl_event_init(&event_, lifecycle, this); eventReady_ = true;
        stopping_.store(false);
        return advertise() ? R::Ok : R::StartFailed;
    }
    ExperimentalMetadata::Result poll() override {
        if (stopping_.load()) return ExperimentalMetadata::Result::NotRunning;
        portENTER_CRITICAL(&mux_);
        session_->poll();
        const bool fault = session_->faulted();
        portEXIT_CRITICAL(&mux_);
        if (fault) ble_npl_eventq_put(nimble_port_get_dflt_eventq(), &event_);
        return error_.load() ? ExperimentalMetadata::Result::StartFailed : ExperimentalMetadata::Result::Ok;
    }
    bool stop() override {
        if (!ownsStack_) return true;
        stopping_.store(true);
        // init() can fail before the NimBLE host/locks exist. Do not call raw
        // GAP/NPL APIs on that failure path; use the wrapper's guarded deinit.
        if (NimBLEDevice::isInitialized()) {
            ble_gap_adv_stop();
            if (eventReady_) ble_npl_eventq_remove(nimble_port_get_dflt_eventq(), &event_);
        }
        // Never free callback arguments/raw definitions until the host stops.
        if (!NimBLEDevice::deinit(false)) return false;
        if (!NimBLEDevice::deinit(true)) return false;
        ownsStack_ = false; eventReady_ = false; session_ = nullptr;
        return true;
    }
};
}
MetadataDriver* createMetadataDriver() { return new (std::nothrow) NimbleMetadataDriver; }
} }
#endif
