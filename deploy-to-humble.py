#!/usr/bin/env python3
"""Upload server files to Humble Servers via SFTP."""

import os
import subprocess
import sys
from pathlib import Path

import paramiko

ROOT = Path(__file__).resolve().parent
HOST = "eux1.humbleservers.com"
PORT = 2022
USER = "emnxwjpu.0fb44e8e"

# Full deploy bundle (use sparingly — overwrites live configs).
FULL_UPLOADS = [
    ("plugins/KingdomCore.jar", ROOT / "plugins/KingdomCore.jar"),
    ("plugins/KingdomCore/config.yml", ROOT / "plugins/KingdomCore/config.yml"),
    ("config/paper-world-defaults.yml", ROOT / "config/paper-world-defaults.yml"),
    ("plugins/FancyHolograms/holograms.yml", ROOT / "plugins/FancyHolograms/holograms.yml"),
    ("plugins/FancyNpcs/npcs.yml", ROOT / "plugins/FancyNpcs/npcs.yml"),
    ("plugins/ItemsAdder/contents/kingdomcore/configs/items.yml",
     ROOT / "plugins/ItemsAdder/contents/kingdomcore/configs/items.yml"),
    ("plugins/ItemsAdder/contents/kingdomcore/configs/recipes.yml",
     ROOT / "plugins/ItemsAdder/contents/kingdomcore/configs/recipes.yml"),
    ("plugins/ItemsAdder/config.yml", ROOT / "plugins/ItemsAdder/config.yml"),
    ("plugins/ItemsAdder/contents/kingdomcore/models/item/mace.json",
     ROOT / "plugins/ItemsAdder/contents/kingdomcore/models/item/mace.json"),
    ("plugins/ItemsAdder/contents/kingdomcore/models/item/scythe.json",
     ROOT / "plugins/ItemsAdder/contents/kingdomcore/models/item/scythe.json"),
    ("plugins/ItemsAdder/contents/kingdomcore/models/item/trident.json",
     ROOT / "plugins/ItemsAdder/contents/kingdomcore/models/item/trident.json"),
    ("plugins/ItemsAdder/contents/kingdomcore/models/item/trident_throwing.json",
     ROOT / "plugins/ItemsAdder/contents/kingdomcore/models/item/trident_throwing.json"),
    ("plugins/ItemsAdder/contents/kingdomcore/models/item/ia_auto/trident_icon.json",
     ROOT / "plugins/ItemsAdder/contents/kingdomcore/models/item/ia_auto/trident_icon.json"),
    ("plugins/ItemsAdder/contents/kingdomcore/textures/item/trident.png",
     ROOT / "plugins/ItemsAdder/contents/kingdomcore/textures/item/trident.png"),
    ("plugins/ItemsAdder/contents/kingdomcore/textures/item/trident_material.png",
     ROOT / "plugins/ItemsAdder/contents/kingdomcore/textures/item/trident_material.png"),
    ("plugins/ItemsAdder/contents/kingdomcore/resourcepack/kingdomcore/textures/item/trident_material.png",
     ROOT / "plugins/ItemsAdder/contents/kingdomcore/resourcepack/kingdomcore/textures/item/trident_material.png"),
    ("plugins/ItemsAdder/contents/kingdomcore/textures/item/trident_icon.png",
     ROOT / "plugins/ItemsAdder/contents/kingdomcore/textures/item/trident_icon.png"),
    ("commands.yml", ROOT / "commands.yml"),
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

GUI_MENUS = ROOT / "plugins/DeluxeMenus/gui_menus"
for menu_file in sorted(GUI_MENUS.glob("*.yml")):
    FULL_UPLOADS.append((f"plugins/DeluxeMenus/gui_menus/{menu_file.name}", menu_file))

# Safe hotfix deploy: plugin jar + config + holograms.
FIX_UPLOADS = [
    ("plugins/KingdomCore.jar", ROOT / "plugins/KingdomCore.jar"),
    ("plugins/KingdomCore/config.yml", ROOT / "plugins/KingdomCore/config.yml"),
    ("plugins/FancyHolograms/holograms.yml", ROOT / "plugins/FancyHolograms/holograms.yml"),
]


def validate_jar(jar_path: Path) -> None:
    """Reject macOS-corrupted jars (duplicate extract names like 'Foo 2.class')."""
    listing = subprocess.check_output(["jar", "tf", str(jar_path)], text=True)
    bad = [line for line in listing.splitlines() if " 2.class" in line or " 3.class" in line]
    if bad:
        sample = ", ".join(bad[:3])
        raise RuntimeError(
            f"Corrupt jar {jar_path}: {len(bad)} macOS duplicate entries (e.g. {sample}). "
            "Run './gradlew clean build' in plugins/KingdomCore and redeploy."
        )
    kingdomcore = [line for line in listing.splitlines() if line.startswith("com/yourorg/kingdomcore/")]
    if not kingdomcore:
        raise RuntimeError(f"Jar {jar_path} has no KingdomCore classes — wrong artifact?")


def build_jar() -> None:
    print("Building KingdomCore...")
    subprocess.run(
        ["./gradlew", "clean", "build", "-x", "test", "-q"],
        cwd=ROOT / "plugins/KingdomCore",
        check=True,
    )
    src = ROOT / "plugins/KingdomCore/build/libs/KingdomCore-1.0.0.jar"
    dst = ROOT / "plugins/KingdomCore.jar"
    validate_jar(src)
    dst.write_bytes(src.read_bytes())
    validate_jar(dst)
    print(f"Built {dst} ({dst.stat().st_size} bytes)")


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
        print("Set HUMBLE_SFTP_PASSWORD, .humble-deploy.env, or .vscode/sftp.json password.", file=sys.stderr)
        return ""
    return password


def main() -> int:
    password = load_password()
    if not password:
        return 1

    uploads = FIX_UPLOADS if "--fix" in sys.argv else FULL_UPLOADS
    if "--fix" in sys.argv:
        print("Fix deploy: KingdomCore jar + config + holograms.")
    else:
        print("Full deploy: jar + configs (use --fix for hotfixes only).")

    build_jar()

    transport = paramiko.Transport((HOST, PORT))
    transport.connect(username=USER, password=password)
    sftp = paramiko.SFTPClient.from_transport(transport)

    try:
        for remote, local in uploads:
            if not local.exists():
                print(f"SKIP missing: {local}")
                continue
            remote_dir = os.path.dirname(remote)
            parts = []
            for part in remote_dir.split("/"):
                parts.append(part)
                path = "/".join(parts)
                try:
                    sftp.stat(path)
                except OSError:
                    try:
                        sftp.mkdir(path)
                    except OSError:
                        pass
            print(f"PUT {remote} ({local.stat().st_size} bytes)")
            sftp.put(str(local), remote)
        if uploads is FULL_UPLOADS:
            for remote in REMOTE_DELETES:
                try:
                    sftp.remove(remote)
                    print(f"DEL {remote}")
                except OSError:
                    pass
    finally:
        sftp.close()
        transport.close()

    print("\nUpload complete.")
    print(">>> FULL RESTART required in Humble panel (panel.humbleservers.com) — NOT /reload <<<")
    print(">>> Reload commands cannot load a plugin that failed at startup. <<<")
    print("After restart: python3 verify-humble-kc.py  (should say OK)")
    if uploads is FIX_UPLOADS:
        print("  /fancyholograms reload")
        print("  /papi reload")
    else:
        print("  /iacleancache items")
        print("  /iazip")
        print("  /iareload")
        print("  /fancyholograms reload")
        print("  /dm reload")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
