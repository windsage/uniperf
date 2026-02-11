PRODUCT_PACKAGES += \
    libperfengine-adapter \
    vendor.transsion.perfengine-V1-ndk

PERFENGINE_PLATFORM_TYPE := unknown
PERFENGINE_CHIP_NAME := unknown

ifneq ($(filter msm% kona lahaina taro kalama volcano pineapple sm% sdm%,$(TARGET_BOARD_PLATFORM)),)
    PERFENGINE_PLATFORM_TYPE := qcom
    PERFENGINE_CHIP_NAME := $(TARGET_BOARD_PLATFORM)
endif

ifneq ($(filter mt%,$(TARGET_BOARD_PLATFORM)),)
    PERFENGINE_PLATFORM_TYPE := mtk
    PERFENGINE_CHIP_NAME := $(TARGET_BOARD_PLATFORM)
endif

ifneq ($(filter ums% unisoc%,$(TARGET_BOARD_PLATFORM)),)
    PERFENGINE_PLATFORM_TYPE := unisoc
    PERFENGINE_CHIP_NAME := $(TARGET_BOARD_PLATFORM)
endif

PERFENGINE_BASE_DIR := vendor/transsion/vendor_modules/usf/perfengine
PERFENGINE_CONFIG_DIR := $(PERFENGINE_BASE_DIR)/vendor/configs

ifeq ($(PERFENGINE_PLATFORM_TYPE),qcom)
    PERFENGINE_CHIP_CONFIG_DIR := $(PERFENGINE_CONFIG_DIR)/qcom/$(PERFENGINE_CHIP_NAME)
    PERFENGINE_MAPPING_PATH := $(PERFENGINE_CONFIG_DIR)/platform_mappings_qcom.xml
else ifeq ($(PERFENGINE_PLATFORM_TYPE),mtk)
    PERFENGINE_CHIP_CONFIG_DIR := $(PERFENGINE_CONFIG_DIR)/mtk/$(PERFENGINE_CHIP_NAME)
    PERFENGINE_MAPPING_PATH := $(PERFENGINE_CONFIG_DIR)/platform_mappings_mtk.xml
else
    PERFENGINE_CHIP_CONFIG_DIR :=
    PERFENGINE_MAPPING_PATH :=
endif

PERFENGINE_CONFIG_PATH :=
ifneq ($(PERFENGINE_CHIP_CONFIG_DIR),)
    ifneq ($(wildcard $(PERFENGINE_CHIP_CONFIG_DIR)/perfengine_params.xml),)
        PERFENGINE_CONFIG_PATH := $(PERFENGINE_CHIP_CONFIG_DIR)/perfengine_params.xml
    endif
endif

PRODUCT_VENDOR_PROPERTIES += \
    vendor.transsion.perfengine.platform=$(PERFENGINE_PLATFORM_TYPE) \
    vendor.transsion.perfengine.chip=$(PERFENGINE_CHIP_NAME)

ifneq ($(filter eng userdebug,$(TARGET_BUILD_VARIANT)),)
    PRODUCT_VENDOR_PROPERTIES += \
        persist.vendor.transsion.perfengine.debug=1
endif
