# ============================================================
# PerfEngine - Vendor 分区配置
# ============================================================

$(info [PerfEngine] Loading Vendor partition configuration)

# ==================== Vendor 模块聚合 ====================

PRODUCT_PACKAGES += \
    perfengine_vendor

# ==================== 配置文件复制 ====================

# 复制平台配置文件到 /data/vendor/perfengine/
ifneq ($(PERFENGINE_CONFIG_PATH),)
    PRODUCT_COPY_FILES += \
        $(PERFENGINE_CONFIG_PATH):$(TARGET_COPY_OUT_VENDOR)/etc/perfengine/perfengine_params.xml

    $(info [PerfEngine] Config will be copied to: /vendor/etc/perfengine/perfengine_params.xml)
endif

# ==================== Vendor 属性配置 ====================

# 平台信息
PRODUCT_VENDOR_PROPERTIES += \
    vendor.transsion.perfengine.platform=$(PERFENGINE_PLATFORM_TYPE) \
    vendor.transsion.perfengine.chip=$(PERFENGINE_CHIP_NAME)

# ==================== SELinux 策略 ====================

BOARD_SEPOLICY_DIRS += \
    vendor/transsion/vendor_modules/usf/perfengine/sepolicy/vendor

# ==================== Vendor 额外依赖 ====================

# 如果需要额外的 Vendor 模块依赖，在这里添加
# PRODUCT_PACKAGES += \
#     some_future_vendor_module

# ==================== 调试配置 ====================

# Debug 版本的额外配置
ifneq ($(filter eng userdebug,$(TARGET_BUILD_VARIANT)),)
    PRODUCT_VENDOR_PROPERTIES += \
        persist.vendor.transsion.perfengine.debug=1 \
        persist.vendor.transsion.perfengine.log_level=verbose
endif

PRODUCT_COPY_FILES += \
    vendor/transsion/vendor_modules/usf/perfengine/vendor/configs/platform_mappings_qcom.xml:$(TARGET_COPY_OUT_VENDOR)/etc/perfengine/platform_mappings_qcom.xml \
    vendor/transsion/vendor_modules/usf/perfengine/vendor/configs/platform_mappings_mtk.xml:$(TARGET_COPY_OUT_VENDOR)/etc/perfengine/platform_mappings_mtk.xml
