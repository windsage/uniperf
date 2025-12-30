# ============================================================
# TranPerfHub - System 分区配置
# ============================================================

$(info [TranPerfHub] Loading System partition configuration)

# ==================== System 模块聚合 ====================

PRODUCT_PACKAGES += \
    tranperfhub_system

# ==================== System 属性配置 ====================

# TranPerfHub 版本信息
PRODUCT_SYSTEM_PROPERTIES += \
    ro.transsion.perfhub.version=1.0.0 \
    ro.transsion.perfhub.build_date=$(shell date +%Y%m%d)

# ==================== Framework JAR 配置 ====================

# 如果需要将 TranPerfHub 实现加入 BOOTCLASSPATH
# PRODUCT_BOOT_JARS += \
#     tranperfhub_impl

# ==================== System 额外依赖 ====================

# 如果需要额外的 System 模块依赖，在这里添加
# PRODUCT_PACKAGES += \
#     some_future_system_module

# ==================== 调试配置 ====================

# Debug 版本的额外配置
ifneq ($(filter eng userdebug,$(TARGET_BUILD_VARIANT)),)
    PRODUCT_SYSTEM_PROPERTIES += \
        persist.transsion.perfhub.debug=1
endif
