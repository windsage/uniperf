# ============================================================
# PerfEngine - System 分区配置
# ============================================================

$(info [PerfEngine] Loading System partition configuration)

# ==================== System 模块聚合 ====================

PRODUCT_PACKAGES += \
    perfengine_system

# ==================== System 属性配置 ====================

# PerfEngine 版本信息
PRODUCT_SYSTEM_PROPERTIES += \
    ro.transsion.perfengine.version=1.0.0 \
    ro.transsion.perfengine.build_date=$(shell date +%Y%m%d)

# ==================== Framework JAR 配置 ====================

# 如果需要将 PerfEngine 实现加入 BOOTCLASSPATH
# PRODUCT_BOOT_JARS += \
#     perfengine_impl

# ==================== System 额外依赖 ====================

# 如果需要额外的 System 模块依赖，在这里添加
# PRODUCT_PACKAGES += \
#     some_future_system_module

# ==================== 调试配置 ====================

# Debug 版本的额外配置
ifneq ($(filter eng userdebug,$(TARGET_BUILD_VARIANT)),)
    PRODUCT_SYSTEM_PROPERTIES += \
        persist.transsion.perfengine.debug=1
endif
