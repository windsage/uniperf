ifeq ($(TARGET_FWK_SUPPORTS_FULL_VALUEADDS),true)

PRODUCT_PACKAGES += \
    perfservice \
    vendor.qti.hardware.perf@2.0.vendor \
    vendor.qti.hardware.perf@2.1.vendor \
    vendor.qti.hardware.perf@2.2.vendor \
    vendor.qti.hardware.perf@2.3.vendor \
    vendor.qti.hardware.perf2.vendor \

ifneq ($(TARGET_BOARD_AUTO),true)
PRODUCT_PACKAGES += \
     vendor.qti.MemHal.vendor \
     libqti-MemHal-client-system \
     MemHalTest-system
endif


# Preloading QPerformance jar to ensure faster perflocks in Boost Framework
PRODUCT_BOOT_JARS += QPerformance

PRODUCT_PACKAGES += \
    QPerformance \
    libqti_performance \
    libqti-perfd-client_system\

endif

ifeq ($(TARGET_SUPPORTS_WEAR_OS),true)

PRODUCT_PACKAGES += \
    perfservice \
    vendor.qti.hardware.perf@2.0.vendor \
    vendor.qti.hardware.perf@2.1.vendor \
    vendor.qti.hardware.perf@2.2.vendor \
    vendor.qti.hardware.perf@2.3.vendor \
    vendor.qti.hardware.perf2.vendor \

PRODUCT_PACKAGES += \
     libqti_performance \
     libqti-perfd-client_system

endif
