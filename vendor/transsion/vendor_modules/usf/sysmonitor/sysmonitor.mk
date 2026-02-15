SYSMONITOR_BASE_DIR := vendor/transsion/vendor_modules/usf/sysmonitor

ifneq ($(TARGET_COPY_OUT_SYSTEM),)
    $(call inherit-product-if-exists, $(SYSMONITOR_BASE_DIR)/system/sysmonitor_system.mk)
endif

ifneq ($(TARGET_COPY_OUT_VENDOR),)
    $(call inherit-product-if-exists, $(SYSMONITOR_BASE_DIR)/vendor/sysmonitor_vendor.mk)
endif
