#!/usr/bin/env python3
"""Check whether KingdomCore is healthy on the live Humble server."""

import json
import subprocess
import sys
import tempfile
from pathlib import Path

import paramiko

ROOT = Path(__file__).resolve().parent
HOST = "eux1.humbleservers.com"
PORT = 2022
USER = "emnxwjpu.0fb44e8e"


def load_password() -> str:
    password = __import__("os").environ.get("HUMBLE_SFTP_PASSWORD", "").strip()
    env_file = ROOT / ".humble-deploy.env"
    if not password and env_file.exists():
        for line in env_file.read_text().splitlines():
            if line.startswith("HUMBLE_SFTP_PASSWORD="):
                password = line.split("=", 1)[1].strip().strip("'\"")
    if not password:
        data = json.loads((ROOT / ".vscode/sftp.json").read_text())
        password = str(data.get("password", "")).strip()
    return password


def main() -> int:
    password = load_password()
    if not password:
        print("Missing SFTP password.", file=sys.stderr)
        return 1

    transport = paramiko.Transport((HOST, PORT))
    transport.connect(username=USER, password=password)
    sftp = paramiko.SFTPClient.from_transport(transport)

    jar_path = "plugins/KingdomCore.jar"
    jar_size = sftp.stat(jar_path).st_size
    with tempfile.NamedTemporaryFile(suffix=".jar", delete=False) as tmp:
        tmp_path = Path(tmp.name)
    sftp.get(jar_path, str(tmp_path))

    log_path = Path(tempfile.mkstemp(suffix=".log")[1])
    sftp.get("logs/latest.log", str(log_path))
    sftp.close()
    transport.close()

    listing = subprocess.check_output(["jar", "tf", str(tmp_path)], text=True)
    bad = [line for line in listing.splitlines() if " 2.class" in line or " 3.class" in line]
    hulk = [line for line in listing.splitlines() if "HulkAbility" in line]

    print(f"Remote jar: {jar_size} bytes, HulkAbility entries: {len(hulk)}, corrupt names: {len(bad)}")
    if bad:
        print("FAIL: jar still has macOS duplicate class names — redeploy with deploy-to-humble.py --fix")
        return 2
    if jar_size < 14_700_000 or jar_size > 14_850_000:
        print("WARN: unexpected jar size (expected ~14791233)")

    text = log_path.read_text(errors="replace")
    enabled = [line for line in text.splitlines() if "Enabling KingdomCore" in line]
    markers = [line for line in text.splitlines() if "Build marker: KC_RENAME_HUD_FIX_" in line]
    remap_fail = [line for line in text.splitlines() if "Failed to remap plugin jar 'plugins/KingdomCore.jar'" in line]

    if enabled:
        print("OK: KingdomCore enabled in latest.log")
        if markers:
            print(f"     {markers[-1].strip()}")
        return 0

    if remap_fail:
        print("FAIL: Paper remapper rejected KingdomCore.jar at last server START")
        print("      Fix jar + full panel RESTART (reload commands do not load plugins)")
        return 3

    print("FAIL: KingdomCore never enabled in latest.log — server needs a full RESTART from panel")
    print("      After restart, run: /fancyholograms reload  then  /papi reload")
    return 4


if __name__ == "__main__":
    raise SystemExit(main())
