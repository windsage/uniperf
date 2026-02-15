PRODUCT_PACKAGES += \
    perfengine_impl \
    libperfengine-jni \
    libperfengine-client \
    com.transsion.perfengine.xml \
    vendor.transsion.perfengine-V1-ndk \
    vendor.transsion.perfengine-V1-java

PRODUCT_SYSTEM_PROPERTIES += \
    ro.transsion.perfengine.version=1.0.0

ifneq ($(filter eng userdebug,$(TARGET_BUILD_VARIANT)),)
    PRODUCT_SYSTEM_PROPERTIES += \
        persist.transsion.perfengine.debug=1
endif
