PRODUCT_PACKAGES += \
    libperfengine-jni \
    libperfengine-client \
    PerfEngineDemo \
    vendor.transsion.perfengine-V1-ndk \
    vendor.transsion.perfengine-V1-java \
    privapp-permissions-perfengine \
    PerfEnginePermissionOverlay

PRODUCT_SYSTEM_PROPERTIES += \
    ro.transsion.perfengine.version=1.0.0

ifneq ($(filter eng userdebug,$(TARGET_BUILD_VARIANT)),)
    PRODUCT_SYSTEM_PROPERTIES += \
        persist.transsion.perfengine.debug=1
endif

DEVICE_FRAMEWORK_COMPATIBILITY_MATRIX_FILE += \
    vendor/transsion/system_modules/usf/perfengine/framework_compatibility_matrix_perfengine.xml
