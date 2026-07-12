#!/usr/bin/env python3
"""
Season 1 Final Tier List — one-time snapshot generator (FINAL).

TODO (Season 2 — KingdomCore tracking migration): public kill/death stats should
exclude team-kills / friendly-fire; only count kills/deaths vs non-teammates.
Likely hook: TabTeamResolver.isSameTeam() in the PvP death / heart-transfer path.

Pulls KingdomCore SQLite + vanilla world/stats JSON over SFTP (or local paths),
applies manual stat overrides and season-final legendary ownership (owner
recollection — not DB/inventory), computes composite scores, and optionally
posts to Discord.

Legendaries are sourced from MANUAL_LEGENDARY_HOLDERS below, not live scans.
The server is offline; no rejoin or inventory scan is required.

Usage:
  python3 scripts/season1_tier_list.py              # preview only (default)
  python3 scripts/season1_tier_list.py --post       # also post to Discord
  python3 scripts/season1_tier_list.py --local      # skip SFTP, use local paths

Configure via scripts/season1_tier_list_config.json (see .example file).
"""

from __future__ import annotations

import argparse
import json
import sqlite3
import sys
import urllib.request
from dataclasses import dataclass, field
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

try:
    import paramiko
except ImportError:
    paramiko = None  # type: ignore


SCRIPT_DIR = Path(__file__).resolve().parent
ROOT = SCRIPT_DIR.parent
DEFAULT_CONFIG = SCRIPT_DIR / "season1_tier_list_config.json"
EXAMPLE_CONFIG = SCRIPT_DIR / "season1_tier_list_config.example.json"

# --- Season 1 final manual adjustments (case-insensitive name keys) ---

EXCLUDED_PLAYERS = {"gameaxion", "d1qiiiw", "fw3y"}

MANUAL_STAT_OVERRIDES = {
    "HamiltonLMAO": {"kills": 12},
    "StrunaOtStunny": {"kills": 5, "deaths": 3},
    "dumb4reason": {"deaths": 5},
    "kristoferrrrr": {"kills": 6, "deaths": 3},
    "landonorris": {"kills": 1, "deaths": 0},
    "Bernardo2079": {"kills": 3, "deaths": 3, "hearts": 14},
    "Alkns": {"kills": 2, "deaths": 1},
    "ezz_player": {"kills": 7, "deaths": 3, "hearts": 14},
    "cofi5": {"deaths": 5},
    "Yosaifmrj_": {"deaths": 4},
}

MANUAL_STATS_FOOTNOTE = (
    "Kill/death stats for 10 players were manually corrected by the server owner due to "
    "confirmed inaccuracies in vanilla stat tracking; remaining players use unverified vanilla stats."
)

MANUAL_LEGENDARY_HOLDERS = {
    "kristoferrrrr": ["mace"],
    "StrunaOtStunny": ["trident", "warden_cp"],
    "Bernardo2079": ["scythe"],
    "dumb4reason": ["warden_cp"],
    "landonorris": ["crown"],
}

MANUAL_TIER_OVERRIDES = {
    "StrunaOtStunny": "A",
    "ezz_player": "A",
}

DATA_QUALITY_DISCLAIMER = (
    "⚠️ **Note:** Legendary item ownership in this list is based on the server "
    "owner's recollection of season 1's final state, not a database record — "
    "some uncertainty exists (see Warden Chestplate, credited to two players)."
)

ASSASSIN_EVENT_NOTES = (
    "Assassin event: no confirmed winner this season — DB bonus flag not used for scoring. "
    "Informational (not scored): StrunaOtStunny had the most assassin target eliminations (2) "
    "among participants."
)

LEGENDARY_ITEM_COUNT = 5  # mace, crown, scythe, trident, warden_cp

# Assassin category removed (no confirmed S1 winner). Weights rebalanced so
# K/D is weighted above hearts (not tied).
DEFAULT_WEIGHTS = {
    "kd": 35.0,
    "hearts": 22.0,
    "playtime": 17.0,
    "legendary": 26.0,
}


@dataclass
class PlayerRow:
    uuid: str
    name: str
    kills: int = 0
    deaths: int = 0
    kills_manual: bool = False
    deaths_manual: bool = False
    hearts_manual: bool = False
    playtime_hours: float = 0.0
    progression_hearts: int = 0
    ability_id: str | None = None
    ability_level: int = 0
    legendaries: list[str] = field(default_factory=list)
    kd_raw: float = 0.0
    kd_norm: float = 0.0
    hearts_norm: float = 0.0
    play_norm: float = 0.0
    legendary_norm: float = 0.0
    score: float = 0.0
    tier: str = "D"


def _norm_name(name: str) -> str:
    return name.strip().lower()


def _lookup_ci(mapping: dict[str, Any], player_name: str) -> Any | None:
    key = _norm_name(player_name)
    for k, v in mapping.items():
        if _norm_name(k) == key:
            return v
    return None


def apply_manual_overrides(
    name: str, kills: int, deaths: int, hearts: int
) -> tuple[int, int, int, bool, bool, bool]:
    override = _lookup_ci(MANUAL_STAT_OVERRIDES, name)
    kills_manual = deaths_manual = hearts_manual = False
    if isinstance(override, dict):
        if "kills" in override:
            kills = int(override["kills"])
            kills_manual = True
        if "deaths" in override:
            deaths = int(override["deaths"])
            deaths_manual = True
        if "hearts" in override:
            hearts = int(override["hearts"])
            hearts_manual = True
    return kills, deaths, hearts, kills_manual, deaths_manual, hearts_manual


def apply_stat_overrides(name: str, kills: int, deaths: int) -> tuple[int, int, bool, bool]:
    k, d, _, km, dm, _ = apply_manual_overrides(name, kills, deaths, 0)
    return k, d, km, dm


def format_hearts(p: PlayerRow) -> str:
    return f"{p.progression_hearts}*" if p.hearts_manual else str(p.progression_hearts)


def format_kd_stats(p: PlayerRow) -> str:
    k = f"{p.kills}*" if p.kills_manual else str(p.kills)
    d = f"{p.deaths}*" if p.deaths_manual else str(p.deaths)
    return f"{k}/{d}"


def compute_kd_raw(kills: int, deaths: int) -> float:
    """Volume + efficiency: kills * sqrt(kills / max(deaths, 1))."""
    if kills <= 0:
        return 0.0
    efficiency = kills / max(deaths, 1)
    return kills * (efficiency**0.5)


def load_config(path: Path) -> dict[str, Any]:
    if not path.exists():
        if EXAMPLE_CONFIG.exists():
            print(f"Config not found at {path}. Copy {EXAMPLE_CONFIG.name} and edit it.", file=sys.stderr)
        else:
            print(f"Config not found: {path}", file=sys.stderr)
        sys.exit(1)
    return json.loads(path.read_text())


def load_password(cfg: dict[str, Any]) -> str:
    import os

    password = os.environ.get("HUMBLE_SFTP_PASSWORD", "").strip()
    if password:
        return password
    env_file = ROOT / ".humble-deploy.env"
    if env_file.exists():
        for line in env_file.read_text().splitlines():
            if line.startswith("HUMBLE_SFTP_PASSWORD="):
                password = line.split("=", 1)[1].strip().strip("'\"")
                if password:
                    return password
    sftp_json = ROOT / ".vscode/sftp.json"
    if sftp_json.exists():
        password = str(json.loads(sftp_json.read_text()).get("password", "")).strip()
        if password:
            return password
    return str(cfg.get("sftp", {}).get("password", "")).strip()


def sftp_connect(cfg: dict[str, Any]):
    if paramiko is None:
        raise RuntimeError("paramiko is required for remote fetch. pip install paramiko")
    sftp_cfg = cfg.get("sftp", {})
    host = sftp_cfg.get("host", "eux1.humbleservers.com")
    port = int(sftp_cfg.get("port", 2022))
    user = sftp_cfg.get("username", "emnxwjpu.0fb44e8e")
    password = load_password(cfg)
    if not password:
        raise RuntimeError("SFTP password not found (HUMBLE_SFTP_PASSWORD, .humble-deploy.env, or config)")
    transport = paramiko.Transport((host, port))
    transport.connect(username=user, password=password)
    return paramiko.SFTPClient.from_transport(transport), transport


def fetch_remote_file(sftp, remote: str, local: Path) -> None:
    local.parent.mkdir(parents=True, exist_ok=True)
    sftp.get(remote, str(local))


def load_kc_players(db_path: Path) -> dict[str, dict[str, Any]]:
    conn = sqlite3.connect(db_path)
    conn.row_factory = sqlite3.Row
    rows = conn.execute(
        "SELECT uuid, last_name, progression_hearts, assassin_win_bonus, ability_id, ability_level "
        "FROM player_state"
    ).fetchall()
    abilities = {
        r["owner_uuid"].lower(): r["ability_id"]
        for r in conn.execute("SELECT owner_uuid, ability_id FROM ability_owners")
    }
    conn.close()
    out: dict[str, dict[str, Any]] = {}
    for r in rows:
        uuid = r["uuid"].lower()
        out[uuid] = {
            "name": r["last_name"] or uuid[:8],
            "progression_hearts": int(r["progression_hearts"] or 0),
            "assassin_win_bonus": int(r["assassin_win_bonus"] or 0),
            "ability_id": abilities.get(uuid) or r["ability_id"],
            "ability_level": int(r["ability_level"] or 0),
        }
    return out


def load_vanilla_stats(stats_dir: Path) -> dict[str, dict[str, Any]]:
    out: dict[str, dict[str, Any]] = {}
    if not stats_dir.exists():
        return out
    for path in stats_dir.glob("*.json"):
        uuid = path.stem.lower()
        try:
            data = json.loads(path.read_text())
        except json.JSONDecodeError:
            continue
        custom = data.get("stats", {}).get("minecraft:custom", {})
        kills = int(custom.get("minecraft:player_kills", 0))
        deaths = int(custom.get("minecraft:deaths", 0))
        play_ticks = int(custom.get("minecraft:play_time", 0))
        out[uuid] = {
            "kills": kills,
            "deaths": deaths,
            "playtime_hours": play_ticks / 20 / 3600,
        }
    return out


def build_players(
    kc: dict[str, dict[str, Any]],
    stats: dict[str, dict[str, Any]],
) -> list[PlayerRow]:
    uuids = {
        u
        for u in stats
        if u in kc
        and (
            stats[u].get("playtime_hours", 0) > 0
            or stats[u].get("kills", 0) > 0
            or stats[u].get("deaths", 0) > 0
        )
    }
    players: list[PlayerRow] = []

    for uuid in sorted(uuids):
        kc_row = kc[uuid]
        stat_row = stats.get(uuid, {})
        play_hrs = float(stat_row.get("playtime_hours", 0))
        kills = int(stat_row.get("kills", 0))
        deaths = int(stat_row.get("deaths", 0))
        if play_hrs <= 0 and kills <= 0 and deaths <= 0:
            continue

        name = kc_row.get("name") or uuid[:8]
        if _norm_name(name) in EXCLUDED_PLAYERS:
            continue

        kills, deaths, hearts, kills_manual, deaths_manual, hearts_manual = apply_manual_overrides(
            name, kills, deaths, int(kc_row.get("progression_hearts", 0))
        )

        legendaries = list(_lookup_ci(MANUAL_LEGENDARY_HOLDERS, name) or [])

        players.append(
            PlayerRow(
                uuid=uuid,
                name=name,
                kills=kills,
                deaths=deaths,
                kills_manual=kills_manual,
                deaths_manual=deaths_manual,
                hearts_manual=hearts_manual,
                playtime_hours=play_hrs,
                progression_hearts=hearts,
                ability_id=kc_row.get("ability_id"),
                ability_level=int(kc_row.get("ability_level", 0)),
                legendaries=legendaries,
            )
        )
    return players


def score_players(players: list[PlayerRow], weights: dict[str, float]) -> None:
    if not players:
        return
    max_hearts = max((p.progression_hearts for p in players), default=1) or 1
    max_play = max((p.playtime_hours for p in players), default=1.0) or 1.0

    for p in players:
        p.kd_raw = compute_kd_raw(p.kills, p.deaths)

    max_kd_raw = max((p.kd_raw for p in players), default=1.0) or 1.0

    for p in players:
        p.kd_norm = p.kd_raw / max_kd_raw
        p.hearts_norm = p.progression_hearts / max_hearts
        p.play_norm = p.playtime_hours / max_play
        p.legendary_norm = min(len(p.legendaries) / LEGENDARY_ITEM_COUNT, 1.0)
        p.score = (
            weights["kd"] * p.kd_norm
            + weights["hearts"] * p.hearts_norm
            + weights["playtime"] * p.play_norm
            + weights["legendary"] * p.legendary_norm
        )

    players.sort(key=lambda x: (-x.score, -x.kills, x.name.lower()))


def assign_tiers(players: list[PlayerRow]) -> str:
    n = len(players)
    if n == 0:
        return "none"
    cuts = {
        "S": max(1, round(n * 0.10)),
        "A": max(1, round(n * 0.20)),
        "B": max(1, round(n * 0.30)),
        "C": max(1, round(n * 0.25)),
    }
    idx = 0
    for tier, count in [("S", cuts["S"]), ("A", cuts["A"]), ("B", cuts["B"]), ("C", cuts["C"])]:
        for _ in range(count):
            if idx >= n:
                break
            players[idx].tier = tier
            idx += 1
    while idx < n:
        players[idx].tier = "D"
        idx += 1
    return (
        f"Percentile buckets on {n} players: S=top 10%, A=next 20%, B=next 30%, "
        f"C=next 25%, D=remainder"
    )


def apply_tier_overrides(players: list[PlayerRow]) -> None:
    for p in players:
        tier = _lookup_ci(MANUAL_TIER_OVERRIDES, p.name)
        if tier:
            p.tier = tier


def format_kd_component_table(players: list[PlayerRow]) -> str:
    max_raw = max((p.kd_raw for p in players), default=1.0) or 1.0
    sorted_players = sorted(players, key=lambda p: (-p.kd_raw, p.name.lower()))
    lines = [
        "K/D component (volume + efficiency)",
        "raw = kills × sqrt(kills / max(deaths, 1))  |  norm = raw / pool_max",
        f"pool max raw = {max_raw:.2f}",
        "",
        f"{'Player':<16} {'K/D stats':>10} {'raw':>8} {'norm':>6}",
    ]
    for p in sorted_players:
        lines.append(
            f"{p.name[:16]:<16} {format_kd_stats(p):>10} {p.kd_raw:8.2f} {p.kd_norm:6.3f}"
        )
    return "\n".join(lines)


def format_breakdown_table(players: list[PlayerRow], weights: dict[str, float]) -> str:
    lines = [
        "Season 1 Final Tier List — score breakdown",
        f"Formula (0–100): {weights['kd']:.2f}×K/D + {weights['hearts']:.2f}×Hearts + "
        f"{weights['playtime']:.2f}×Play + {weights['legendary']:.2f}×Legendaries",
        "(Assassin category removed — 15 pts redistributed proportionally across the four above)",
        "K/D norm = raw / max(raw) where raw = kills × sqrt(kills / max(deaths, 1))",
        "Hearts norm = hearts/max | Play norm = hrs/max | Legendary = manual items / 5",
        "* = manual kill/death/heart override",
        MANUAL_STATS_FOOTNOTE,
        ASSASSIN_EVENT_NOTES,
        f"Excluded: {', '.join(sorted(EXCLUDED_PLAYERS))}",
        "",
        f"{'Tier':<4} {'Player':<16} {'Score':>5} {'K/D':>5} {'Hrt':>4} {'Play':>4} "
        f"{'Leg':>3}  K/D pts  Hrt  Play  Leg  Details",
    ]
    for p in players:
        kd_pts = weights["kd"] * p.kd_norm
        h_pts = weights["hearts"] * p.hearts_norm
        pl_pts = weights["playtime"] * p.play_norm
        l_pts = weights["legendary"] * p.legendary_norm
        kd = p.kills / max(p.deaths, 1)
        leg = ",".join(p.legendaries) if p.legendaries else "—"
        kd_stat = format_kd_stats(p)
        details = f"{kd_stat} kd={kd:.2f} {format_hearts(p)}h {p.playtime_hours:.1f}hr"
        if p.ability_id:
            details += f" {p.ability_id}L{p.ability_level}"
        lines.append(
            f"{p.tier:<4} {p.name[:16]:<16} {p.score:5.1f} {p.kd_norm:5.2f} {p.hearts_norm:4.2f} "
            f"{p.play_norm:4.2f} {p.legendary_norm:3.2f}  "
            f"{kd_pts:5.1f} {h_pts:4.1f} {pl_pts:4.1f} {l_pts:4.1f}  {details} [{leg}]"
        )
    return "\n".join(lines)


def tier_summary_discord(players: list[PlayerRow], tier_method: str) -> list[str]:
    by_tier: dict[str, list[PlayerRow]] = {t: [] for t in "SABCD"}
    for p in players:
        by_tier[p.tier].append(p)
    now = datetime.now(timezone.utc).strftime("%Y-%m-%d %H:%M UTC")

    tier_blocks: list[str] = []
    for tier in "SABCD":
        group = by_tier[tier]
        if not group:
            continue
        chunk_lines = [f"**{tier}-Tier** ({len(group)})"]
        for p in group:
            kd = p.kills / max(p.deaths, 1)
            extras = []
            if p.legendaries:
                extras.append("+".join(p.legendaries))
            if p.ability_id:
                extras.append(p.ability_id)
            extra = f" — {', '.join(extras)}" if extras else ""
            chunk_lines.append(
                f"• **{p.name}** ({p.score:.1f}) — {format_hearts(p)}♥ K/D {kd:.2f} "
                f"({format_kd_stats(p)}){extra}"
            )
        tier_blocks.append("\n".join(chunk_lines))

    header = (
        f"## 🏆 Season 1 Final Tier List\n_{now}_\n\n"
        f"{DATA_QUALITY_DISCLAIMER}\n\n"
        f"_{MANUAL_STATS_FOOTNOTE}_\n\n"
    )
    body = "\n\n".join(tier_blocks)
    meta = f"\n\n_{tier_method}_\n_{ASSASSIN_EVENT_NOTES}_"
    full = header + body + meta
    if len(full) <= 1900:
        return [full]

    result: list[str] = []
    current = header
    for block in tier_blocks:
        if len(current) + len(block) + len(meta) + 10 > 1900:
            result.append(current.rstrip())
            current = block + "\n\n"
        else:
            current += block + "\n\n"
    current += meta.lstrip()
    result.append(current.rstrip())
    return result


def post_discord(webhook_url: str, content: str) -> None:
    if not webhook_url or webhook_url == "YOUR_DISCORD_WEBHOOK_URL_HERE":
        raise RuntimeError("Set discord_webhook_url in config before --post")
    payload = json.dumps({"content": content[:2000]}).encode("utf-8")
    req = urllib.request.Request(
        webhook_url,
        data=payload,
        headers={"Content-Type": "application/json", "User-Agent": "Season1TierList/1.0"},
        method="POST",
    )
    with urllib.request.urlopen(req, timeout=30) as resp:
        if resp.status >= 300:
            raise RuntimeError(f"Discord webhook HTTP {resp.status}")


def fetch_data(cfg: dict[str, Any], cache_dir: Path) -> tuple[Path, Path]:
    if cfg.get("mode") == "local":
        return Path(cfg["local"]["kingdomcore_db"]), Path(cfg["local"]["stats_dir"])

    if paramiko is None:
        raise RuntimeError("Remote mode requires: pip install paramiko")

    cache_dir.mkdir(parents=True, exist_ok=True)
    sftp, transport = sftp_connect(cfg)
    try:
        db_path = cache_dir / "kingdomcore.db"
        fetch_remote_file(sftp, cfg["remote"]["kingdomcore_db"], db_path)

        stats_dir = cache_dir / "stats"
        stats_dir.mkdir(exist_ok=True)
        for fname in sftp.listdir(cfg["remote"]["stats_dir"]):
            if fname.endswith(".json"):
                fetch_remote_file(sftp, f"{cfg['remote']['stats_dir']}/{fname}", stats_dir / fname)
        return db_path, stats_dir
    finally:
        sftp.close()
        transport.close()


def load_ranked_players(cfg: dict[str, Any] | None = None, *, local: bool = False) -> tuple[list[PlayerRow], str]:
    """Load data, apply overrides, score, and assign tiers. Used by text + image output."""
    if cfg is None:
        cfg = load_config(DEFAULT_CONFIG)
    if local:
        cfg["mode"] = "local"
    cache_dir = Path(cfg.get("cache_dir", SCRIPT_DIR / ".season1_cache"))
    db_path, stats_dir = fetch_data(cfg, cache_dir)
    kc = load_kc_players(db_path)
    stats = load_vanilla_stats(stats_dir)
    players = build_players(kc, stats)
    weights = {k: v for k, v in {**DEFAULT_WEIGHTS, **cfg.get("weights", {})}.items() if k != "assassin"}
    score_players(players, weights)
    tier_method = assign_tiers(players)
    apply_tier_overrides(players)
    return players, tier_method


def main() -> None:
    parser = argparse.ArgumentParser(description="Season 1 Final Tier List generator")
    parser.add_argument("--config", type=Path, default=DEFAULT_CONFIG)
    parser.add_argument("--post", action="store_true", help="Post to Discord webhook (default is preview only)")
    parser.add_argument("--local", action="store_true", help="Use local paths from config")
    args = parser.parse_args()

    cfg = load_config(args.config)
    weights = {k: v for k, v in {**DEFAULT_WEIGHTS, **cfg.get("weights", {})}.items() if k != "assassin"}

    players, tier_method = load_ranked_players(cfg, local=bool(args.local))

    print(format_breakdown_table(players, weights))
    print()
    print(format_kd_component_table(players))
    print()
    print("Manual stat overrides applied:")
    for key, expected in MANUAL_STAT_OVERRIDES.items():
        match = next((p for p in players if _norm_name(p.name) == _norm_name(key)), None)
        if match is None:
            print(f"  {key}: NOT FOUND in player pool")
            continue
        ok_k = expected.get("kills", match.kills) == match.kills if "kills" in expected else True
        ok_d = expected.get("deaths", match.deaths) == match.deaths if "deaths" in expected else True
        ok_h = expected.get("hearts", match.progression_hearts) == match.progression_hearts if "hearts" in expected else True
        status = "OK" if ok_k and ok_d and ok_h else "MISMATCH"
        extra = f" {format_hearts(match)}h" if "hearts" in expected else ""
        print(f"  {match.name}: {format_kd_stats(match)}{extra} [{status}]")
    print()
    print(tier_method)
    print(f"Eligible players: {len(players)}")
    print(f"Excluded (not in output): {', '.join(sorted(EXCLUDED_PLAYERS))}")

    holders = [
        f"{name}: {', '.join(items)}"
        for name, items in MANUAL_LEGENDARY_HOLDERS.items()
    ]
    print("\nManual legendary holders:")
    for line in holders:
        print(f"  {line}")

    pages = tier_summary_discord(players, tier_method)
    print("\n--- Discord preview ---")
    for i, page in enumerate(pages, 1):
        print(f"\n[Page {i}/{len(pages)}]\n{page}")

    if args.post:
        webhook = cfg.get("discord_webhook_url", "")
        for i, page in enumerate(pages, 1):
            suffix = f" (page {i}/{len(pages)})" if len(pages) > 1 else ""
            post_discord(webhook, page.replace("## 🏆", f"## 🏆{suffix}"))
        print("\nPosted to Discord.", file=sys.stderr)


if __name__ == "__main__":
    main()
