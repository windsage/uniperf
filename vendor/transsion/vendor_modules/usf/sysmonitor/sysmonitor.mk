SYSMONITOR_BASE_DIR := vendor/transsion/vendor_modules/usf/sysmonitor

ifneq ($(TARGET_COPY_OUT_SYSTEM),)
    include $(SYSMONITOR_BASE_DIR)/vendor/sysmonitor_vendor.mk
endif

ifneq ($(TARGET_COPY_OUT_VENDOR),)
    include $(SYSMONITOR_BASE_DIR)/system/sysmonitor_system.mk
endif
