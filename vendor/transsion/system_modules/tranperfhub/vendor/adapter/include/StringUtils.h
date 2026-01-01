#pragma once

#include <string>
#include <vector>

namespace vendor {
namespace transsion {
namespace perfhub {
namespace utils {

// ASCII Unit Separator
constexpr char STRING_SEPARATOR = '\x1F';

/**
 * Split string by separator
 */
inline std::vector<std::string> splitStrings(const std::string &str) {
    std::vector<std::string> result;

    if (str.empty()) {
        return result;
    }

    size_t start = 0;
    size_t pos = 0;

    while ((pos = str.find(STRING_SEPARATOR, start)) != std::string::npos) {
        result.emplace_back(str.substr(start, pos - start));
        start = pos + 1;
    }

    // Last segment
    if (start < str.size()) {
        result.emplace_back(str.substr(start));
    } else if (start == str.size()) {
        // Trailing separator
        result.emplace_back("");
    }

    return result;
}

/**
 * Safe get string from vector
 */
inline std::string getStringAt(const std::vector<std::string> &strings, size_t index,
                               const std::string &defaultValue = "") {
    return index < strings.size() ? strings[index] : defaultValue;
}

/**
 * Safe get int from vector
 */
inline int32_t getIntAt(const std::vector<int32_t> &ints, size_t index, int32_t defaultValue = 0) {
    return index < ints.size() ? ints[index] : defaultValue;
}

}    // namespace utils
}    // namespace perfhub
}    // namespace transsion
}    // namespace vendor
