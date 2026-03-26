SYSMONITOR_BASE_DIR := vendor/transsion/vendor_modules/usf/sysmonitor

ifndef PERFENGINE_PLATFORM_TYPE
    ifneq ($(wildcard vendor/qcom),)
        PERFENGINE_PLATFORM_TYPE := qcom
    else ifneq ($(wildcard vendor/mediatek),)
        PERFENGINE_PLATFORM_TYPE := mtk
    else ifneq ($(wildcard vendor/sprd),)
        PERFENGINE_PLATFORM_TYPE := unisoc
    else ifneq ($(wildcard vendor/unisoc),)
        PERFENGINE_PLATFORM_TYPE := unisoc
    else
        PERFENGINE_PLATFORM_TYPE := unknown
    endif
endif

ifneq ($(TARGET_COPY_OUT_SYSTEM),)
    include $(SYSMONITOR_BASE_DIR)/vendor/sysmonitor_vendor.mk
endif

ifneq ($(TARGET_COPY_OUT_VENDOR),)
    include $(SYSMONITOR_BASE_DIR)/system/sysmonitor_system.mk
endif

SOONG_CONFIG_NAMESPACES += transsion_sysmonitor
SOONG_CONFIG_transsion_sysmonitor += platform
SOONG_CONFIG_transsion_sysmonitor_platform := $(PERFENGINE_PLATFORM_TYPE)
