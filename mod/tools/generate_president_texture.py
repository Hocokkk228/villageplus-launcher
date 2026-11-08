"""Draw the profession overlay directly on the villager's 64x64 UV grid."""
from pathlib import Path
import struct
import zlib

SIZE = 64
pixels = bytearray(SIZE * SIZE * 4)


def pixel(x, y, color):
    index = (y * SIZE + x) * 4
    pixels[index:index + 4] = bytes((*color, 255))


def jacket(x, y, width, height, front=False):
    for v in range(height):
        for u in range(width):
            edge = min(u, width - u - 1)
            shade = (u * 5 + v * 3) % 9
            color = (24 + shade, 31 + shade, 43 + shade)
            if edge == 0:
                color = (22, 28, 38)
            if front:
                if v < 7 and abs(2 * u - (width - 1)) <= max(2, 7 - v):
                    color = (218 + shade, 220 + shade, 219 + shade)
                if v <= 8 and u in (width // 2 - 1, width // 2) and (v > 0 or u == width // 2):
                    color = (125 + shade, 26 + shade, 42 + shade)
                if 1 <= v <= 5 and edge == (v + 1) // 2:
                    color = (63, 73, 89)
                if v in (10, 14) and u == width // 2:
                    color = (111, 104, 87)
                if v == 6 and u == width - 2:
                    color = (172, 177, 182)
            pixel(x + u, y + v, color)


def wrapped(x, y, width, height, depth, formal=True):
    jacket(x + depth, y, width, depth)
    jacket(x + depth + width, y, width, depth)
    left = x
    for index, face_width in enumerate((depth, width, depth, width)):
        jacket(left, y + depth, face_width, height, front=formal and index == 1)
        left += face_width


wrapped(16, 20, 8, 12, 6)  # torso
wrapped(0, 38, 8, 20, 6)   # overcoat body
wrapped(0, 22, 4, 12, 4, formal=False)  # mirrored legs share this region
# Folded-arm group and split-arm animation use separate UV areas.
for x, y, width, height in ((44, 22, 16, 12), (40, 38, 24, 8)):
    jacket(x, y, width, height)
for y in (29, 30):
    for x in (46, 47, 60, 61):
        pixel(x, y, (216, 216, 208))


def chunk(kind, data):
    return struct.pack(">I", len(data)) + kind + data + struct.pack(">I", zlib.crc32(kind + data))


png = b"\x89PNG\r\n\x1a\n"
png += chunk(b"IHDR", struct.pack(">IIBBBBB", SIZE, SIZE, 8, 6, 0, 0, 0))
rows = b"".join(b"\0" + pixels[i:i + SIZE * 4] for i in range(0, len(pixels), SIZE * 4))
png += chunk(b"IDAT", zlib.compress(rows)) + chunk(b"IEND", b"")
root = Path(__file__).resolve().parents[1] / "src/main/resources/assets/halloween/textures/entity"
for entity in ("villager", "zombie_villager"):
    texture = root / entity / "profession/president.png"
    texture.write_bytes(png)
    texture.with_suffix(".png.mcmeta").write_text('{"villager":{"hat":"none"}}\n')
