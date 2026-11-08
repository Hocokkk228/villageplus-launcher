"""Author the actual Minecraft model, pixel textures and editable Blockbench source.

Requires Pillow and numpy. Geometry is a hollow, eight-lobed voxel shell. The face
is cut out of that shell; an inset emissive core is visible through the openings.
Only exposed faces are emitted, merged into rectangles to remove hidden geometry.
"""
from pathlib import Path
import base64
import json
import math
import uuid
import numpy as np
from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
RES = ROOT / "src/main/resources"
ASSETS = RES / "assets/halloween"
ART = ROOT / "art"
ART.mkdir(exist_ok=True)


def write_json(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")


PALETTES = {
    "rind": [(176, 65, 12), (191, 76, 14), (207, 88, 18), (218, 101, 23), (229, 114, 29)],
    "ridge": [(195, 76, 13), (213, 90, 17), (228, 105, 22), (237, 120, 29), (245, 134, 38)],
    "groove": [(126, 43, 10), (143, 50, 11), (161, 60, 12), (177, 70, 15), (188, 80, 18)],
    "flesh": [(157, 71, 15), (179, 90, 19), (204, 112, 24), (224, 139, 34), (239, 163, 48)],
    "stem": [(53, 55, 25), (65, 68, 28), (81, 84, 34), (99, 100, 42), (117, 116, 49)],
    "glow": [(241, 87, 8), (251, 112, 12), (255, 140, 20), (255, 165, 34), (255, 185, 55)],
}
for index, (name, palette) in enumerate(PALETTES.items()):
    rng = np.random.default_rng(417 + index)
    pixels = np.empty((32, 32, 3), dtype=np.uint8)
    columns = rng.integers(1, 4, size=32)
    for py in range(32):
        for px in range(32):
            tone = int(columns[px])
            if rng.random() < 0.18:
                tone += int(rng.choice([-1, 1]))
            if name == "glow":
                tone = min(4, 2 + int((py % 16) > 5) + int((py % 16) > 10))
            pixels[py, px] = palette[max(0, min(4, tone))]
    path = ASSETS / f"textures/block/pumpkin_{name}.png"
    path.parent.mkdir(parents=True, exist_ok=True)
    Image.fromarray(pixels).save(path)

# Each character is half a model unit. Rows below are authored from top to bottom.
face_rows = [
    "............................",
    "....##................##....",
    "....####............####....",
    "....######........######....",
    "....######........######....",
    ".....####..........####.....",
    "............................",
    "............####............",
    ".............##.............",
    "............................",
    "..##....................##..",
    "..####................####..",
    "...#####..######..#######...",
    "....####################....",
    ".....#####..##..#######.....",
    ".......###..##..#####.......",
    ".........##########.........",
    "............................",
]


def carved(x, y, z):
    col = math.floor((x - 1) * 2)
    row = math.floor((10.5 - y) * 2)
    return z < 8 and 0 <= row < len(face_rows) and 0 <= col < 28 and face_rows[row][col] == "#"


SIZE = 32
grid = np.zeros((SIZE, SIZE, SIZE), dtype=bool)
for ix, iy, iz in np.ndindex(grid.shape):
    x, y, z = (ix + .5) / 2, (iy + .5) / 2, (iz + .5) / 2
    theta = math.atan2(z - 8, x - 8)
    radius = 6.55 + .55 * math.cos(8 * theta)
    radial = math.hypot(x - 8, (z - 8) * 1.05)
    outer = (radial / radius) ** 2 + ((y - 6.25) / 6.25) ** 2 <= 1
    inner = (radial / (radius - 1.15)) ** 2 + ((y - 6.25) / 4.95) ** 2 < 1
    grid[ix, iy, iz] = outer and not inner and not carved(x, y, z)

elements = []
face_dirs = {"west": (0, -1), "east": (0, 1), "down": (1, -1), "up": (1, 1), "north": (2, -1), "south": (2, 1)}


def texture_for(pos, direction):
    x, y, z = pos
    axis, sign = face_dirs[direction]
    neighbor = list(pos)
    neighbor[axis] += sign * .26
    radial = math.hypot(x - 8, (z - 8) * 1.05)
    if carved(*neighbor):
        return "flesh"
    if (radial / 5.5) ** 2 + ((y - 6.25) / 5.0) ** 2 < 1.08:
        return "glow" if z >= 7 else "flesh"
    rib = math.cos(8 * math.atan2(z - 8, x - 8))
    return "groove" if rib < -.55 else "ridge" if rib > .35 else "rind"


def face_uv(direction, lo, hi):
    x, y, z = lo
    X, Y, Z = hi
    return {
        "north": [16-X, 16-Y, 16-x, 16-y], "south": [x, 16-Y, X, 16-y],
        "west": [z, 16-Y, Z, 16-y], "east": [16-Z, 16-Y, 16-z, 16-y],
        "up": [x, z, X, Z], "down": [x, 16-Z, X, 16-z],
    }[direction]


for direction, (axis, sign) in face_dirs.items():
    a, b = [dim for dim in range(3) if dim != axis]
    for layer in range(SIZE):
        mask = np.full((SIZE, SIZE), "", dtype=object)
        for u in range(SIZE):
            for v in range(SIZE):
                p = [0, 0, 0]
                p[axis], p[a], p[b] = layer, u, v
                q = p.copy()
                q[axis] += sign
                if not grid[tuple(p)] or (0 <= q[axis] < SIZE and grid[tuple(q)]):
                    continue
                center = [(c + .5) / 2 for c in p]
                center[axis] += sign * .25
                mask[u, v] = texture_for(center, direction)
        for u in range(SIZE):
            for v in range(SIZE):
                tex = mask[u, v]
                if not tex:
                    continue
                U, V = u + 1, v + 1
                while U < SIZE and mask[U, v] == tex:
                    U += 1
                while V < SIZE and all(mask[u:U, V] == tex):
                    V += 1
                mask[u:U, v:V] = ""
                lo, hi = [0., 0., 0.], [0., 0., 0.]
                lo[a], hi[a], lo[b], hi[b] = u / 2, U / 2, v / 2, V / 2
                plane = (layer + (sign > 0)) / 2
                lo[axis] = plane if sign < 0 else plane - .01
                hi[axis] = plane + .01 if sign < 0 else plane
                lo, hi = [round(n, 3) for n in lo], [round(n, 3) for n in hi]
                elements.append({"name": f"Carved rind {len(elements):03d}", "from": lo, "to": hi,
                                 "faces": {direction: {"uv": face_uv(direction, lo, hi), "texture": f"#{tex}"}}})
                if tex == "glow":
                    elements[-1].update(light_emission=15, shade=False)


def cube(name, lo, hi, tex, emission=0):
    element = {"name": name, "from": lo, "to": hi,
               "faces": {d: {"uv": face_uv(d, lo, hi), "texture": f"#{tex}"} for d in face_dirs}}
    if emission:
        element.update(light_emission=emission, shade=False)
    elements.append(element)


# Only the rear inner wall emits light; no glowing geometry protrudes from the shell.
cube("Stem collar", [6.03, 12.1, 6.03], [9.97, 12.6, 9.97], "stem")
cube("Stem root", [6.5, 12.6, 6.5], [9, 13.5, 9], "stem")
cube("Stem bend lower", [7, 13.5, 6.5], [9, 14.5, 8.5], "stem")
cube("Stem bend upper", [7.5, 14.5, 6.5], [9.5, 15.5, 8.5], "stem")
cube("Stem tip", [8, 15.5, 6.5], [10, 16, 8], "stem")

model = {
    "credit": "Halloween Furniture — original carved voxel pumpkin",
    "ambientocclusion": True,
    "textures": {**{k: f"halloween:block/pumpkin_{k}" for k in PALETTES}, "particle": "halloween:block/pumpkin_rind"},
    "display": {
        "head": {"rotation": [0, 0, 0], "translation": [0, 2, 0], "scale": [1.6, 1.6, 1.6]},
        "gui": {"rotation": [25, 155, 0], "translation": [0, 0, 0], "scale": [.8, .8, .8]},
        "ground": {"translation": [0, 3, 0], "scale": [.35, .35, .35]},
        "fixed": {"rotation": [0, 180, 0], "scale": [.65, .65, .65]},
        "thirdperson_righthand": {"rotation": [75, 45, 0], "translation": [0, 2.5, 0], "scale": [.375, .375, .375]},
        "firstperson_righthand": {"rotation": [0, 135, 0], "scale": [.5, .5, .5]},
        "firstperson_lefthand": {"rotation": [0, 225, 0], "scale": [.5, .5, .5]},
    },
    "elements": elements,
}
write_json(ASSETS / "models/block/pumpkin_lantern.json", model)
write_json(ASSETS / "items/pumpkin_lantern.json", {"model": {"type": "minecraft:model", "model": "halloween:block/pumpkin_lantern"}})
write_json(ASSETS / "blockstates/pumpkin_lantern.json", {"variants": {
    f"facing={d}": {"model": "halloween:block/pumpkin_lantern", "y": angle}
    for d, angle in [("north", 0), ("east", 90), ("south", 180), ("west", 270)]}})
write_json(ASSETS / "lang/en_us.json", {"block.halloween.pumpkin_lantern": "Carved Pumpkin Lantern"})
write_json(ASSETS / "lang/ru_ru.json", {"block.halloween.pumpkin_lantern": "Резной тыквенный фонарь"})
write_json(RES / "data/halloween/loot_table/blocks/pumpkin_lantern.json", {
    "type": "minecraft:block", "pools": [{"rolls": 1, "entries": [{"type": "minecraft:item", "name": "halloween:pumpkin_lantern"}],
    "conditions": [{"condition": "minecraft:survives_explosion"}]}]})
write_json(RES / "data/halloween/recipe/pumpkin_lantern.json", {
    "type": "minecraft:crafting_shapeless", "category": "building", "ingredients": ["minecraft:pumpkin", "minecraft:torch"],
    "result": {"id": "halloween:pumpkin_lantern", "count": 1}})
write_json(RES / "data/minecraft/tags/block/mineable/axe.json", {"replace": False, "values": ["halloween:pumpkin_lantern"]})

# Blockbench source embeds textures so it can be moved or opened on its own.
texture_names = list(PALETTES)
bb_elements = []
for index, elem in enumerate(elements):
    bb_elements.append({
        "name": elem["name"], "type": "cube", "uuid": str(uuid.uuid5(uuid.NAMESPACE_URL, f"halloween/pumpkin/{index}")),
        "from": elem["from"], "to": elem["to"], "origin": [8, 8, 8], "rotation": [0, 0, 0],
        "autouv": 0, "rescale": False, "locked": False, "visibility": True,
        "shade": elem.get("shade", True), "light_emission": elem.get("light_emission", 0),
        "faces": {d: {**elem["faces"][d], "texture": texture_names.index(elem["faces"][d]["texture"][1:])}
                  if d in elem["faces"] else {"uv": [0, 0, 0, 0], "texture": None} for d in face_dirs},
    })
for elem in bb_elements:
    for face in elem["faces"].values():
        face["uv"] = [n * 2 for n in face["uv"]]
write_json(ART / "pumpkin_lantern.bbmodel", {
    "meta": {"format_version": "4.10", "model_format": "java_block", "box_uv": False},
    "name": "Carved Pumpkin Lantern", "model_identifier": "pumpkin_lantern", "visible_box": [1, 1, 0],
    "resolution": {"width": 32, "height": 32}, "elements": bb_elements,
    "outliner": [e["uuid"] for e in bb_elements], "display": model["display"],
    "textures": [{"name": f"pumpkin_{name}.png", "id": name, "namespace": "halloween", "folder": "block",
                  "width": 32, "height": 32, "uv_width": 32, "uv_height": 32,
                  "uuid": str(uuid.uuid5(uuid.NAMESPACE_URL, f"halloween/texture/{name}")),
                  "source": "data:image/png;base64," + base64.b64encode((ASSETS / f"textures/block/pumpkin_{name}.png").read_bytes()).decode()}
                 for name in texture_names],
})
print(f"Generated {len(elements)} model elements; carved shell has {int(grid.sum())} half-unit voxels.")
