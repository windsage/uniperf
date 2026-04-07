PRODUCT_PACKAGES += \
    libperfengine-client \
    libperfengine-adapter \
    vendor.transsion.perfengine-V1-ndk

# -------------------------------------------------------------
# 2. 芯片名称标准化
#    从 TARGET_BOARD_PLATFORM 提取可识别的芯片名，
#    用于后续档位推导。
# -------------------------------------------------------------
PERFENGINE_CHIP_NAME := unknown

ifneq ($(filter msm% kona lahaina taro kalama volcano pineapple sm% sdm%,$(TARGET_BOARD_PLATFORM)),)
    PERFENGINE_CHIP_NAME := $(TARGET_BOARD_PLATFORM)
endif

ifneq ($(filter mt%,$(TARGET_BOARD_PLATFORM)),)
    PERFENGINE_CHIP_NAME := $(TARGET_BOARD_PLATFORM)
endif

ifneq ($(filter ums% unisoc%,$(TARGET_BOARD_PLATFORM)),)
    PERFENGINE_CHIP_NAME := $(TARGET_BOARD_PLATFORM)
endif

# -------------------------------------------------------------
# 3. 档位自动推导（芯片 → profile 对照表）
#    优先级：手动设置 > 芯片名匹配
#
#    flagship : 旗舰级  resourceMask=0xFF，完整阶段链+增强追踪
#    main     : 主流级  resourceMask=0x7F，完整阶段链
#    entry    : 入门级  resourceMask=0x0F，精简阶段链（默认）
# -------------------------------------------------------------
ifndef PERFENGINE_PROFILE_TYPE

    # -- There is currently no Snapdragon flagship chip available. --
#    ifneq ($(filter kona lahaina taro kalama pineapple sm8450 sm8475 sm8550 sm8650,$(PERFENGINE_CHIP_NAME)),)
#        PERFENGINE_PROFILE_TYPE := flagship

    # -- QCOM main --
    # Snapdragon 7 系列 / 中高端 SoC
    ifneq ($(filter volcano ,$(PERFENGINE_CHIP_NAME)),)
        PERFENGINE_PROFILE_TYPE := main

    # -- MTK flagship --
    # Dimensity 9000 8000 系列
    else ifneq ($(filter mt6985 mt6896 mt6897 mt6899 ,$(PERFENGINE_CHIP_NAME)),)
        PERFENGINE_PROFILE_TYPE := flagship

    # -- MTK main --
    # Dimensity 7300 7400 系列
    else ifneq ($(filter mt6858 mt6878 ,$(PERFENGINE_CHIP_NAME)),)
        PERFENGINE_PROFILE_TYPE := main

    # -- Unisoc main --
    else ifneq ($(filter ums9610 ums9215,$(PERFENGINE_CHIP_NAME)),)
        PERFENGINE_PROFILE_TYPE := main

    # -- unkown type is entry --
    else
        PERFENGINE_PROFILE_TYPE := entry
    endif

endif
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

ifneq ($(PERFENGINE_MAPPING_PATH),)
    PRODUCT_COPY_FILES += \
        $(PERFENGINE_MAPPING_PATH):$(TARGET_COPY_OUT_VENDOR)/etc/perfengine/$(notdir $(PERFENGINE_MAPPING_PATH))
endif

ifneq ($(PERFENGINE_CONFIG_PATH),)
    PRODUCT_COPY_FILES += \
        $(PERFENGINE_CONFIG_PATH):$(TARGET_COPY_OUT_VENDOR)/etc/perfengine/perfengine_params.xml
endif

$(warning ############### PERFENGINE DEBUG ###############)
$(warning PLATFORM: $(PERFENGINE_PLATFORM_TYPE))
$(warning CHIP: $(PERFENGINE_CHIP_NAME))
$(warning PROFILE: $(PERFENGINE_PROFILE_TYPE))
$(warning FULL_CONFIG_DIR: $(PERFENGINE_CHIP_CONFIG_DIR))
$(warning MAPPING_FILE: $(PERFENGINE_MAPPING_PATH))
$(warning CONFIG_FILE: $(PERFENGINE_CONFIG_PATH))
$(warning ################################################)
