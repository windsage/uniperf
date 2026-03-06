PERFENGINE_BASE_DIR := vendor/transsion/vendor_modules/usf/perfengine
PERFENGINE_CONFIG_DIR := $(PERFENGINE_BASE_DIR)/vendor/configs

ifndef PERFENGINE_PLATFORM_TYPE
    ifneq ($(wildcard vendor/qcom),)
        PERFENGINE_PLATFORM_TYPE := qcom
    else ifneq ($(wildcard vendor/mediatek),)
        PERFENGINE_PLATFORM_TYPE := mtk
    else
        PERFENGINE_PLATFORM_TYPE := unknown
    endif
endif

ifneq ($(TARGET_COPY_OUT_SYSTEM),)
    $(call inherit-product-if-exists, $(PERFENGINE_BASE_DIR)/system/perfengine_system.mk)
endif

ifneq ($(TARGET_COPY_OUT_VENDOR),)
    $(call inherit-product-if-exists, $(PERFENGINE_BASE_DIR)/vendor/perfengine_vendor.mk)
endif
