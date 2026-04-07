#!/usr/bin/env python3
"""
Batch cherry-pick from Gerrit URLs.

Input file format (one entry per line, tab or spaces separated):
    <gerrit_url>  <local_repo_path>

    - local_repo_path: absolute path, or relative to --aosp-root
    - Lines starting with '#' are ignored
    - Blank lines are ignored

Example input file (picks.txt):
    https://gerrit.transsion.com/c/QCOM/LA/platform/frameworks/base/+/1426628  system/framework/base
    https://gerrit.transsion.com/c/QCOM/LA/platform/vendor/qcom-proprietary/ship/perf-core/+/1460770  vendor/qcom-proprietary/perf-core

Usage:
    python3 cherry_pick.py --input picks.txt --aosp-root /home/user/aosp
    python3 cherry_pick.py --input picks.txt  # aosp-root defaults to cwd
    python3 cherry_pick.py --input picks.txt --dry-run
"""

import argparse
import json
import logging
import os
import pwd
import subprocess
import sys
from dataclasses import dataclass, field
from typing import Optional
from urllib.parse import urlparse

# ---------------------------------------------------------------------------
# Constants
# ---------------------------------------------------------------------------
GERRIT_HOST = "gerrit.transsion.com"
GERRIT_SSH_PORT = 29418

# ---------------------------------------------------------------------------
# Logging setup
# ---------------------------------------------------------------------------
logging.basicConfig(
    format="%(asctime)s [%(levelname)s] %(message)s",
    datefmt="%H:%M:%S",
    level=logging.INFO,
)
log = logging.getLogger("cherry_pick")


# ---------------------------------------------------------------------------
# Data types
# ---------------------------------------------------------------------------
@dataclass
class PickEntry:
    gerrit_url: str
    local_path: str       # absolute path after resolution
    change_number: str = ""
    project: str = ""     # Gerrit project name, e.g. QCOM/LA/platform/frameworks/base
    ref: str = ""         # e.g. refs/changes/28/1426628/14
    fetch_url: str = ""   # ssh://<user>@gerrit.transsion.com:29418/<project>


@dataclass
class PickResult:
    entry: PickEntry
    success: bool
    message: str = ""


# ---------------------------------------------------------------------------
# Helpers
# ---------------------------------------------------------------------------
def get_current_user() -> str:
    """Get the current Linux username via pwd (more reliable than os.getlogin)."""
    return pwd.getpwuid(os.getuid()).pw_name


def parse_change_number(gerrit_url: str) -> str:
    """
    Extract change number from a Gerrit URL.
    e.g. https://gerrit.transsion.com/c/QCOM/.../+/1426628  ->  '1426628'
    """
    path = urlparse(gerrit_url).path  # /c/QCOM/.../+/1426628
    parts = [p for p in path.split("/") if p]
    # The change number is always after '+' in AOSP-style Gerrit URLs
    try:
        plus_idx = parts.index("+")
        return parts[plus_idx + 1]
    except (ValueError, IndexError):
        raise ValueError(f"Cannot extract change number from URL: {gerrit_url}")


def query_gerrit_ssh(change_number: str, ssh_user: str) -> dict:
    """
    Query Gerrit via SSH to get current patchset info.
    Returns the parsed change JSON dict.

    Command:
        ssh -p 29418 <user>@gerrit.transsion.com gerrit query \
            --format=JSON --current-patch-set change:<number>
    """
    cmd = [
        "ssh",
        "-p", str(GERRIT_SSH_PORT),
        "-o", "BatchMode=yes",           # never prompt for password
        "-o", "StrictHostKeyChecking=no",
        f"{ssh_user}@{GERRIT_HOST}",
        "gerrit", "query",
        "--format=JSON",
        "--current-patch-set",
        f"change:{change_number}",
    ]
    log.debug("SSH query: %s", " ".join(cmd))

    result = subprocess.run(
        cmd,
        capture_output=True,
        text=True,
        timeout=30,
    )
    if result.returncode != 0:
        raise RuntimeError(
            f"SSH gerrit query failed (rc={result.returncode}): {result.stderr.strip()}"
        )

    # Output contains multiple JSON lines; first line is the change, last is stats
    change_data = None
    for line in result.stdout.splitlines():
        line = line.strip()
        if not line:
            continue
        try:
            obj = json.loads(line)
        except json.JSONDecodeError:
            continue
        if obj.get("type") == "stats":
            continue
        change_data = obj
        break

    if change_data is None:
        raise RuntimeError(
            f"No change data returned for change {change_number}. "
            "Check if the change number is correct and you have access."
        )

    row_count = None
    for line in result.stdout.splitlines():
        try:
            obj = json.loads(line)
            if obj.get("type") == "stats":
                row_count = obj.get("rowCount", 0)
        except json.JSONDecodeError:
            pass

    if row_count == 0:
        raise RuntimeError(f"Change {change_number} not found on Gerrit.")

    return change_data


def resolve_local_path(raw_path: str, aosp_root: str) -> str:
    """Resolve local_repo_path to absolute path."""
    if os.path.isabs(raw_path):
        return raw_path
    return os.path.join(aosp_root, raw_path)


def run_git(args: list, cwd: str, dry_run: bool) -> subprocess.CompletedProcess:
    """Run a git command in the given directory."""
    cmd = ["git"] + args
    log.debug("  $ %s  (cwd=%s)", " ".join(cmd), cwd)
    if dry_run:
        log.info("  [dry-run] %s", " ".join(cmd))
        return subprocess.CompletedProcess(cmd, returncode=0, stdout="", stderr="")
    return subprocess.run(cmd, cwd=cwd, capture_output=True, text=True)


# ---------------------------------------------------------------------------
# Core logic
# ---------------------------------------------------------------------------
def resolve_entry(entry: PickEntry, ssh_user: str) -> None:
    """
    Fill entry.project, entry.ref, entry.fetch_url by querying Gerrit SSH.
    Mutates entry in place.
    """
    change_data = query_gerrit_ssh(entry.change_number, ssh_user)

    entry.project = change_data.get("project", "")
    if not entry.project:
        raise RuntimeError(
            f"Gerrit returned empty project for change {entry.change_number}"
        )

    current_ps = change_data.get("currentPatchSet", {})
    entry.ref = current_ps.get("ref", "")
    if not entry.ref:
        raise RuntimeError(
            f"Gerrit returned empty ref for change {entry.change_number}"
        )

    ps_number = current_ps.get("number", "?")
    log.info(
        "  change=%s  project=%s  patchset=%s  ref=%s",
        entry.change_number,
        entry.project,
        ps_number,
        entry.ref,
    )

    entry.fetch_url = (
        f"ssh://{ssh_user}@{GERRIT_HOST}:{GERRIT_SSH_PORT}/{entry.project}"
    )


def do_cherry_pick(entry: PickEntry, dry_run: bool) -> PickResult:
    """
    Execute:
        git fetch <fetch_url> <ref>
        git cherry-pick FETCH_HEAD
    On failure, run git cherry-pick --abort and return failure result.
    """
    local_path = entry.local_path

    if not dry_run and not os.path.isdir(local_path):
        return PickResult(
            entry=entry,
            success=False,
            message=f"Local path does not exist: {local_path}",
        )

    # Step 1: git fetch
    fetch_result = run_git(
        ["fetch", entry.fetch_url, entry.ref],
        cwd=local_path,
        dry_run=dry_run,
    )
    if fetch_result.returncode != 0:
        return PickResult(
            entry=entry,
            success=False,
            message=f"git fetch failed:\n{fetch_result.stderr.strip()}",
        )

    # Step 2: git cherry-pick FETCH_HEAD
    cp_result = run_git(
        ["cherry-pick", "FETCH_HEAD"],
        cwd=local_path,
        dry_run=dry_run,
    )
    if cp_result.returncode != 0:
        # Abort to leave the repo clean
        abort_result = run_git(
            ["cherry-pick", "--abort"],
            cwd=local_path,
            dry_run=dry_run,
        )
        abort_msg = ""
        if abort_result.returncode != 0:
            abort_msg = f" (abort also failed: {abort_result.stderr.strip()})"
        return PickResult(
            entry=entry,
            success=False,
            message=(
                f"git cherry-pick failed (auto-aborted{abort_msg}):\n"
                f"{cp_result.stderr.strip() or cp_result.stdout.strip()}"
            ),
        )

    return PickResult(entry=entry, success=True, message="OK")


# ---------------------------------------------------------------------------
# Input parsing
# ---------------------------------------------------------------------------
def parse_input_file(input_file: str, aosp_root: str) -> list:
    """
    Parse input file and return list of PickEntry.
    Each non-comment, non-blank line must have exactly two fields:
        <gerrit_url>  <local_repo_path>
    """
    entries = []
    with open(input_file, "r", encoding="utf-8") as f:
        for lineno, raw in enumerate(f, start=1):
            line = raw.strip()
            if not line or line.startswith("#"):
                continue
            parts = line.split()
            if len(parts) < 2:
                log.warning("Line %d: skipping (expected 2 fields): %s", lineno, line)
                continue
            gerrit_url, raw_path = parts[0], parts[1]
            try:
                change_number = parse_change_number(gerrit_url)
            except ValueError as e:
                log.warning("Line %d: %s", lineno, e)
                continue
            local_path = resolve_local_path(raw_path, aosp_root)
            entries.append(PickEntry(
                gerrit_url=gerrit_url,
                local_path=local_path,
                change_number=change_number,
            ))
    return entries


# ---------------------------------------------------------------------------
# Summary printer
# ---------------------------------------------------------------------------
def print_summary(results: list) -> None:
    ok = [r for r in results if r.success]
    fail = [r for r in results if not r.success]

    print("\n" + "=" * 70)
    print(f"  SUMMARY:  {len(ok)} succeeded,  {len(fail)} failed  (total {len(results)})")
    print("=" * 70)

    if ok:
        print("\n✅ Succeeded:")
        for r in ok:
            print(f"   change={r.entry.change_number}  path={r.entry.local_path}")

    if fail:
        print("\n❌ Failed:")
        for r in fail:
            print(f"   change={r.entry.change_number}  path={r.entry.local_path}")
            for msg_line in r.message.splitlines():
                print(f"       {msg_line}")

    print()


# ---------------------------------------------------------------------------
# Entry point
# ---------------------------------------------------------------------------
def main() -> int:
    parser = argparse.ArgumentParser(
        description="Batch cherry-pick Gerrit changes into local AOSP repos."
    )
    parser.add_argument(
        "--input", required=True,
        help="Path to input file (one '<gerrit_url> <local_path>' per line)",
    )
    parser.add_argument(
        "--aosp-root", default=os.getcwd(),
        help="AOSP root directory for resolving relative local paths (default: cwd)",
    )
    parser.add_argument(
        "--dry-run", action="store_true",
        help="Print actions without executing git commands",
    )
    parser.add_argument(
        "--verbose", action="store_true",
        help="Enable debug logging",
    )
    args = parser.parse_args()

    if args.verbose:
        log.setLevel(logging.DEBUG)

    # Detect current user (supports multi-user usage without --user flag)
    ssh_user = get_current_user()
    log.info("Gerrit SSH user: %s", ssh_user)
    log.info("AOSP root: %s", args.aosp_root)
    if args.dry_run:
        log.info("DRY-RUN mode: no git commands will be executed")

    # Parse input file
    entries = parse_input_file(args.input, args.aosp_root)
    if not entries:
        log.error("No valid entries found in %s", args.input)
        return 1
    log.info("Loaded %d entries", len(entries))

    results = []
    for idx, entry in enumerate(entries, start=1):
        log.info("")
        log.info("[%d/%d] change=%s  path=%s",
                 idx, len(entries), entry.change_number, entry.local_path)

        # Query Gerrit for latest patchset info
        try:
            resolve_entry(entry, ssh_user)
        except Exception as e:
            log.error("  Gerrit query failed: %s", e)
            results.append(PickResult(entry=entry, success=False, message=str(e)))
            continue

        # Execute cherry-pick
        result = do_cherry_pick(entry, dry_run=args.dry_run)
        if result.success:
            log.info("  ✅ cherry-pick OK")
        else:
            log.error("  ❌ cherry-pick FAILED: %s", result.message.splitlines()[0])
        results.append(result)

    print_summary(results)
    failed_count = sum(1 for r in results if not r.success)
    return 0 if failed_count == 0 else 1


if __name__ == "__main__":
    sys.exit(main())
