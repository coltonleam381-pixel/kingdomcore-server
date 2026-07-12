#!/usr/bin/env python3
"""Download a full server mirror from Humble (excluding regeneratable runtime dirs)."""

from __future__ import annotations

import json
import os
import shutil
import stat
import sys
from pathlib import Path

import paramiko

ROOT = Path(__file__).resolve().parent
HOST = "eux1.humbleservers.com"
PORT = 2022
USER = "emnxwjpu.0fb44e8e"

SKIP_DIRS = {
    ".gradle",
    ".paper-remapped",
    ".git",
}

RUNTIME_DIRS = {
    "cache",
    "libraries",
    "versions",
    "logs",
    "tmp",
}

SKIP_FILES = {".DS_Store", "session.lock"}


def load_password() -> str:
    password = os.environ.get("HUMBLE_SFTP_PASSWORD", "").strip()
    env_file = ROOT / ".humble-deploy.env"
    if not password and env_file.exists():
        for line in env_file.read_text().splitlines():
            if line.startswith("HUMBLE_SFTP_PASSWORD="):
                password = line.split("=", 1)[1].strip().strip("'\"")
    if not password:
        sftp_json = ROOT / ".vscode/sftp.json"
        if sftp_json.exists():
            password = str(json.loads(sftp_json.read_text()).get("password", "")).strip()
    if not password:
        raise SystemExit("Missing SFTP password.")
    return password


def should_skip(name: str, is_dir: bool, include_runtime: bool) -> bool:
    if is_dir:
        if name in SKIP_DIRS:
            return True
        if not include_runtime and name in RUNTIME_DIRS:
            return True
        return False
    return name in SKIP_FILES


def list_remote_files(sftp: paramiko.SFTPClient, include_runtime: bool, remote: str = "") -> list[tuple[str, int]]:
    found: list[tuple[str, int]] = []

    def walk(path: str) -> None:
        for entry in sftp.listdir_attr(path or "."):
            name = entry.filename
            is_dir = stat.S_ISDIR(entry.st_mode)
            if should_skip(name, is_dir, include_runtime):
                continue
            full = f"{path}/{name}" if path else name
            if is_dir:
                walk(full)
            else:
                found.append((full, entry.st_size))

    walk(remote)
    return found


def connect() -> paramiko.SFTPClient:
    transport = paramiko.Transport((HOST, PORT))
    transport.connect(username=USER, password=load_password())
    return paramiko.SFTPClient.from_transport(transport)


def download_all(sftp: paramiko.SFTPClient, files: list[tuple[str, int]]) -> tuple[int, int, int, int]:
    downloaded = 0
    skipped = 0
    failed = 0
    total_bytes = 0

    for remote, size in files:
        local = ROOT / remote
        local.parent.mkdir(parents=True, exist_ok=True)

        if local.exists() and local.is_file() and local.stat().st_size == size:
            skipped += 1
            continue

        try:
            print(f"GET {remote} ({size} bytes)")
            sftp.get(remote, str(local))
            downloaded += 1
            total_bytes += size
        except OSError as exc:
            print(f"FAIL {remote}: {exc}", file=sys.stderr)
            failed += 1

    return downloaded, skipped, failed, total_bytes


def remove_stale_backups() -> None:
    for name in ("world.backup", "world_nether.backup", "world_the_end.backup"):
        path = ROOT / name
        if path.is_dir():
            shutil.rmtree(path)
            print(f"Removed old backup {name}/")


def main() -> int:
    import argparse

    parser = argparse.ArgumentParser(description="Download full server mirror from Humble")
    parser.add_argument(
        "--everything",
        action="store_true",
        help="Also pull cache/, libraries/, versions/, logs/, tmp/ (full byte-for-byte backup)",
    )
    args = parser.parse_args()

    include_runtime = args.everything
    print(f"Full pull from {USER}@{HOST}:{PORT}")
    print(f"Local root: {ROOT}")
    if include_runtime:
        print("Mode: EVERYTHING (including runtime folders)")
    else:
        print(f"Skipping runtime dirs: {sorted(RUNTIME_DIRS)}")
    print()

    sftp = connect()
    try:
        files = list_remote_files(sftp, include_runtime)
        total_size = sum(size for _, size in files)
        print(f"Remote mirror: {len(files)} files, {total_size / 1024 / 1024:.1f} MB\n")

        downloaded, skipped, failed, pulled = download_all(sftp, files)
    finally:
        sftp.close()

    remove_stale_backups()

    print()
    print("=== Full pull complete ===")
    print(f"Downloaded/updated: {downloaded} files ({pulled / 1024 / 1024:.1f} MB)")
    print(f"Already matched:      {skipped} files")
    print(f"Failed:               {failed} files")
    print()
    if include_runtime:
        print("Included runtime folders: cache/, libraries/, versions/, logs/, tmp/")
    else:
        print("Runtime folders skipped (use --everything for those).")
    print("Included: worlds, plugins, configs, player data, ItemsAdder pack, Paper jar.")
    return 1 if failed else 0


if __name__ == "__main__":
    raise SystemExit(main())
