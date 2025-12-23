#!/system/bin/sh

###############################################################################
# Performance Opcode Test Script - Simple Example
#
# This script demonstrates how to use the perf_opcode_test binary
# You can modify this script to test different opcode combinations
#
###############################################################################

# Binary path
PERF_TEST_BINARY="/vendor/bin/perf_opcode_test"

echo "=== Simplified Performance Opcode Test Examples ==="
echo ""

# Check if binary exists
if [ ! -f "$PERF_TEST_BINARY" ]; then
    echo "ERROR: Binary not found: $PERF_TEST_BINARY"
    echo "Please build and install first:"
    echo "  mm -j8"
    echo "  adb push out/target/product/xxx/vendor/bin/perf_opcode_test /vendor/bin/"
    echo "  adb shell chmod +x /vendor/bin/perf_opcode_test"
    exit 1
fi

echo "Binary found: $PERF_TEST_BINARY"
echo ""

# Function to wait and show separator
wait_separator() {
    echo "----------------------------------------"
    sleep 1
}

# Example 1: Test custom TRAN_PERF opcodes
echo "=== Example 1: Custom TRAN_PERF Opcodes ==="
echo "Testing TRAN_INPUT_BOOST_DURATION_MS (3 seconds auto-release):"
$PERF_TEST_BINARY acquire 3000 0x44004000:400
wait_separator

echo "Testing TRAN_DISPLAY_OFF_CANCEL_CTRL (2 seconds):"
$PERF_TEST_BINARY acquire 2000 0x44008000:1
wait_separator

# Example 2: Multi-opcode merged test
echo "=== Example 2: Multi-Opcode Merged Test ==="
echo "CPU frequency + GPU power (merged, 4 seconds):"
$PERF_TEST_BINARY acquire 4000 0x40800000:1900000 0x42800000:1
wait_separator

echo "Complex performance boost (CPU + GPU + Cores + Scheduler, 5 seconds):"
$PERF_TEST_BINARY acquire 5000 0x40800000:2000000 0x42800000:0 0x41000000:6 0x40C00000:2
wait_separator

# Example 3: Persistent locks demonstration
echo "=== Example 3: Persistent Locks ==="
echo "Creating persistent CPU frequency lock..."
handle1_output=$($PERF_TEST_BINARY acquire 0x40800000:1800000 | grep "Handle =" | awk '{print $3}')
echo "Handle created: $handle1_output"
sleep 2

echo "Creating persistent GPU power lock..."
handle2_output=$($PERF_TEST_BINARY acquire 0x42800000:1 | grep "Handle =" | awk '{print $3}')
echo "Handle created: $handle2_output"
sleep 2

echo "Releasing persistent locks..."
if [ -n "$handle1_output" ]; then
    $PERF_TEST_BINARY release $handle1_output
fi
if [ -n "$handle2_output" ]; then
    $PERF_TEST_BINARY release $handle2_output
fi
wait_separator

# Example 4: Space tolerance test
echo "=== Example 4: Space Tolerance Test ==="
echo "Testing opcode with spaces (should work correctly):"
$PERF_TEST_BINARY acquire 2000 " 0x44004000 : 500 " " 0x44008000 : 1 "
wait_separator

# Example 5: Common performance scenarios
echo "=== Example 5: Common Scenarios ==="

echo "Gaming Performance Mode (8 seconds):"
$PERF_TEST_BINARY acquire 8000 0x40800000:2400000 0x42800000:0 0x41000000:8 0x40C00000:3
sleep 2

echo "Power Saving Mode (3 seconds):"
$PERF_TEST_BINARY acquire 3000 0x40804000:1200000 0x41004000:2
sleep 2

echo "Balanced Performance Mode (3 seconds):"
$PERF_TEST_BINARY acquire 3000 0x40800000:1600000 0x42800000:2 0x41000000:4
wait_separator

# Example 6: Error handling demonstration
echo "=== Example 6: Error Handling ==="
echo "Testing invalid format (should show error):"
$PERF_TEST_BINARY acquire invalid_format 2>/dev/null || echo "  -> Correctly rejected invalid format"

echo "Testing missing parameters (should show error):"
$PERF_TEST_BINARY acquire 2>/dev/null || echo "  -> Correctly rejected missing parameters"

echo "Testing invalid handle release (should show error):"
$PERF_TEST_BINARY release 99999 2>/dev/null || echo "  -> Correctly handled invalid handle"
wait_separator

# Example 7: Hex vs Decimal test
echo "=== Example 7: Hex vs Decimal Values ==="
echo "Testing hex opcode with decimal value:"
$PERF_TEST_BINARY acquire 2000 0x44004000:400

echo "Testing decimal opcode with hex value:"
$PERF_TEST_BINARY acquire 2000 1140883456:0x190
wait_separator

echo "=== All Examples Completed ==="
echo ""
echo "Key Testing Patterns:"
echo "1. Custom opcodes:        $PERF_TEST_BINARY acquire 3000 0x44004000:400"
echo "2. Multi-opcode merged:   $PERF_TEST_BINARY acquire 5000 0x40800000:1900000 0x42800000:1"
echo "3. Persistent locks:      $PERF_TEST_BINARY acquire 0x40800000:1900000"
echo "4. Manual release:        $PERF_TEST_BINARY release <handle>"
echo "5. Space tolerance:       $PERF_TEST_BINARY acquire 3000 \" 0x44004000 : 400 \""
echo ""
