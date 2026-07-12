#!/usr/bin/env python3
"""Audit and sync local Minecraft server with Humble (bidirectional where needed)."""

from __future__ import annotations

import argparse
import hashlib
import os
import shutil
import stat
import subprocess
import sys
from dataclasses import dataclass
from pathlib import Path

import paramiko

ROOT = Path(__file__).resolve().parent
HOST = "eux1.humbleservers.com"
PORT = 2022
USER = "emnxwjpu.0fb44e8e"

# Live server is authoritative for player/access state and runtime WG flags.
PULL_FROM_REMOTE = [
    "whitelist.json",
    "ops.json",
    "usercache.json",
    "banned-players.json",
    "banned-ips.json",
    "plugins/KingdomCore/kingdomcore.db",
    "plugins/WorldGuard/worlds/world/regions.yml",
]

# Local repo is authoritative for plugin builds and edited configs.
PUSH_TO_REMOTE = [
    ("plugins/KingdomCore.jar", ROOT / "plugins/KingdomCore.jar"),
    ("plugins/KingdomCore/config.yml", ROOT / "plugins/KingdomCore/config.yml"),
    ("config/paper-world-defaults.yml", ROOT / "config/paper-world-defaults.yml"),
    ("server.properties", ROOT / "server.properties"),
    ("commands.yml", ROOT / "commands.yml"),
    ("plugins/FancyHolograms/holograms.yml", ROOT / "plugins/FancyHolograms/holograms.yml"),
    ("plugins/FancyNpcs/npcs.yml", ROOT / "plugins/FancyNpcs/npcs.yml"),
    ("plugins/ItemsAdder/config.yml", ROOT / "plugins/ItemsAdder/config.yml"),
    ("plugins/ItemsAdder/contents/kingdomcore/configs/items.yml",
     ROOT / "plugins/ItemsAdder/contents/kingdomcore/configs/items.yml"),
    ("plugins/ItemsAdder/contents/kingdomcore/configs/recipes.yml",
     ROOT / "plugins/ItemsAdder/contents/kingdomcore/configs/recipes.yml"),
    ("plugins/ItemsAdder/contents/kingdomcore/models/item/mace.json",
     ROOT / "plugins/ItemsAdder/contents/kingdomcore/models/item/mace.json"),
    ("plugins/ItemsAdder/contents/kingdomcore/models/item/scythe.json",
     ROOT / "plugins/ItemsAdder/contents/kingdomcore/models/item/scythe.json"),
    ("plugins/voicechat-bukkit-2.6.20.jar", ROOT / "plugins/voicechat-bukkit-2.6.20.jar"),
    ("plugins/voicechat/voicechat-server.properties",
     ROOT / "plugins/voicechat/voicechat-server.properties"),
    ("plugins/instantrestock_2.6.3.jar", ROOT / "plugins/instantrestock_2.6.3.jar"),
]

REMOTE_DELETES = [
    "plugins/ItemsAdder/contents/kingdomcore/resourcepack/kingdomcore/models/item/mace.json",
    "plugins/ItemsAdder/contents/kingdomcore/resourcepack/kingdomcore/models/item/scythe.json.broken",
    "plugins/voicechat-bukkit-2.6.12.jar",
    "plugins/voicechat-bukkit-2.6.18.jar",
]

IP_DEPENDENT = [
    "plugins/ItemsAdder/config.yml",
    "plugins/ItemsAdder/storage/cache/various/resourcepacks.yml",
    "plugins/voicechat/voicechat-server.properties",
]

WORLD_FOLDERS = ["world", "world_nether", "world_the_end"]


@dataclass
class FileState:
    path: str
    local_size: int | None
    remote_size: int | None

    @property
    def status(self) -> str:
        if self.local_size is None and self.remote_size is None:
            return "missing"
        if self.local_size is None:
            return "remote-only"
        if self.remote_size is None:
            return "local-only"
        if self.local_size == self.remote_size:
            return "sync"
        return "diff"


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
            import json

            data = json.loads(sftp_json.read_text())
            password = str(data.get("password", "")).strip()
    if not password:
        raise SystemExit("Set HUMBLE_SFTP_PASSWORD, .humble-deploy.env, or .vscode/sftp.json password.")
    return password


def connect(password: str) -> paramiko.SFTPClient:
    transport = paramiko.Transport((HOST, PORT))
    transport.connect(username=USER, password=password)
    return paramiko.SFTPClient.from_transport(transport)


def remote_size(sftp: paramiko.SFTPClient, path: str) -> int | None:
    try:
        return sftp.stat(path).st_size
    except OSError:
        return None


def ensure_remote_dir(sftp: paramiko.SFTPClient, remote_path: str) -> None:
    remote_dir = os.path.dirname(remote_path)
    if not remote_dir:
        return
    parts: list[str] = []
    for part in remote_dir.split("/"):
        parts.append(part)
        current = "/".join(parts)
        try:
            sftp.stat(current)
        except OSError:
            try:
                sftp.mkdir(current)
            except OSError:
                pass


def build_jar() -> None:
    print("Building KingdomCore...")
    subprocess.run(
        ["./gradlew", "build", "-x", "test", "-q"],
        cwd=ROOT / "plugins/KingdomCore",
        check=True,
    )
    src = ROOT / "plugins/KingdomCore/build/libs/KingdomCore-1.0.0.jar"
    dst = ROOT / "plugins/KingdomCore.jar"
    dst.write_bytes(src.read_bytes())
    print(f"Built {dst} ({dst.stat().st_size} bytes)")


def audit(sftp: paramiko.SFTPClient) -> list[FileState]:
    seen: set[str] = set()
    states: list[FileState] = []

    for rel in PULL_FROM_REMOTE:
        seen.add(rel)
        local = ROOT / rel
        states.append(
            FileState(
                rel,
                local.stat().st_size if local.exists() else None,
                remote_size(sftp, rel),
            )
        )

    for remote, local in PUSH_TO_REMOTE:
        seen.add(remote)
        states.append(
            FileState(
                remote,
                local.stat().st_size if local.exists() else None,
                remote_size(sftp, remote),
            )
        )

    print(f"{'PATH':<60} {'LOCAL':>10} {'REMOTE':>10} {'STATUS':>12}")
    print("-" * 96)
    diffs = 0
    for state in states:
        ls = str(state.local_size) if state.local_size is not None else "-"
        rs = str(state.remote_size) if state.remote_size is not None else "-"
        if state.status == "diff":
            diffs += 1
        print(f"{state.path:<60} {ls:>10} {rs:>10} {state.status:>12}")

    print("\n--- Plugin jars ---")
    remote_jars = sorted(f for f in sftp.listdir("plugins") if f.endswith(".jar"))
    for jar in remote_jars:
        rs = remote_size(sftp, f"plugins/{jar}")
        local = ROOT / "plugins" / jar
        ls = local.stat().st_size if local.exists() else None
        status = "sync" if ls == rs else ("local-only" if ls is None else "diff")
        if status != "sync":
            diffs += 1
        print(f"  {jar:<42} local={ls or '-':>10} remote={rs or '-':>10} [{status}]")

    print(f"\nAudit: {diffs} difference(s)")
    print("\nIP-dependent files (update on host migration):")
    for rel in IP_DEPENDENT:
        print(f"  - {rel}")
    print(f"\nHumble SFTP: {USER}@{HOST}:{PORT}")
    return states


def pull_live(sftp: paramiko.SFTPClient) -> None:
    print("Pulling live state from Humble -> local...")
    for rel in PULL_FROM_REMOTE:
        local = ROOT / rel
        rs = remote_size(sftp, rel)
        if rs is None:
            print(f"  SKIP remote missing: {rel}")
            continue
        local.parent.mkdir(parents=True, exist_ok=True)
        sftp.get(rel, str(local))
        print(f"  GET {rel} ({rs} bytes)")


def push_build(sftp: paramiko.SFTPClient) -> None:
    print("Pushing local build/config -> Humble...")
    gui_menus = ROOT / "plugins/DeluxeMenus/gui_menus"
    uploads = list(PUSH_TO_REMOTE)
    if gui_menus.is_dir():
        for menu_file in sorted(gui_menus.glob("*.yml")):
            uploads.append((f"plugins/DeluxeMenus/gui_menus/{menu_file.name}", menu_file))

    for remote, local in uploads:
        if not local.exists():
            print(f"  SKIP missing local: {local}")
            continue
        ensure_remote_dir(sftp, remote)
        print(f"  PUT {remote} ({local.stat().st_size} bytes)")
        sftp.put(str(local), remote)

    for remote in REMOTE_DELETES:
        try:
            sftp.remove(remote)
            print(f"  DEL {remote}")
        except OSError:
            pass


def pull_worlds(sftp: paramiko.SFTPClient) -> None:
    print("Pulling world folders from Humble -> local (stop server first for clean snapshot)...")

    def download_dir(remote: str, local: Path) -> None:
        local.mkdir(parents=True, exist_ok=True)
        for entry in sftp.listdir_attr(remote):
            remote_path = f"{remote}/{entry.filename}"
            local_path = local / entry.filename
            if stat.S_ISDIR(entry.st_mode):
                download_dir(remote_path, local_path)
            else:
                sftp.get(remote_path, str(local_path))

    for world in WORLD_FOLDERS:
        dest = ROOT / world
        backup = ROOT / f"{world}.backup"
        rs = remote_size(sftp, world)
        if rs is None:
            print(f"  SKIP remote missing: {world}/")
            continue
        if dest.exists():
            if backup.exists():
                shutil.rmtree(backup)
            print(f"  Backup {world} -> {world}.backup")
            shutil.move(str(dest), str(backup))
        print(f"  Downloading {world}/ ...")
        download_dir(world, dest)
        lock = dest / "session.lock"
        if lock.exists():
            lock.unlink()
        size_mb = sum(f.stat().st_size for f in dest.rglob("*") if f.is_file()) / 1024 / 1024
        print(f"  Done {world}/ ({size_mb:.1f} MB)")


def main() -> int:
    parser = argparse.ArgumentParser(description="Sync local server with Humble")
    parser.add_argument("--audit", action="store_true", help="Compare local vs remote only")
    parser.add_argument("--pull", action="store_true", help="Pull live player/state files to local")
    parser.add_argument("--push", action="store_true", help="Build + push plugin/config to remote")
    parser.add_argument("--pull-worlds", action="store_true", help="Pull world/, world_nether/, world_the_end/ from remote")
    parser.add_argument("--full", action="store_true", help="Pull live state, build, push configs")
    args = parser.parse_args()

    if not any([args.audit, args.pull, args.push, args.full, args.pull_worlds]):
        args.full = True

    password = load_password()
    sftp = connect(password)
    try:
        if args.audit or args.full:
            audit(sftp)
        if args.pull or args.full:
            pull_live(sftp)
        if args.pull_worlds:
            pull_worlds(sftp)
        if args.push or args.full:
            build_jar()
            push_build(sftp)
        if args.push or args.full:
            print("\nSync push complete. Restart server, then run:")
            print("  /iacleancache items")
            print("  /iazip")
            print("  /iareload")
            print("  /fancyholograms reload")
            print("  /dm reload")
    finally:
        sftp.close()
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
