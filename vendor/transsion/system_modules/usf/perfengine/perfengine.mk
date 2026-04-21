PERFENGINE_BASE_DIR := vendor/transsion/system_modules/usf/perfengine

ifneq ($(TARGET_COPY_OUT_SYSTEM),)
    $(call inherit-product-if-exists, $(PERFENGINE_BASE_DIR)/system/perfengine_system.mk)
endif
