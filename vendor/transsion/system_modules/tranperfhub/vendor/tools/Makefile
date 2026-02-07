# tools/Makefile

# Project root directory
PROJECT_ROOT := ..

# Python script
GEN_SCRIPT := gen_platform_mapping.py

# Paths
CONFIGS_DIR := $(PROJECT_ROOT)/configs
INCLUDE_DIR := $(PROJECT_ROOT)/adapter/include

# Source XML files
QCOM_XML := $(CONFIGS_DIR)/platform_mappings_qcom.xml
MTK_XML := $(CONFIGS_DIR)/platform_mappings_mtk.xml

# Output include files
QCOM_INC := $(INCLUDE_DIR)/QcomMappings.inc
MTK_INC := $(INCLUDE_DIR)/MtkMappings.inc

.PHONY: all qcom mtk clean help

# Default target
all: qcom mtk

# Generate QCOM mappings
qcom: $(QCOM_INC)

$(QCOM_INC): $(QCOM_XML) $(GEN_SCRIPT)
	@echo "Generating QCOM hardcoded mappings..."
	python3 $(GEN_SCRIPT) -i $(QCOM_XML) -o $(QCOM_INC)

# Generate MTK mappings
mtk: $(MTK_INC)

$(MTK_INC): $(MTK_XML) $(GEN_SCRIPT)
	@echo "Generating MTK hardcoded mappings..."
	python3 $(GEN_SCRIPT) -i $(MTK_XML) -o $(MTK_INC)

# Clean generated files
clean:
	@echo "Cleaning generated files..."
	rm -f $(QCOM_INC) $(MTK_INC)

# Help
help:
	@echo "PlatformMapping Generator Makefile"
	@echo ""
	@echo "Targets:"
	@echo "  all   - Generate all platform mappings (default)"
	@echo "  qcom  - Generate QCOM mappings only"
	@echo "  mtk   - Generate MTK mappings only"
	@echo "  clean - Remove generated .inc files"
	@echo "  help  - Show this help message"