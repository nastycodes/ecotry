"""Generates the EcoTry icon: a command block with a gold coin in front (no Pillow needed).

Usage: python tools/gen_icon.py <project root>
Draws 16x16 pixel art and scales it x8 to 128x128 (assets/ecotry/icon.png).
"""
import math
import os
import random
import struct
import sys
import zlib

SIZE = 16
SCALE = 8

OUTLINE = (40, 28, 22, 255)
ORANGE_LIGHT = (236, 152, 74, 255)
ORANGE = (204, 118, 52, 255)
ORANGE_DARK = (150, 80, 32, 255)
SCREEN = (34, 36, 42, 255)
PROMPT = (226, 230, 232, 255)
GOLD_LIGHT = (255, 232, 128, 255)
GOLD = (243, 192, 52, 255)
GOLD_DARK = (190, 130, 22, 255)
GOLD_RIM = (124, 76, 6, 255)


def write_png(path, w, h, px):
    raw = bytearray()
    for y in range(h):
        raw.append(0)
        for x in range(w):
            raw.extend(px[y][x])

    def chunk(tag, data):
        c = struct.pack(">I", len(data)) + tag + data
        return c + struct.pack(">I", zlib.crc32(tag + data) & 0xFFFFFFFF)

    png = b"\x89PNG\r\n\x1a\n"
    png += chunk(b"IHDR", struct.pack(">IIBBBBB", w, h, 8, 6, 0, 0, 0))
    png += chunk(b"IDAT", zlib.compress(bytes(raw), 9))
    png += chunk(b"IEND", b"")
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "wb") as f:
        f.write(png)


def shade(c, d):
    return (max(0, min(255, c[0] + d)), max(0, min(255, c[1] + d)), max(0, min(255, c[2] + d)), c[3])


def icon():
    rnd = random.Random(7)
    px = [[(0, 0, 0, 0)] * SIZE for _ in range(SIZE)]
    # Command block body with a light top-left and a dark bottom-right bevel.
    for y in range(SIZE):
        for x in range(SIZE):
            if x in (0, 15) or y in (0, 15):
                c = OUTLINE
            elif x == 1 or y == 1:
                c = ORANGE_LIGHT
            elif x == 14 or y == 14:
                c = ORANGE_DARK
            else:
                c = shade(ORANGE, rnd.randint(-8, 8))
            px[y][x] = c
    # Screen with a "> _" prompt.
    for y in range(3, 11):
        for x in range(3, 13):
            px[y][x] = OUTLINE if x in (3, 12) or y in (3, 10) else shade(SCREEN, rnd.randint(-3, 3))
    for x, y in ((5, 5), (6, 6), (5, 7), (7, 7), (8, 7)):
        px[y][x] = PROMPT
    # Gold coin in front of the bottom-right corner.
    cx, cy, r = 11.0, 11.0, 3.7
    for y in range(SIZE):
        for x in range(SIZE):
            d = math.hypot(x - cx, y - cy)
            if d <= r:
                if d > r - 1.0:
                    c = GOLD_RIM
                elif (x - cx) + (y - cy) < -1.5:
                    c = GOLD_LIGHT
                elif (x - cx) + (y - cy) > 1.5:
                    c = GOLD_DARK
                else:
                    c = GOLD
                px[y][x] = c
            elif d <= r + 0.8 and px[y][x][3] > 0:
                px[y][x] = OUTLINE
    return px


def main(root):
    small = icon()
    big = [[small[y // SCALE][x // SCALE] for x in range(SIZE * SCALE)] for y in range(SIZE * SCALE)]
    path = os.path.join(root, "src/main/resources/assets/ecotry/icon.png")
    write_png(path, SIZE * SCALE, SIZE * SCALE, big)
    print("wrote", path)


if __name__ == "__main__":
    main(sys.argv[1] if len(sys.argv) > 1 else ".")
