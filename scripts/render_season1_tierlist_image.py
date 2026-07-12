#!/usr/bin/env python3
"""
Render Season 1 tier list poster (HTML → PNG) using Playwright.

Generates scripts/season1_tier_list_render.html and
scripts/output/season1_tierlist.png from the same data as season1_tier_list.py.

Usage:
  python3 scripts/render_season1_tierlist_image.py
  python3 scripts/render_season1_tierlist_image.py --local
"""

from __future__ import annotations

import argparse
import html
import importlib.util
import sys
from datetime import datetime, timezone
from pathlib import Path

SCRIPT_DIR = Path(__file__).resolve().parent
HTML_PATH = SCRIPT_DIR / "season1_tier_list_render.html"
OUTPUT_DIR = SCRIPT_DIR / "output"
PNG_PATH = OUTPUT_DIR / "season1_tierlist.png"

TIER_STYLES = {
    "S": {"label": "S-Tier", "accent": "#ff9f1a", "glow": "rgba(255, 159, 26, 0.35)"},
    "A": {"label": "A-Tier", "accent": "#a855f7", "glow": "rgba(168, 85, 247, 0.35)"},
    "B": {"label": "B-Tier", "accent": "#3b82f6", "glow": "rgba(59, 130, 246, 0.35)"},
    "C": {"label": "C-Tier", "accent": "#22c55e", "glow": "rgba(34, 197, 94, 0.35)"},
    "D": {"label": "D-Tier", "accent": "#94a3b8", "glow": "rgba(148, 163, 184, 0.25)"},
}

LEGENDARY_LABELS = {
    "mace": "Mace",
    "crown": "Crown",
    "scythe": "Scythe",
    "trident": "Trident",
    "warden_cp": "Warden CP",
}


def load_tier_module():
    path = SCRIPT_DIR / "season1_tier_list.py"
    spec = importlib.util.spec_from_file_location("season1_tier_list", path)
    if spec is None or spec.loader is None:
        raise RuntimeError(f"Cannot load {path}")
    module = importlib.util.module_from_spec(spec)
    sys.modules["season1_tier_list"] = module
    spec.loader.exec_module(module)
    return module


def display_name(raw: str) -> str:
    if not raw:
        return raw
    if raw.islower() or raw.isupper():
        return raw.title()
    return raw


def player_subtext(player, tl) -> str:
    kd = player.kills / max(player.deaths, 1)
    stats = tl.format_kd_stats(player).replace("*", "")
    base = f"{stats} · K/D {kd:.2f}"
    if player.legendaries:
        labels = [LEGENDARY_LABELS.get(x, x) for x in player.legendaries]
        return f"{base} · {', '.join(labels)}"
    return base


def build_html(players, tl) -> str:
    by_tier: dict[str, list] = {t: [] for t in "SABCD"}
    for p in players:
        by_tier[p.tier].append(p)

    tier_rows = []
    for tier in "SABCD":
        group = by_tier[tier]
        if not group:
            continue
        style = TIER_STYLES[tier]
        chips = []
        for p in group:
            chips.append(
                f"""
                <div class="player-chip" style="--chip-accent: {style['accent']}; --chip-glow: {style['glow']}">
                  <div class="player-name">{html.escape(display_name(p.name))}</div>
                  <div class="player-meta">{html.escape(player_subtext(p, tl))}</div>
                </div>
                """
            )
        tier_rows.append(
            f"""
            <section class="tier-row" style="--tier-accent: {style['accent']}; --tier-glow: {style['glow']}">
              <div class="tier-badge">{tier}</div>
              <div class="tier-players">{''.join(chips)}</div>
            </section>
            """
        )

    generated = datetime.now(timezone.utc).strftime("%Y-%m-%d %H:%M UTC")
    legend_note = html.escape(
        "Legendary ownership based on server owner recollection of season 1's final state — "
        "not a database record. Warden Chestplate credited to two players due to uncertainty."
    )
    stats_note = html.escape(tl.MANUAL_STATS_FOOTNOTE)

    return f"""<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="utf-8">
  <title>Season 1 Final Tier List</title>
  <style>
    * {{ box-sizing: border-box; margin: 0; padding: 0; }}
    body {{
      width: 1200px;
      background: linear-gradient(165deg, #0c0c12 0%, #151522 45%, #0f1018 100%);
      color: #eef0f6;
      font-family: "Segoe UI", system-ui, -apple-system, BlinkMacSystemFont, sans-serif;
      padding: 48px 40px 36px;
    }}
    .poster {{
      display: flex;
      flex-direction: column;
      gap: 22px;
    }}
    header {{
      text-align: center;
      margin-bottom: 8px;
    }}
    h1 {{
      font-size: 42px;
      font-weight: 800;
      letter-spacing: 0.02em;
      background: linear-gradient(90deg, #ffd166, #ff9f1a, #ff6b6b);
      -webkit-background-clip: text;
      background-clip: text;
      color: transparent;
      text-shadow: 0 0 40px rgba(255, 159, 26, 0.15);
    }}
    .subtitle {{
      margin-top: 8px;
      font-size: 15px;
      color: #9aa3b5;
      letter-spacing: 0.08em;
      text-transform: uppercase;
    }}
    .tier-row {{
      display: grid;
      grid-template-columns: 72px 1fr;
      gap: 16px;
      align-items: start;
      background: rgba(255, 255, 255, 0.03);
      border: 1px solid rgba(255, 255, 255, 0.06);
      border-left: 4px solid var(--tier-accent);
      border-radius: 14px;
      padding: 16px 18px;
      box-shadow: inset 0 0 24px var(--tier-glow);
    }}
    .tier-badge {{
      width: 56px;
      height: 56px;
      border-radius: 12px;
      display: flex;
      align-items: center;
      justify-content: center;
      font-size: 28px;
      font-weight: 900;
      color: #0c0c12;
      background: var(--tier-accent);
      box-shadow: 0 4px 18px var(--tier-glow);
    }}
    .tier-players {{
      display: flex;
      flex-wrap: wrap;
      gap: 10px;
      align-items: stretch;
    }}
    .player-chip {{
      min-width: 168px;
      max-width: 240px;
      flex: 1 1 168px;
      background: rgba(12, 12, 18, 0.85);
      border: 1px solid rgba(255, 255, 255, 0.08);
      border-top: 3px solid var(--chip-accent);
      border-radius: 10px;
      padding: 12px 14px 10px;
      box-shadow: 0 6px 16px rgba(0, 0, 0, 0.35);
    }}
    .player-name {{
      font-size: 20px;
      font-weight: 700;
      line-height: 1.2;
      color: #ffffff;
      margin-bottom: 6px;
      word-break: break-word;
    }}
    .player-meta {{
      font-size: 12px;
      line-height: 1.35;
      color: #a8b0c0;
    }}
    footer {{
      margin-top: 10px;
      padding-top: 18px;
      border-top: 1px solid rgba(255, 255, 255, 0.08);
      display: flex;
      flex-direction: column;
      gap: 10px;
    }}
    .footnote {{
      font-size: 12px;
      line-height: 1.45;
      color: #8b93a7;
    }}
    .footnote strong {{
      color: #c5cad6;
      font-weight: 600;
    }}
    .generated {{
      font-size: 11px;
      color: #636b7d;
      text-align: right;
      margin-top: 4px;
    }}
  </style>
</head>
<body>
  <div class="poster" id="poster">
    <header>
      <h1>Season 1 Final Tier List</h1>
      <div class="subtitle">Kingdom Server · Composite ranking</div>
    </header>
    {''.join(tier_rows)}
    <footer>
      <p class="footnote"><strong>Stats note:</strong> {stats_note}</p>
      <p class="footnote"><strong>Legendaries note:</strong> {legend_note}</p>
      <p class="generated">Generated {html.escape(generated)}</p>
    </footer>
  </div>
</body>
</html>
"""


def render_png(html_path: Path, png_path: Path) -> None:
    try:
        from playwright.sync_api import sync_playwright
    except ImportError as exc:
        raise RuntimeError(
            "Playwright is required. Run: pip install playwright && playwright install chromium"
        ) from exc

    file_url = html_path.resolve().as_uri()
    with sync_playwright() as p:
        browser = p.chromium.launch()
        page = browser.new_page(
            viewport={"width": 1200, "height": 900},
            device_scale_factor=2,
        )
        page.goto(file_url, wait_until="networkidle")
        poster = page.locator("#poster")
        poster.screenshot(path=str(png_path), type="png")
        browser.close()


def main() -> None:
    parser = argparse.ArgumentParser(description="Render Season 1 tier list PNG")
    parser.add_argument("--local", action="store_true")
    args = parser.parse_args()

    tl = load_tier_module()
    cfg = tl.load_config(tl.DEFAULT_CONFIG)
    players, _ = tl.load_ranked_players(cfg, local=args.local)

    html_content = build_html(players, tl)
    HTML_PATH.write_text(html_content, encoding="utf-8")
    OUTPUT_DIR.mkdir(parents=True, exist_ok=True)
    render_png(HTML_PATH, PNG_PATH)

    print(f"HTML: {HTML_PATH}")
    print(f"PNG:  {PNG_PATH}")


if __name__ == "__main__":
    main()
