#!/usr/bin/env python3
"""Draws the mod's pixel-art textures. Standard library only.

    python3 tools/gen-textures.py

The PNGs are committed; run this again only after changing a drawing below.
"""
import math
import pathlib
import struct
import zlib

ROOT = pathlib.Path(__file__).resolve().parent.parent
ASSETS = ROOT / "src/main/resources/assets/kodland_camera/textures"


def write_png(path, rows):
    """rows: list of lists of (r, g, b, a)."""
    height, width = len(rows), len(rows[0])
    raw = b"".join(b"\x00" + bytes(c for px in row for c in px) for row in rows)

    def chunk(kind, data):
        return struct.pack(">I", len(data)) + kind + data + struct.pack(">I", zlib.crc32(kind + data) & 0xFFFFFFFF)

    png = b"\x89PNG\r\n\x1a\n"
    png += chunk(b"IHDR", struct.pack(">IIBBBBB", width, height, 8, 6, 0, 0, 0))
    png += chunk(b"IDAT", zlib.compress(raw, 9))
    png += chunk(b"IEND", b"")
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes(png)


def hex_rgba(value):
    value = value.lstrip("#")
    return tuple(int(value[i:i + 2], 16) for i in (0, 2, 4)) + (255,)


def from_map(art, palette):
    clear = (0, 0, 0, 0)
    assert all(len(line) == 16 for line in art), [len(line) for line in art]
    return [[hex_rgba(palette[ch]) if ch in palette else clear for ch in line] for line in art]


CAMERA = [
    "................",
    "................",
    "..kkk.....kkkk..",
    "..krk.....kwwk..",
    ".kkkkkkkkkkkkkk.",
    ".kssbbkkkkbbbbk.",
    ".kbbbkllllkbbbk.",
    ".kbbklcccclkbbk.",
    ".kbbklhccclkbbk.",
    ".kbbklcccclkbbk.",
    ".kbbklccdclkbbk.",
    ".kbbbkllllkbbbk.",
    ".kggggkkkkggggk.",
    ".kkkkkkkkkkkkkk.",
    "................",
    "................",
]
CAMERA_PALETTE = {
    "k": "#1d1f27",  # outline
    "b": "#454b5c",  # body
    "s": "#6b7387",  # body highlight
    "g": "#30343f",  # grip
    "l": "#9aa3b8",  # lens ring
    "c": "#3f8fd0",  # glass
    "d": "#2c6aa0",  # glass shadow
    "h": "#d8f0ff",  # glint
    "r": "#e2483f",  # shutter
    "w": "#f4f1e6",  # flash
}

TRASH = [
    "................",
    "................",
    "......kkkk......",
    "......k..k......",
    "..kkkkkkkkkkkk..",
    "..kRRRRRRRRRRk..",
    "..kkkkkkkkkkkk..",
    "...krrrrrrrrk...",
    "...krrdrrdrrk...",
    "...krrdrrdrrk...",
    "...krrdrrdrrk...",
    "...krrdrrdrrk...",
    "...krrrrrrrrk...",
    "...kkkkkkkkkk...",
    "................",
    "................",
]
TRASH_PALETTE = {
    "k": "#4a1916",
    "R": "#f07a72",  # lid, lighter
    "r": "#e0544b",
    "d": "#a8322b",
}


def check_mark():
    """A thick tick drawn by distance to a polyline, with a one-pixel outline."""
    points = [(3.0, 8.5), (6.5, 12.0), (12.5, 4.0)]
    fill_rgba, edge_rgba = hex_rgba("#5ccf63"), hex_rgba("#1c4a20")

    def dist(px, py, a, b):
        ax, ay = a
        bx, by = b
        dx, dy = bx - ax, by - ay
        t = max(0.0, min(1.0, ((px - ax) * dx + (py - ay) * dy) / (dx * dx + dy * dy)))
        return math.hypot(px - (ax + t * dx), py - (ay + t * dy))

    inside = [[min(dist(x + 0.5, y + 0.5, points[i], points[i + 1]) for i in range(len(points) - 1)) <= 1.25
               for x in range(16)] for y in range(16)]
    rows = []
    for y in range(16):
        row = []
        for x in range(16):
            if inside[y][x]:
                row.append(fill_rgba)
            elif any(0 <= y + j < 16 and 0 <= x + i < 16 and inside[y + j][x + i]
                     for j in (-1, 0, 1) for i in (-1, 0, 1)):
                row.append(edge_rgba)
            else:
                row.append((0, 0, 0, 0))
        rows.append(row)
    return rows


def main():
    write_png(ASSETS / "item/camera.png", from_map(CAMERA, CAMERA_PALETTE))
    write_png(ASSETS / "gui/sprites/polaroid/delete.png", from_map(TRASH, TRASH_PALETTE))
    write_png(ASSETS / "gui/sprites/polaroid/save.png", check_mark())
    print("textures written to", ASSETS)


if __name__ == "__main__":
    main()
