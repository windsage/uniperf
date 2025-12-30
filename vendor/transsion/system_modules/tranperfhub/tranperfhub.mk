# vendor/transsion/system_modules/tranperfhub/tranperfhub.mk

# ============================================================
# TranPerfHub - 自动化配置脚本
# ============================================================
#
# 功能:
#   1. 自动检测平台类型 (QCOM/MTK/UNISOC)
#   2. 自动识别芯片型号
#   3. 自动选择对应的配置文件
#   4. 自动编译 System 或 Vendor 分区模块
#
# 使用方式:
#   在 device.mk 中添加:
#   $(call inherit-product, vendor/transsion/system_modules/tranperfhub/tranperfhub.mk)
#
# ============================================================

# ==================== 平台检测 ====================

TRANPERFHUB_PLATFORM_TYPE := unknown
TRANPERFHUB_CHIP_NAME := unknown

# QCOM 平台检测
ifneq ($(filter msm% kona lahaina taro kalama volcano pineapple sm% sdm%,$(TARGET_BOARD_PLATFORM)),)
    TRANPERFHUB_PLATFORM_TYPE := qcom
    TRANPERFHUB_CHIP_NAME := $(TARGET_BOARD_PLATFORM)
endif

# MTK 平台检测
ifneq ($(filter mt%,$(TARGET_BOARD_PLATFORM)),)
    TRANPERFHUB_PLATFORM_TYPE := mtk
    TRANPERFHUB_CHIP_NAME := $(TARGET_BOARD_PLATFORM)
endif

# UNISOC 平台检测
ifneq ($(filter ums% unisoc%,$(TARGET_BOARD_PLATFORM)),)
    TRANPERFHUB_PLATFORM_TYPE := unisoc
    TRANPERFHUB_CHIP_NAME := $(TARGET_BOARD_PLATFORM)
endif

# 调试输出
$(info [TranPerfHub] Platform detected: $(TRANPERFHUB_PLATFORM_TYPE) - $(TRANPERFHUB_CHIP_NAME))

# ==================== 配置文件路径 ====================

TRANPERFHUB_BASE_DIR := vendor/transsion/system_modules/tranperfhub
TRANPERFHUB_CONFIG_DIR := $(TRANPERFHUB_BASE_DIR)/vendor/configs

# 根据平台选择配置目录
ifeq ($(TRANPERFHUB_PLATFORM_TYPE),qcom)
    TRANPERFHUB_CHIP_CONFIG_DIR := $(TRANPERFHUB_CONFIG_DIR)/qcom/$(TRANPERFHUB_CHIP_NAME)
    TRANPERFHUB_DEFAULT_CONFIG_DIR := $(TRANPERFHUB_CONFIG_DIR)/qcom/default
else ifeq ($(TRANPERFHUB_PLATFORM_TYPE),mtk)
    TRANPERFHUB_CHIP_CONFIG_DIR := $(TRANPERFHUB_CONFIG_DIR)/mtk/$(TRANPERFHUB_CHIP_NAME)
    TRANPERFHUB_DEFAULT_CONFIG_DIR := $(TRANPERFHUB_CONFIG_DIR)/mtk/default
else ifeq ($(TRANPERFHUB_PLATFORM_TYPE),unisoc)
    TRANPERFHUB_CHIP_CONFIG_DIR := $(TRANPERFHUB_CONFIG_DIR)/unisoc/$(TRANPERFHUB_CHIP_NAME)
    TRANPERFHUB_DEFAULT_CONFIG_DIR := $(TRANPERFHUB_CONFIG_DIR)/unisoc/default
else
    $(warning [TranPerfHub] Unknown platform: $(TARGET_BOARD_PLATFORM))
    TRANPERFHUB_CHIP_CONFIG_DIR :=
    TRANPERFHUB_DEFAULT_CONFIG_DIR :=
endif

# 查找配置文件 使用芯片特定配置
TRANPERFHUB_CONFIG_PATH :=
ifneq ($(TRANPERFHUB_CHIP_CONFIG_DIR),)
    ifneq ($(wildcard $(TRANPERFHUB_CHIP_CONFIG_DIR)/perfhub_params.xml),)
        # 找到芯片特定配置
        TRANPERFHUB_CONFIG_PATH := $(TRANPERFHUB_CHIP_CONFIG_DIR)/perfhub_params.xml
        $(info [TranPerfHub] Using chip-specific config: $(TRANPERFHUB_CHIP_NAME))
    else ifneq ($(wildcard $(TRANPERFHUB_DEFAULT_CONFIG_DIR)/perfhub_params.xml),)
        # 使用平台默认配置
        TRANPERFHUB_CONFIG_PATH := $(TRANPERFHUB_DEFAULT_CONFIG_DIR)/perfhub_params.xml
        $(warning [TranPerfHub] Chip config not found, using default config)
    else
        $(warning [TranPerfHub] No config found for platform: $(TRANPERFHUB_PLATFORM_TYPE))
    endif
endif

# ==================== System 分区编译 ====================

ifneq ($(TARGET_COPY_OUT_SYSTEM),)
    $(info [TranPerfHub] Building System partition modules)

    # System 分区详细配置
    $(call inherit-product-if-exists, \
        $(TRANPERFHUB_BASE_DIR)/system/tranperfhub_system.mk)
endif

# ==================== Vendor 分区编译 ====================

ifneq ($(TARGET_COPY_OUT_VENDOR),)
    $(info [TranPerfHub] Building Vendor partition modules)

    # Vendor 分区详细配置
    $(call inherit-product-if-exists, \
        $(TRANPERFHUB_BASE_DIR)/vendor/tranperfhub_vendor.mk)
endif

# ==================== 调试信息 ====================

ifneq ($(filter eng userdebug,$(TARGET_BUILD_VARIANT)),)
    $(info ========================================)
    $(info [TranPerfHub] Configuration Summary)
    $(info ========================================)
    $(info Platform Type:    $(TRANPERFHUB_PLATFORM_TYPE))
    $(info Chip Name:        $(TRANPERFHUB_CHIP_NAME))
    $(info Config Path:      $(TRANPERFHUB_CONFIG_PATH))
    $(info System Modules:   $(if $(filter tranperfhub_system,$(PRODUCT_PACKAGES)),ENABLED,DISABLED))
    $(info Vendor Modules:   $(if $(filter tranperfhub_vendor,$(PRODUCT_PACKAGES)),ENABLED,DISABLED))
    $(info ========================================)
endif
