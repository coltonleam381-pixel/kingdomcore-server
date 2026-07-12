#!/usr/bin/env bash
# Upload changed server files to Humble Servers (AbilitySMP).
#
# Option A — panel (no script):
#   Humble panel → Files → upload these paths from this folder on your Mac.
#
# Option B — SFTP script:
#   export HUMBLE_SFTP_PASSWORD='your-panel-password'
#   ./deploy-to-humble.sh
#
# SFTP: sftp://eux1.humbleservers.com:2022  user: emnxwjpu.0fb44e8e

set -euo pipefail

ROOT="$(cd "$(dirname "$0")" && pwd)"
HOST="eux1.humbleservers.com"
PORT="2022"
USER="emnxwjpu.0fb44e8e"

echo "Building KingdomCore..."
(cd "$ROOT/plugins/KingdomCore" && ./gradlew build -x test -q)
cp "$ROOT/plugins/KingdomCore/build/libs/KingdomCore-1.0.0.jar" "$ROOT/plugins/KingdomCore.jar"
echo "Built: $ROOT/plugins/KingdomCore.jar ($(wc -c < "$ROOT/plugins/KingdomCore.jar") bytes)"

BATCH="$(mktemp)"
cat > "$BATCH" <<EOF
put "$ROOT/plugins/KingdomCore.jar" plugins/KingdomCore.jar
put "$ROOT/plugins/FancyHolograms/holograms.yml" plugins/FancyHolograms/holograms.yml
put "$ROOT/plugins/FancyNpcs/npcs.yml" plugins/FancyNpcs/npcs.yml
put "$ROOT/plugins/DeluxeMenus/gui_menus/abilitymen.yml" plugins/DeluxeMenus/gui_menus/abilitymen.yml
put "$ROOT/plugins/DeluxeMenus/gui_menus/abilitymenutwo.yml" plugins/DeluxeMenus/gui_menus/abilitymenutwo.yml
put "$ROOT/plugins/DeluxeMenus/gui_menus/ability_hulk.yml" plugins/DeluxeMenus/gui_menus/ability_hulk.yml
put "$ROOT/plugins/DeluxeMenus/gui_menus/ability_thor.yml" plugins/DeluxeMenus/gui_menus/ability_thor.yml
put "$ROOT/plugins/DeluxeMenus/gui_menus/ability_zeus.yml" plugins/DeluxeMenus/gui_menus/ability_zeus.yml
put "$ROOT/plugins/DeluxeMenus/gui_menus/ability_skybreaker.yml" plugins/DeluxeMenus/gui_menus/ability_skybreaker.yml
put "$ROOT/plugins/DeluxeMenus/gui_menus/ability_protocol_one.yml" plugins/DeluxeMenus/gui_menus/ability_protocol_one.yml
put "$ROOT/plugins/DeluxeMenus/gui_menus/ability_phasestep.yml" plugins/DeluxeMenus/gui_menus/ability_phasestep.yml
put "$ROOT/plugins/DeluxeMenus/gui_menus/ability_sanguinefog.yml" plugins/DeluxeMenus/gui_menus/ability_sanguinefog.yml
put "$ROOT/plugins/DeluxeMenus/gui_menus/ability_dash.yml" plugins/DeluxeMenus/gui_menus/ability_dash.yml
put "$ROOT/plugins/DeluxeMenus/gui_menus/ability_icenova.yml" plugins/DeluxeMenus/gui_menus/ability_icenova.yml
put "$ROOT/plugins/DeluxeMenus/gui_menus/ability_phasewalk.yml" plugins/DeluxeMenus/gui_menus/ability_phasewalk.yml
put "$ROOT/plugins/DeluxeMenus/gui_menus/ability_atlant.yml" plugins/DeluxeMenus/gui_menus/ability_atlant.yml
put "$ROOT/plugins/DeluxeMenus/gui_menus/ability_heartshield.yml" plugins/DeluxeMenus/gui_menus/ability_heartshield.yml
put "$ROOT/plugins/DeluxeMenus/gui_menus/ability_meteor.yml" plugins/DeluxeMenus/gui_menus/ability_meteor.yml
put "$ROOT/plugins/DeluxeMenus/gui_menus/ability_lifesteal.yml" plugins/DeluxeMenus/gui_menus/ability_lifesteal.yml
put "$ROOT/plugins/DeluxeMenus/gui_menus/ability_recall.yml" plugins/DeluxeMenus/gui_menus/ability_recall.yml
put "$ROOT/plugins/DeluxeMenus/gui_menus/ability_berserk.yml" plugins/DeluxeMenus/gui_menus/ability_berserk.yml
put "$ROOT/plugins/DeluxeMenus/gui_menus/ability_protector.yml" plugins/DeluxeMenus/gui_menus/ability_protector.yml
put "$ROOT/plugins/ItemsAdder/contents/kingdomcore/configs/recipes.yml" plugins/ItemsAdder/contents/kingdomcore/configs/recipes.yml
put "$ROOT/commands.yml" commands.yml
bye
EOF

if [[ -f "$ROOT/.humble-deploy.env" ]]; then
  # shellcheck disable=SC1091
  source "$ROOT/.humble-deploy.env"
fi

if [[ -z "${HUMBLE_SFTP_PASSWORD:-}" ]]; then
  echo "Missing password. Either:"
  echo "  1. Put it in $ROOT/.humble-deploy.env"
  echo "  2. export HUMBLE_SFTP_PASSWORD='...' before running this script"
  exit 1
fi

upload() {
  echo "Uploading to $USER@$HOST:$PORT ..."
  sftp -o StrictHostKeyChecking=accept-new -P "$PORT" -b "$BATCH" "$USER@$HOST"
}

if command -v sshpass >/dev/null 2>&1; then
  sshpass -p "$HUMBLE_SFTP_PASSWORD" upload
else
  echo "Note: install sshpass for automated upload (brew install hudochenkov/sshpass/sshpass)"
  echo "Trying sftp; password entry may not work in all environments."
  SSH_ASKPASS_REQUIRE=force SSH_ASKPASS=/bin/false upload || {
    echo "SFTP failed. Use Humble panel → Files → upload manually, or run this script in Terminal."
    exit 1
  }
fi

rm -f "$BATCH"

echo ""
echo "Upload complete. In the Humble panel console, run after restart:"
echo "  /fancyholograms reload"
echo "  /dm reload"
echo "  /iareload"
