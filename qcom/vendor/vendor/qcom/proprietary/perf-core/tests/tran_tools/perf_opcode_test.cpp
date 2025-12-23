/******************************************************************************
  @file    perf_opcode_test.cpp
  @brief   Performance Opcode Testing Tool

  DESCRIPTION
  Streamlined binary for testing MP-CTL opcodes with essential functionality:
  - Unified acquire command with optional duration
  - Merged opcode requests for atomic operation
  - Manual release capability
  - Property query from perfconfigstore.xml
  - Optimized for performance and simplicity

  USAGE
  ./perf_opcode_test acquire [duration_ms] <opcode:value> [opcode:value...]
  ./perf_opcode_test release <handle> [handle...]
  ./perf_opcode_test getprop <prop_name> [default_value]

  EXAMPLES
  # Persistent boost (no auto-release)
  ./perf_opcode_test acquire 0x44004000:400 0x44008000:1

  # Timed boost (3 seconds, auto-release)
  ./perf_opcode_test acquire 3000 0x44004000:400 0x44008000:1

  # Manual release
  ./perf_opcode_test release 634 635

  # Query property from perfconfigstore.xml
  ./perf_opcode_test getprop vendor.perf.gestureflingboost.enable
  ./perf_opcode_test getprop vendor.perf.gestureflingboost.enable false

  ---------------------------------------------------------------------------
******************************************************************************/

#include <dlfcn.h>
#include <errno.h>
#include <inttypes.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/types.h>
#include <unistd.h>

#include <string>
#include <vector>

// Perf library function pointers
typedef int (*perf_lock_acq_func)(int handle, int duration, int list[], int numArgs);
typedef int (*perf_lock_rel_func)(int handle);

// Property query structure and function pointer
#define PROP_VAL_LENGTH 92
typedef struct {
    char value[PROP_VAL_LENGTH];
} PropVal;
typedef PropVal (*perf_get_prop_func)(const char *prop, const char *def_val);

#define MAX_ARGS 32
#define PERF_CLIENT_LIB "libqti-perfd-client.so"
#define PERSISTENT_DURATION 0x7FFFFFFF
#define DEFAULT_PROP_VALUE "unknown"

struct OpcodeGroup {
    uint32_t opcode;
    std::vector<int> values;

    OpcodeGroup() : opcode(0) {}
};

// Global function pointers
static perf_lock_acq_func perf_lock_acq_ptr = nullptr;
static perf_lock_rel_func perf_lock_rel_ptr = nullptr;
static perf_get_prop_func perf_get_prop_ptr = nullptr;
static void *lib_handle = nullptr;

void print_usage(const char *program_name) {
    printf("Simplified Performance Opcode Testing Tool\n");
    printf("=========================================\n\n");
    printf("USAGE:\n");
    printf("  %s acquire [duration_ms] <opcode:value> [opcode:value...]\n", program_name);
    printf("  %s release <handle> [handle...]\n", program_name);
    printf("  %s getprop <prop_name> [default_value]\n\n", program_name);

    printf("ACQUIRE:\n");
    printf("  - Without duration: Persistent boost (manual release required)\n");
    printf("  - With duration: Timed boost (auto-release after specified ms)\n");
    printf("  - Multiple opcodes are merged into single atomic request\n\n");

    printf("GETPROP:\n");
    printf("  - Query property value from perfconfigstore.xml\n");
    printf("  - Optional default value if property not found (default: \"unknown\")\n\n");

    printf("EXAMPLES:\n");
    printf("  # Persistent boost\n");
    printf("  %s acquire 0x44004000:400 0x44008000:1\n\n", program_name);
    printf("  # 3-second boost (auto-release)\n");
    printf("  %s acquire 3000 0x44004000:400 0x44008000:1\n\n", program_name);
    printf("  # Manual release\n");
    printf("  %s release 634\n\n", program_name);
    printf("  # Query property\n");
    printf("  %s getprop vendor.perf.gestureflingboost.enable\n", program_name);
    printf("  %s getprop vendor.perf.gestureflingboost.enable false\n\n", program_name);
}

int load_perf_library() {
    lib_handle = dlopen(PERF_CLIENT_LIB, RTLD_NOW);
    if (!lib_handle) {
        printf("ERROR: Failed to load %s: %s\n", PERF_CLIENT_LIB, dlerror());
        return -1;
    }

    // Load perf_lock_acq function
    perf_lock_acq_ptr = (perf_lock_acq_func)dlsym(lib_handle, "perf_lock_acq");
    if (!perf_lock_acq_ptr) {
        printf("ERROR: Failed to get perf_lock_acq symbol: %s\n", dlerror());
        return -1;
    }

    // Load perf_lock_rel function
    perf_lock_rel_ptr = (perf_lock_rel_func)dlsym(lib_handle, "perf_lock_rel");
    if (!perf_lock_rel_ptr) {
        printf("ERROR: Failed to get perf_lock_rel symbol: %s\n", dlerror());
        return -1;
    }

    // Load perf_get_prop function (optional for getprop command)
    perf_get_prop_ptr = (perf_get_prop_func)dlsym(lib_handle, "perf_get_prop");
    if (!perf_get_prop_ptr) {
        printf("WARNING: perf_get_prop symbol not found, getprop command will be unavailable\n");
    }

    return 0;
}

std::string trim_spaces(const std::string &str) {
    size_t start = 0;
    size_t end = str.length();

    while (start < end && (str[start] == ' ' || str[start] == '\t')) {
        start++;
    }

    while (end > start && (str[end - 1] == ' ' || str[end - 1] == '\t')) {
        end--;
    }

    return str.substr(start, end - start);
}

uint32_t parse_value(const char *str) {
    std::string trimmed = trim_spaces(std::string(str));
    const char *clean_str = trimmed.c_str();

    if (strncmp(clean_str, "0x", 2) == 0 || strncmp(clean_str, "0X", 2) == 0) {
        return (uint32_t)strtoul(clean_str, nullptr, 16);
    }
    return (uint32_t)strtoul(clean_str, nullptr, 10);
}

bool parse_opcode_group(const char *group_str, OpcodeGroup &group) {
    std::string input = trim_spaces(std::string(group_str));
    size_t colon_pos = input.find(':');

    if (colon_pos == std::string::npos) {
        printf("ERROR: Invalid format '%s'. Expected 'opcode:value'\n", group_str);
        return false;
    }

    std::string opcode_str = trim_spaces(input.substr(0, colon_pos));

    group.opcode = parse_value(opcode_str.c_str());

    // Validate opcode value
    if (group.opcode == 0 && opcode_str != "0" && opcode_str != "0x0") {
        printf("ERROR: Invalid opcode '%s'\n", opcode_str.c_str());
        return false;
    }

    std::string values_str = trim_spaces(input.substr(colon_pos + 1));
    if (values_str.empty()) {
        printf("ERROR: No values for opcode 0x%08X\n", group.opcode);
        return false;
    }

    size_t start = 0;
    size_t pos = 0;
    while ((pos = values_str.find(',', start)) != std::string::npos) {
        std::string value_str = trim_spaces(values_str.substr(start, pos - start));
        if (!value_str.empty()) {
            char *endptr;
            long value = strtol(value_str.c_str(), &endptr, 0);
            if (*endptr == '\0') {
                group.values.push_back((int)value);
            } else {
                printf("ERROR: Invalid value '%s'\n", value_str.c_str());
                return false;
            }
        }
        start = pos + 1;
    }

    std::string last_value = trim_spaces(values_str.substr(start));
    if (!last_value.empty()) {
        char *endptr;
        long value = strtol(last_value.c_str(), &endptr, 0);
        if (*endptr == '\0') {
            group.values.push_back((int)value);
        } else {
            printf("ERROR: Invalid value '%s'\n", last_value.c_str());
            return false;
        }
    }

    if (group.values.empty()) {
        printf("ERROR: No valid values for opcode 0x%08X\n", group.opcode);
        return false;
    }

    return true;
}

int acquire_perflock(const std::vector<OpcodeGroup> &groups, int duration) {
    if (groups.empty()) {
        printf("ERROR: No opcode groups provided\n");
        return -1;
    }

    int args[MAX_ARGS];
    int arg_index = 0;

    printf("Acquiring perflock:\n");

    for (size_t i = 0; i < groups.size(); i++) {
        const OpcodeGroup &group = groups[i];

        printf("  Group %zu: Opcode=0x%X, Values=[", i + 1, group.opcode);
        for (size_t j = 0; j < group.values.size(); j++) {
            printf("%d", group.values[j]);
            if (j < group.values.size() - 1) {
                printf(", ");
            }
        }
        printf("]\n");

        if (arg_index >= MAX_ARGS) {
            printf("ERROR: Too many arguments (max %d)\n", MAX_ARGS);
            return -1;
        }
        args[arg_index++] = group.opcode;

        for (size_t j = 0; j < group.values.size(); j++) {
            if (arg_index >= MAX_ARGS) {
                printf("ERROR: Too many arguments (max %d)\n", MAX_ARGS);
                return -1;
            }
            args[arg_index++] = group.values[j];
        }
    }

    const char *duration_str = (duration == PERSISTENT_DURATION) ? "PERSISTENT"
                                   : (std::to_string(duration) + "ms").c_str();
    printf("  Duration: %s\n", duration_str);
    printf("  Total args: %d\n", arg_index);

    int handle = perf_lock_acq_ptr(0, duration, args, arg_index);
    if (handle <= 0) {
        printf("ERROR: perflock failed, handle: %d\n", handle);
        return -1;
    }

    printf("SUCCESS: Handle = %d\n", handle);

    if (duration != PERSISTENT_DURATION) {
        printf("Auto-release in %dms\n", duration);
    } else {
        printf("Persistent lock - use 'release %d' to unlock\n", handle);
    }

    return handle;
}

int release_perflock(int handle) {
    printf("Releasing handle: %d\n", handle);

    int result = perf_lock_rel_ptr(handle);
    if (result != 0) {
        printf("ERROR: Release failed, result: %d\n", result);
        return -1;
    }

    printf("SUCCESS: Released handle %d\n", handle);
    return 0;
}

int query_property(const char *prop_name, const char *default_value) {
    // Check if perf_get_prop function is available
    if (!perf_get_prop_ptr) {
        printf("ERROR: perf_get_prop API not available\n");
        printf("Please ensure perfhal service is running and library is properly loaded\n");
        return -1;
    }

    // Validate property name
    if (!prop_name || strlen(prop_name) == 0) {
        printf("ERROR: Property name cannot be empty\n");
        return -1;
    }

    // Use default value if not provided
    const char *def_val = default_value ? default_value : DEFAULT_PROP_VALUE;

    printf("Querying property: %s\n", prop_name);
    printf("Default value: %s\n", def_val);

    // Call perf_get_prop API
    PropVal result = perf_get_prop_ptr(prop_name, def_val);

    // Check if result is valid
    if (result.value[0] == '\0') {
        printf("Property value: %s (using default)\n", def_val);
    } else {
        printf("Property value: %s\n", result.value);

        // Check if using default value
        if (strcmp(result.value, def_val) == 0) {
            printf("(Note: Property may not exist in perfconfigstore.xml, returned default value)\n");
        }
    }

    return 0;
}

bool is_number(const char *str) {
    if (!str || *str == '\0')
        return false;

    std::string trimmed = trim_spaces(std::string(str));
    if (trimmed.empty())
        return false;

    char *endptr;
    strtol(trimmed.c_str(), &endptr, 10);
    return *endptr == '\0';
}

int main(int argc, char *argv[]) {
    printf("=== Simplified Performance Opcode Tester ===\n\n");

    if (argc < 2) {
        print_usage(argv[0]);
        return 1;
    }

    if (load_perf_library() != 0) {
        return 1;
    }

    const char *command = argv[1];
    int result = 0;

    if (strcmp(command, "acquire") == 0) {
        if (argc < 3) {
            printf("ERROR: acquire requires at least one opcode\n");
            print_usage(argv[0]);
            return 1;
        }

        int duration = PERSISTENT_DURATION;
        int opcode_start_index = 2;

        if (is_number(argv[2])) {
            duration = atoi(argv[2]);
            if (duration <= 0) {
                printf("ERROR: Invalid duration %d\n", duration);
                return 1;
            }
            opcode_start_index = 3;

            if (argc < 4) {
                printf("ERROR: No opcodes specified after duration\n");
                return 1;
            }
        }

        std::vector<OpcodeGroup> groups;
        for (int i = opcode_start_index; i < argc; i++) {
            OpcodeGroup group;
            if (!parse_opcode_group(argv[i], group)) {
                return 1;
            }
            groups.push_back(group);
        }

        if (groups.empty()) {
            printf("ERROR: No valid opcode groups provided\n");
            return 1;
        }

        result = acquire_perflock(groups, duration);
        if (result < 0) {
            return 1;
        }

    } else if (strcmp(command, "release") == 0) {
        if (argc < 3) {
            printf("ERROR: release requires at least one handle\n");
            print_usage(argv[0]);
            return 1;
        }

        for (int i = 2; i < argc; i++) {
            int handle = atoi(argv[i]);
            if (handle <= 0) {
                printf("ERROR: Invalid handle '%s'\n", argv[i]);
                continue;
            }

            result = release_perflock(handle);
            if (i < argc - 1) {
                printf("\n");
            }
        }

    } else if (strcmp(command, "getprop") == 0) {
        if (argc < 3) {
            printf("ERROR: getprop requires property name\n");
            print_usage(argv[0]);
            return 1;
        }

        const char *prop_name = argv[2];
        const char *default_value = (argc >= 4) ? argv[3] : nullptr;

        result = query_property(prop_name, default_value);
        if (result < 0) {
            return 1;
        }

    } else {
        printf("ERROR: Unknown command '%s'\n", command);
        print_usage(argv[0]);
        return 1;
    }

    return 0;
}
