$(info [PerfEngine] Loading Vendor partition configuration)

PRODUCT_PACKAGES += \
    libperfengine-adapter \
    vendor.transsion.perfengine-V1-ndk

ifneq ($(PERFENGINE_CONFIG_PATH),)
    PRODUCT_COPY_FILES += \
        $(PERFENGINE_CONFIG_PATH):$(TARGET_COPY_OUT_VENDOR)/etc/perfengine/perfengine_params.xml
endif

ifneq ($(PERFENGINE_MAPPING_PATH),)
    ifeq ($(PERFENGINE_PLATFORM_TYPE),qcom)
        PRODUCT_COPY_FILES += \
            $(PERFENGINE_MAPPING_PATH):$(TARGET_COPY_OUT_VENDOR)/etc/perfengine/platform_mappings_qcom.xml
    else ifeq ($(PERFENGINE_PLATFORM_TYPE),mtk)
        PRODUCT_COPY_FILES += \
            $(PERFENGINE_MAPPING_PATH):$(TARGET_COPY_OUT_VENDOR)/etc/perfengine/platform_mappings_mtk.xml
    endif
endif

PRODUCT_VENDOR_PROPERTIES += \
    vendor.transsion.perfengine.platform=$(PERFENGINE_PLATFORM_TYPE) \
    vendor.transsion.perfengine.chip=$(PERFENGINE_CHIP_NAME)

ifneq ($(filter eng userdebug,$(TARGET_BUILD_VARIANT)),)
    PRODUCT_VENDOR_PROPERTIES += \
        persist.vendor.transsion.perfengine.debug=1
endif
