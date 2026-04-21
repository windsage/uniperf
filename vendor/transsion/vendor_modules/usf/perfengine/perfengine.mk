PERFENGINE_BASE_DIR := vendor/transsion/vendor_modules/usf/perfengine
PERFENGINE_CONFIG_DIR := $(PERFENGINE_BASE_DIR)/vendor/configs

# -------------------------------------------------------------
# 1. 平台类型自动推导
#    优先级：手动设置 > vendor 目录探测
# -------------------------------------------------------------
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

ifneq ($(TARGET_COPY_OUT_VENDOR),)
    $(call inherit-product-if-exists, $(PERFENGINE_BASE_DIR)/vendor/perfengine_vendor.mk)
endif

SOONG_CONFIG_NAMESPACES += transsion_perfengine
SOONG_CONFIG_transsion_perfengine += platform
SOONG_CONFIG_transsion_perfengine_platform := $(PERFENGINE_PLATFORM_TYPE)
SOONG_CONFIG_transsion_perfengine += profile
SOONG_CONFIG_transsion_perfengine_profile := $(PERFENGINE_PROFILE_TYPE)
