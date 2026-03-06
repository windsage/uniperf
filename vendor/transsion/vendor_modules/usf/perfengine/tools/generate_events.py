#!/usr/bin/env python3
"""
PerfEngine Event ID Code Generator

Parses event_definitions.xml and generates:
  - Java constants for Framework overlay
  - Java constants for SDK (app events only)
  - C++ constants for Native layer
  - C++ import macro for TranPerfEvent class
  - Markdown documentation

Usage:
    python3 generate_events.py event_definitions.xml

Output files:
    - overlay/frameworks/base/core/java/android/util/TranPerfEventConstants.java
    - sdk/src/main/java/android/util/TranPerfEventConstants.java
    - system/native/include/perfengine/TranPerfEventConstants.h
    - system/native/include/perfengine/TranPerfEventImportMacro.h
    - docs/EVENT_ID_REFERENCE.md
"""

import xml.etree.ElementTree as ET
import sys
from pathlib import Path
from datetime import datetime

# Determine output paths relative to script location
SCRIPT_DIR = Path(__file__).parent
ROOT_DIR = SCRIPT_DIR.parent

JAVA_OVERLAY_OUT = ROOT_DIR / "overlay/frameworks/base/core/java/android/util/TranPerfEventConstants.java"
JAVA_SDK_OUT = ROOT_DIR / "sdk/src/main/java/android/util/TranPerfEventConstants.java"
CPP_HEADER_OUT = ROOT_DIR / "system/native/include/perfengine/TranPerfEventConstants.h"
CPP_MACRO_OUT = ROOT_DIR / "system/native/include/perfengine/TranPerfEventImportMacro.h"
DOCS_MD_OUT = ROOT_DIR / "docs/EVENT_ID_REFERENCE.md"

def parse_xml(xml_file):
    """Parse event_definitions.xml and extract ranges and events"""
    tree = ET.parse(xml_file)
    root = tree.getroot()

    ranges = []
    events = []

    # Parse range definitions
    ranges_node = root.find('Ranges')
    if ranges_node is not None:
        for r in ranges_node.findall('Range'):
            ranges.append({
                'name': r.get('name'),
                'start': r.get('start'),
                'end': r.get('end'),
                'desc': r.get('description')
            })

    # Parse events from all categories
    for category_tag in ['SystemEvents', 'AppEvents', 'InternalEvents']:
        for event_group in root.findall(category_tag):
            app_name = event_group.get('app', 'System' if category_tag == 'SystemEvents' else 'Internal')
            for e in event_group.findall('Event'):
                events.append({
                    'id': e.get('id'),
                    'name': e.get('name'),
                    'desc': e.get('description'),
                    'hook': e.get('hook', ''),
                    'category': category_tag,
                    'app': app_name
                })

    return ranges, events

def generate_java_overlay(ranges, events, output_path):
    """Generate Java constants file for Framework overlay (all events)"""
    timestamp = datetime.now().strftime('%Y-%m-%d %H:%M:%S')

    lines = [
        "/*",
        " * AUTO-GENERATED FILE - DO NOT EDIT MANUALLY",
        f" * Generated: {timestamp}",
        " * Source: event_definitions.xml",
        " * Generator: tools/generate_events.py",
        " */",
        "",
        "package android.util;",
        "",
        "/**",
        " * PerfEngine Event ID Constants",
        " *",
        " * Event ID encoding: 16-bit hex (0x00000 - 0xFFFFF)",
        " * - System events: 0x00000 - 0x00FFF",
        " * - App events: 0x01000 - 0xEFFFF",
        " * - Internal events: 0xF0000 - 0xFFFFF",
        " * @hide",
        " */",
        "public final class TranPerfEventConstants {",
        "",
        "    // ==================== Event ID Ranges ====================",
        ""
    ]

    # Add range constants
    for r in ranges:
        lines.append(f"    /** {r['desc']} @hide */")
        lines.append(f"    public static final int {r['name']}_START = {r['start']};")
        lines.append(f"    /** @hide */")
        lines.append(f"    public static final int {r['name']}_END   = {r['end']};")
        lines.append("")

    # Group events by category
    categories = {}
    for e in events:
        cat = e['category']
        if cat not in categories:
            categories[cat] = []
        categories[cat].append(e)

    # Add event constants by category
    for cat_name, cat_events in sorted(categories.items()):
        lines.append(f"    // ==================== {cat_name} ====================")
        lines.append("")

        for e in sorted(cat_events, key=lambda x: int(x['id'], 16)):
            lines.append(f"    /** {e['desc']} */")
            lines.append(f"    public static final int {e['name']} = {e['id']};")
            lines.append("")

    lines.append("    private TranPerfEventConstants() {}")
    lines.append("    // Prevent instantiation")
    lines.append("}")
    lines.append("")

    # Write to file
    output_path.parent.mkdir(parents=True, exist_ok=True)
    output_path.write_text('\n'.join(lines), encoding='utf-8')
    print(f"✓ Generated: {output_path}")

def generate_java_sdk(ranges, events, output_path):
    """Generate Java constants file for SDK (app events only, no system events)"""
    timestamp = datetime.now().strftime('%Y-%m-%d %H:%M:%S')

    lines = [
        "/*",
        " * AUTO-GENERATED FILE - DO NOT EDIT MANUALLY",
        f" * Generated: {timestamp}",
        " * Source: event_definitions.xml",
        " * Generator: tools/generate_events.py",
        " */",
        "",
        "package android.util;",
        "",
        "/**",
        " * PerfEngine Event ID Constants (SDK Version)",
        " *",
        " * This file contains only application-level events for third-party apps.",
        " * System events are internal to the framework.",
        " */",
        "public final class TranPerfEventConstants {",
        "",
        "    // ==================== Event ID Ranges ====================",
        ""
    ]

    # Add only app-related ranges
    for r in ranges:
        if 'APP' in r['name'] or 'THIRDPARTY' in r['name']:
            lines.append(f"    /** {r['desc']} */")
            lines.append(f"    public static final int {r['name']}_START = {r['start']};")
            lines.append(f"    public static final int {r['name']}_END   = {r['end']};")
            lines.append("")

    # Add only app events (exclude SystemEvents)
    app_events = [e for e in events if e['category'] in ['AppEvents', 'InternalEvents']]

    if app_events:
        lines.append("    // ==================== Application Events ====================")
        lines.append("")

        for e in sorted(app_events, key=lambda x: int(x['id'], 16)):
            lines.append(f"    /** {e['desc']} */")
            lines.append(f"    public static final int {e['name']} = {e['id']};")
            lines.append("")

    lines.append("    private TranPerfEventConstants() {}")
    lines.append("    // Prevent instantiation")
    lines.append("}")
    lines.append("")

    # Write to file
    output_path.parent.mkdir(parents=True, exist_ok=True)
    output_path.write_text('\n'.join(lines), encoding='utf-8')
    print(f"✓ Generated: {output_path}")

def generate_cpp_header(ranges, events, output_path):
    """Generate C++ header file with event constants"""
    timestamp = datetime.now().strftime('%Y-%m-%d %H:%M:%S')

    lines = [
        "/*",
        " * AUTO-GENERATED FILE - DO NOT EDIT MANUALLY",
        f" * Generated: {timestamp}",
        " * Source: event_definitions.xml",
        " * Generator: tools/generate_events.py",
        " */",
        "",
        "#ifndef VENDOR_TRANSSION_PERFENGINE_EVENT_CONSTANTS_H",
        "#define VENDOR_TRANSSION_PERFENGINE_EVENT_CONSTANTS_H",
        "",
        "#include <cstdint>",
        "",
        "namespace android {",
        "namespace transsion {",
        "namespace PerfEventConstants {",
        "",
        "// ==================== Event ID Ranges ====================",
        ""
    ]

    # Add range constants
    for r in ranges:
        lines.append(f"/** {r['desc']} */")
        lines.append(f"constexpr int32_t {r['name']}_START = {r['start']};")
        lines.append(f"constexpr int32_t {r['name']}_END   = {r['end']};")
        lines.append("")

    # Group events by category
    categories = {}
    for e in events:
        cat = e['category']
        if cat not in categories:
            categories[cat] = []
        categories[cat].append(e)

    # Add event constants by category
    for cat_name, cat_events in sorted(categories.items()):
        lines.append(f"// ==================== {cat_name} ====================")
        lines.append("")

        for e in sorted(cat_events, key=lambda x: int(x['id'], 16)):
            lines.append(f"/** {e['desc']} */")
            lines.append(f"constexpr int32_t {e['name']} = {e['id']};")
            lines.append("")

    lines.extend([
        "}  // namespace PerfEventConstants",
        "}  // namespace transsion",
        "}  // namespace android",
        "",
        "#endif  // VENDOR_TRANSSION_PERFENGINE_EVENT_CONSTANTS_H",
        ""
    ])

    # Write to file
    output_path.parent.mkdir(parents=True, exist_ok=True)
    output_path.write_text('\n'.join(lines), encoding='utf-8')
    print(f"✓ Generated: {output_path}")

def generate_cpp_import_macro(events, output_path):
    """Generate macro for importing constants into TranPerfEvent class

    This macro allows the TranPerfEvent class to expose all event constants
    without manually listing each one. Usage:

        class TranPerfEvent {
        public:
            PERFENGINE_IMPORT_EVENT_CONSTANTS
            // ... other members ...
        };
    """
    timestamp = datetime.now().strftime('%Y-%m-%d %H:%M:%S')

    lines = [
        "/*",
        " * AUTO-GENERATED FILE - DO NOT EDIT MANUALLY",
        f" * Generated: {timestamp}",
        " * Source: event_definitions.xml",
        " * Generator: tools/generate_events.py",
        " *",
        " * This file provides a macro to import all PerfEngine event constants",
        " * into the TranPerfEvent class scope.",
        " *",
        " * Usage:",
        " *   #include \"TranPerfEventImportMacro.h\"",
        " *",
        " *   class TranPerfEvent {",
        " *   public:",
        " *       PERFENGINE_IMPORT_EVENT_CONSTANTS",
        " *       // ... other members ...",
        " *   };",
        " *",
        " * This expands to static constexpr aliases for all event IDs,",
        " * allowing usage like: TranPerfEvent::EVENT_SYS_APP_LAUNCH",
        " */",
        "",
        "#ifndef VENDOR_TRANSSION_PERFENGINE_IMPORT_MACRO_H",
        "#define VENDOR_TRANSSION_PERFENGINE_IMPORT_MACRO_H",
        "",
        "/**",
        " * Import all event constants from PerfEventConstants namespace",
        " * into the current class scope.",
        " */",
        "#define PERFENGINE_IMPORT_EVENT_CONSTANTS \\",
    ]

    sorted_events = sorted(events, key=lambda x: int(x['id'], 16))
    for i, e in enumerate(sorted_events):
        is_last = (i == len(sorted_events) - 1)
        backslash = "" if is_last else " \\"
        lines.append(f"    static constexpr int32_t {e['name']} = PerfEventConstants::{e['name']};{backslash}")

    lines.extend([
        "",
        "#endif  // VENDOR_TRANSSION_PERFENGINE_IMPORT_MACRO_H",
        ""
    ])

    # Write to file
    output_path.parent.mkdir(parents=True, exist_ok=True)
    output_path.write_text('\n'.join(lines), encoding='utf-8')
    print(f"✓ Generated: {output_path}")

def generate_markdown_docs(ranges, events, output_path):
    """Generate Markdown documentation"""
    timestamp = datetime.now().strftime('%Y-%m-%d %H:%M:%S')

    lines = [
        "# PerfEngine Event ID Reference",
        "",
        f"**Auto-generated:** {timestamp}",
        "",
        "**Source:** `event_definitions.xml`",
        "",
        "---",
        "",
        "## Event ID Encoding",
        "",
        "Events use **16-bit hexadecimal encoding** (0x00000 - 0xFFFFF):",
        "",
        "```",
        "0xCTTNN",
        "  │││└─ Number (event sequence within category)",
        "  ││└── Type (sub-category)",
        "  │└─── Category (system/app/internal)",
        "  └──── Reserved",
        "```",
        "",
        "---",
        "",
        "## Event ID Ranges",
        "",
        "| Range Name | Start | End | Description |",
        "|------------|-------|-----|-------------|"
    ]

    for r in ranges:
        lines.append(f"| `{r['name']}` | `{r['start']}` | `{r['end']}` | {r['desc']} |")

    lines.extend([
        "",
        "---",
        "",
        "## Event Definitions",
        "",
        "### System Events (Framework Internal)",
        "",
        "| Event ID | Constant Name | Description | Hook Point |",
        "|----------|---------------|-------------|------------|"
    ])

    # System events
    sys_events = [e for e in events if e['category'] == 'SystemEvents']
    for e in sorted(sys_events, key=lambda x: int(x['id'], 16)):
        hook = e['hook'] if e['hook'] else 'N/A'
        lines.append(f"| `{e['id']}` | `{e['name']}` | {e['desc']} | {hook} |")

    lines.extend([
        "",
        "### Application Events",
        "",
        "| Event ID | Constant Name | App | Description |",
        "|----------|---------------|-----|-------------|"
    ])

    # App events
    app_events = [e for e in events if e['category'] == 'AppEvents']
    for e in sorted(app_events, key=lambda x: int(x['id'], 16)):
        lines.append(f"| `{e['id']}` | `{e['name']}` | {e['app']} | {e['desc']} |")

    if any(e['category'] == 'InternalEvents' for e in events):
        lines.extend([
            "",
            "### Internal Events (Test/Debug)",
            "",
            "| Event ID | Constant Name | Description |",
            "|----------|---------------|-------------|"
        ])

        internal_events = [e for e in events if e['category'] == 'InternalEvents']
        for e in sorted(internal_events, key=lambda x: int(x['id'], 16)):
            lines.append(f"| `{e['id']}` | `{e['name']}` | {e['desc']} |")

    lines.extend([
        "",
        "---",
        "",
        "## Usage Examples",
        "",
        "### Java (Framework)",
        "",
        "```java",
        "import static android.util.TranPerfEventConstants.*;",
        "",
        "TranPerfEvent.notifyEventStart(EVENT_SYS_APP_LAUNCH, System.nanoTime());",
        "```",
        "",
        "### C++ (Native)",
        "",
        "```cpp",
        "#include <perfengine/TranPerfEvent.h>",
        "using ::android::transsion::TranPerfEvent;",
        "",
        "TranPerfEvent::notifyEventStart(TranPerfEvent::EVENT_SYS_SCROLL,",
        "                               systemTime(SYSTEM_TIME_MONOTONIC));",
        "```",
        "",
        "---",
        "",
        "*This document is auto-generated. Do not edit manually.*",
        ""
    ])

    # Write to file
    output_path.parent.mkdir(parents=True, exist_ok=True)
    output_path.write_text('\n'.join(lines), encoding='utf-8')
    print(f"✓ Generated: {output_path}")

def validate_events(events):
    """Validate event definitions for duplicates and naming conventions"""
    ids_seen = set()
    names_seen = set()
    errors = []

    for e in events:
        # Check for duplicate IDs
        if e['id'] in ids_seen:
            errors.append(f"Duplicate event ID: {e['id']} ({e['name']})")
        ids_seen.add(e['id'])

        # Check for duplicate names
        if e['name'] in names_seen:
            errors.append(f"Duplicate event name: {e['name']} ({e['id']})")
        names_seen.add(e['name'])

        # Validate naming convention
        if e['category'] == 'SystemEvents' and not e['name'].startswith('EVENT_SYS_'):
            errors.append(f"System event must start with EVENT_SYS_: {e['name']}")
        elif e['category'] == 'AppEvents' and not e['name'].startswith('EVENT_APP_'):
            errors.append(f"App event must start with EVENT_APP_: {e['name']}")

    return errors

def main():
    import argparse

    parser = argparse.ArgumentParser(
        description='PerfEngine Event ID Code Generator',
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog="""
Examples:
  # Generate all files (manual execution)
  %(prog)s event_definitions.xml

  # Generate C++ headers only (called by genrule)
  %(prog)s event_definitions.xml --out-cpp-dir output/include/perfengine

  # Generate Java overlay only (called by genrule)
  %(prog)s event_definitions.xml --out-java output/TranPerfEventConstants.java
        """
    )
    parser.add_argument('xml_file', help='Path to event_definitions.xml')
    parser.add_argument('--out-cpp-dir', metavar='DIR',
                        help='Output directory for C++ headers (generates TranPerfEventConstants.h and TranPerfEventImportMacro.h)')
    parser.add_argument('--out-java', metavar='FILE',
                        help='Output file for Java overlay constants')
    parser.add_argument('--out-java-sdk', metavar='FILE',
                        help='Output file for Java SDK constants')
    parser.add_argument('--out-docs', metavar='FILE',
                        help='Output file for Markdown documentation')

    args = parser.parse_args()

    xml_path = Path(args.xml_file)
    if not xml_path.exists():
        print(f"Error: File not found: {args.xml_file}")
        sys.exit(1)

    # 打印标题
    is_genrule_mode = any([args.out_cpp_dir, args.out_java, args.out_java_sdk, args.out_docs])

    if not is_genrule_mode:
        print("=" * 60)
        print("PerfEngine Event ID Code Generator")
        print("=" * 60)
        print()

    # Parse XML
    if not is_genrule_mode:
        print(f"Parsing: {args.xml_file}")
    ranges, events = parse_xml(args.xml_file)
    if not is_genrule_mode:
        print(f"  Ranges: {len(ranges)}")
        print(f"  Events: {len(events)}")
        print()

    # Validate
    if not is_genrule_mode:
        print("Validating event definitions...")
    errors = validate_events(events)
    if errors:
        print("Validation failed:")
        for err in errors:
            print(f"  - {err}")
        sys.exit(1)
    if not is_genrule_mode:
        print("✓ Validation passed")
        print()

    # Generate files based on arguments
    if not is_genrule_mode:
        print("Generating code files...")

    if args.out_cpp_dir:
        # Genrule mode: C++ headers only
        cpp_dir = Path(args.out_cpp_dir)
        cpp_dir.mkdir(parents=True, exist_ok=True)
        generate_cpp_header(ranges, events, cpp_dir / "TranPerfEventConstants.h")
        generate_cpp_import_macro(events, cpp_dir / "TranPerfEventImportMacro.h")

    if args.out_java:
        # Genrule mode: Java overlay only
        generate_java_overlay(ranges, events, Path(args.out_java))

    if args.out_java_sdk:
        # Genrule mode: Java SDK only
        generate_java_sdk(ranges, events, Path(args.out_java_sdk))

    if args.out_docs:
        # Genrule mode: Docs only
        generate_markdown_docs(ranges, events, Path(args.out_docs))

    # Full generation mode (no arguments)
    if not is_genrule_mode:
        generate_java_overlay(ranges, events, JAVA_OVERLAY_OUT)
        generate_java_sdk(ranges, events, JAVA_SDK_OUT)
        generate_cpp_header(ranges, events, CPP_HEADER_OUT)
        generate_cpp_import_macro(events, CPP_MACRO_OUT)
        generate_markdown_docs(ranges, events, DOCS_MD_OUT)

        print()
        print("=" * 60)
        print("✓ Code generation complete!")
        print("=" * 60)

if __name__ == '__main__':
    main()
