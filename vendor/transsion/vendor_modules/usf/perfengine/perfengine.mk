# vendor/transsion/vendor_modules/usf/perfengine/perfengine.mk

# ============================================================
# PerfEngine - 自动化配置脚本
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
#   $(call inherit-product, vendor/transsion/vendor_modules/usf/perfengine/perfengine.mk)
#
# ============================================================

# ==================== 平台检测 ====================

PERFENGINE_PLATFORM_TYPE := unknown
PERFENGINE_CHIP_NAME := unknown

# QCOM 平台检测
ifneq ($(filter msm% kona lahaina taro kalama volcano pineapple sm% sdm%,$(TARGET_BOARD_PLATFORM)),)
    PERFENGINE_PLATFORM_TYPE := qcom
    PERFENGINE_CHIP_NAME := $(TARGET_BOARD_PLATFORM)
endif

# MTK 平台检测
ifneq ($(filter mt%,$(TARGET_BOARD_PLATFORM)),)
    PERFENGINE_PLATFORM_TYPE := mtk
    PERFENGINE_CHIP_NAME := $(TARGET_BOARD_PLATFORM)
endif

# UNISOC 平台检测
ifneq ($(filter ums% unisoc%,$(TARGET_BOARD_PLATFORM)),)
    PERFENGINE_PLATFORM_TYPE := unisoc
    PERFENGINE_CHIP_NAME := $(TARGET_BOARD_PLATFORM)
endif

# 调试输出
$(info [PerfEngine] Platform detected: $(PERFENGINE_PLATFORM_TYPE) - $(PERFENGINE_CHIP_NAME))

# ==================== 配置文件路径 ====================

PERFENGINE_BASE_DIR := vendor/transsion/vendor_modules/usf/perfengine
PERFENGINE_CONFIG_DIR := $(PERFENGINE_BASE_DIR)/vendor/configs

# 根据平台选择配置目录
ifeq ($(PERFENGINE_PLATFORM_TYPE),qcom)
    PERFENGINE_CHIP_CONFIG_DIR := $(PERFENGINE_CONFIG_DIR)/qcom/$(PERFENGINE_CHIP_NAME)
    PERFENGINE_DEFAULT_CONFIG_DIR := $(PERFENGINE_CONFIG_DIR)/qcom/default
else ifeq ($(PERFENGINE_PLATFORM_TYPE),mtk)
    PERFENGINE_CHIP_CONFIG_DIR := $(PERFENGINE_CONFIG_DIR)/mtk/$(PERFENGINE_CHIP_NAME)
    PERFENGINE_DEFAULT_CONFIG_DIR := $(PERFENGINE_CONFIG_DIR)/mtk/default
else ifeq ($(PERFENGINE_PLATFORM_TYPE),unisoc)
    PERFENGINE_CHIP_CONFIG_DIR := $(PERFENGINE_CONFIG_DIR)/unisoc/$(PERFENGINE_CHIP_NAME)
    PERFENGINE_DEFAULT_CONFIG_DIR := $(PERFENGINE_CONFIG_DIR)/unisoc/default
else
    $(warning [PerfEngine] Unknown platform: $(TARGET_BOARD_PLATFORM))
    PERFENGINE_CHIP_CONFIG_DIR :=
    PERFENGINE_DEFAULT_CONFIG_DIR :=
endif

# 查找配置文件 使用芯片特定配置
PERFENGINE_CONFIG_PATH :=
ifneq ($(PERFENGINE_CHIP_CONFIG_DIR),)
    ifneq ($(wildcard $(PERFENGINE_CHIP_CONFIG_DIR)/perfengine_params.xml),)
        # 找到芯片特定配置
        PERFENGINE_CONFIG_PATH := $(PERFENGINE_CHIP_CONFIG_DIR)/perfengine_params.xml
        $(info [PerfEngine] Using chip-specific config: $(PERFENGINE_CHIP_NAME))
    else ifneq ($(wildcard $(PERFENGINE_DEFAULT_CONFIG_DIR)/perfengine_params.xml),)
        # 使用平台默认配置
        PERFENGINE_CONFIG_PATH := $(PERFENGINE_DEFAULT_CONFIG_DIR)/perfengine_params.xml
        $(warning [PerfEngine] Chip config not found, using default config)
    else
        $(warning [PerfEngine] No config found for platform: $(PERFENGINE_PLATFORM_TYPE))
    endif
endif

# ==================== System 分区编译 ====================

ifneq ($(TARGET_COPY_OUT_SYSTEM),)
    $(info [PerfEngine] Building System partition modules)

    # System 分区详细配置
    $(call inherit-product-if-exists, \
        $(PERFENGINE_BASE_DIR)/system/perfengine_system.mk)
endif

# ==================== Vendor 分区编译 ====================

ifneq ($(TARGET_COPY_OUT_VENDOR),)
    $(info [PerfEngine] Building Vendor partition modules)

    # Vendor 分区详细配置
    $(call inherit-product-if-exists, \
        $(PERFENGINE_BASE_DIR)/vendor/perfengine_vendor.mk)
endif

# ==================== 调试信息 ====================

ifneq ($(filter eng userdebug,$(TARGET_BUILD_VARIANT)),)
    $(info ========================================)
    $(info [PerfEngine] Configuration Summary)
    $(info ========================================)
    $(info Platform Type:    $(PERFENGINE_PLATFORM_TYPE))
    $(info Chip Name:        $(PERFENGINE_CHIP_NAME))
    $(info Config Path:      $(PERFENGINE_CONFIG_PATH))
    $(info System Modules:   $(if $(filter perfengine_system,$(PRODUCT_PACKAGES)),ENABLED,DISABLED))
    $(info Vendor Modules:   $(if $(filter perfengine_vendor,$(PRODUCT_PACKAGES)),ENABLED,DISABLED))
    $(info ========================================)
endif
