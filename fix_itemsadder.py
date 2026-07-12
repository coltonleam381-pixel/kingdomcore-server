import os
import json
import shutil
import re

SERVER_DIR = "/Users/markginzburg/Desktop/My server/Saves/Minecraft Server"
IA_DIR = os.path.join(SERVER_DIR, "plugins/ItemsAdder/contents/kingdomcore")
MODELS_DIR = os.path.join(IA_DIR, "resourcepack/kingdomcore/models/item")
TEXTURES_DIR = os.path.join(IA_DIR, "resourcepack/kingdomcore/textures/item")
ARMOR_DIR = os.path.join(IA_DIR, "resourcepack/kingdomcore/textures/armor")
CONFIGS_DIR = os.path.join(IA_DIR, "configs")

os.makedirs(MODELS_DIR, exist_ok=True)
os.makedirs(TEXTURES_DIR, exist_ok=True)
os.makedirs(ARMOR_DIR, exist_ok=True)
os.makedirs(CONFIGS_DIR, exist_ok=True)

SOURCE_TEXTURES = "/Users/markginzburg/Desktop/kingdome core custom items/Textures"
SOURCE_MODELS_DIR = "/Users/markginzburg/Desktop/ItemsAdder/contents/kingdomcore/resourcepack/assets/kingdomcore/models/item"

def make_safe(name):
    name = os.path.basename(name)
    if name.endswith('.png'):
        name = name[:-4]
    name = name.lower()
    name = re.sub(r'[^a-z0-9_]', '_', name)
    name = re.sub(r'_+', '_', name)
    return name.strip('_')

# Copy armor layer
warden_cp_src = os.path.join(SOURCE_TEXTURES, "warden_cp.png")
if os.path.exists(warden_cp_src):
    shutil.copy(warden_cp_src, os.path.join(ARMOR_DIR, "warden_layer_1.png"))
elif os.path.exists(os.path.join(SERVER_DIR, "plugins/Warden_CP/armor_layer_1.png")):
    shutil.copy(os.path.join(SERVER_DIR, "plugins/Warden_CP/armor_layer_1.png"), os.path.join(ARMOR_DIR, "warden_layer_1.png"))

# Models mapping
models_to_process = {
    "mace.json": "mace",
    "model.json": "scythe",
    "heart.json": "heart",
    "medium_heart.json": "medium_heart",
    "small_heart.json": "small_heart"
}

# Add Crown.json from Textures folder
crown_src = os.path.join(SOURCE_TEXTURES, "Crown.json")
if os.path.exists(crown_src):
    with open(crown_src, 'r') as f:
        crown_data = json.load(f)
else:
    crown_data = None

def process_model(src_path, dest_name, data=None):
    if data is None:
        if not os.path.exists(src_path):
            return
        with open(src_path, 'r') as f:
            data = json.load(f)
    
    if "textures" in data:
        tex_dict = data["textures"]
        if isinstance(tex_dict, dict):
            for k, v in tex_dict.items():
                if isinstance(v, str) and not v.startswith("minecraft:"):
                    # Find matching png
                    safe_name = make_safe(v)
                    
                    # Search for original
                    found = False
                    for f in os.listdir(SOURCE_TEXTURES):
                        if f.endswith('.png') and f[:-4] in v:
                            shutil.copy(os.path.join(SOURCE_TEXTURES, f), os.path.join(TEXTURES_DIR, f"{safe_name}.png"))
                            found = True
                            break
                    if not found and '/' in v:
                        basename = os.path.basename(v)
                        safe_name = make_safe(basename)
                        for f in os.listdir(SOURCE_TEXTURES):
                            if f.endswith('.png') and f[:-4] == basename:
                                shutil.copy(os.path.join(SOURCE_TEXTURES, f), os.path.join(TEXTURES_DIR, f"{safe_name}.png"))
                                found = True
                                break
                                
                    tex_dict[k] = f"kingdomcore:item/{safe_name}"
        elif isinstance(tex_dict, list):
             # embedded texture?
             pass
    
    with open(os.path.join(MODELS_DIR, f"{dest_name}.json"), 'w') as f:
        json.dump(data, f, indent=4)

for src_file, dest_name in models_to_process.items():
    process_model(os.path.join(SOURCE_MODELS_DIR, src_file), dest_name)

if crown_data:
    process_model(crown_src, "crown", crown_data)

# Move obj and mtl for trident if they exist
trident_dir = "/Users/markginzburg/Desktop/kingdome core custom items/Trident"
if os.path.exists(trident_dir):
    for f in os.listdir(trident_dir):
        if f.endswith('.obj') or f.endswith('.mtl'):
            shutil.copy(os.path.join(trident_dir, f), os.path.join(MODELS_DIR, f))
        elif f.endswith('.png'):
            shutil.copy(os.path.join(trident_dir, f), os.path.join(TEXTURES_DIR, "trident_material.png"))

# Create items.yml
items_yml = """info:
  namespace: kingdomcore

items:
  heart:
    display_name: '&cHeart'
    permission: kingdomcore.item.heart
    resource:
      material: NETHER_STAR
      generate: false
      model_path: item/heart
  medium_heart:
    display_name: '&cMedium Heart'
    permission: kingdomcore.item.heart
    resource:
      material: NETHER_STAR
      generate: false
      model_path: item/medium_heart
  small_heart:
    display_name: '&cSmall Heart'
    permission: kingdomcore.item.heart
    resource:
      material: NETHER_STAR
      generate: false
      model_path: item/small_heart

  crown:
    display_name: '&6Crown'
    permission: kingdomcore.item.crown
    resource:
      material: NETHERITE_HELMET
      generate: false
      model_path: item/crown

  revive_beacon:
    display_name: '&bRevival Beacon'
    permission: kingdomcore.item.revive_beacon
    resource:
      material: BEACON
      generate: true
      textures:
        - item/revival_beacon

  mace:
    display_name: '&6Mace'
    permission: kingdomcore.item.mace
    resource:
      material: MACE
      generate: false
      model_path: item/mace

  scythe:
    display_name: '&5Scythe'
    permission: kingdomcore.item.scythe
    resource:
      material: NETHERITE_SWORD
      generate: false
      model_path: item/scythe

  warden_cp:
    display_name: '&3Warden Chestplate'
    permission: kingdomcore.item.warden_cp
    resource:
      material: NETHERITE_CHESTPLATE
      generate: false
      textures:
        - item/warden_cp
    specific_properties:
      armor:
        slot: chest
        custom_armor: warden

  trident:
    display_name: '&bPoseidon Trident'
    permission: kingdomcore.item.trident
    resource:
      material: TRIDENT
      generate: false
      model_path: item/sea_dragons_trident (1).obj
"""
with open(os.path.join(CONFIGS_DIR, "items.yml"), 'w') as f:
    f.write(items_yml)

# Copy individual loose textures that might be needed
loose_textures = ["revival_beacon.png", "warden_cp.png"]
for t in loose_textures:
    src = os.path.join(SOURCE_TEXTURES, t)
    if os.path.exists(src):
        shutil.copy(src, os.path.join(TEXTURES_DIR, make_safe(t) + ".png"))
print("Done")
