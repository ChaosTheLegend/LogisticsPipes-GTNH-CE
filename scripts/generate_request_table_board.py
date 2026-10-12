"""Draw the complete request-table motherboard from its Java layout (requires Pillow).

Run from any directory: python scripts/generate_request_table_board.py
This recreates the original template, replacing the hand-edited lettering and socket art.
The current board contains all labels and connections; Minecraft renders chips and socket state.
"""
from pathlib import Path
import re
from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "src/main/java/logisticspipes/crafting/requesttable/gui/upgrade/RequestTableBoardLayout.java"
VALUES = {key: int(value) for key, value in re.findall(r"static final int (\w+) = (\d+);", SOURCE.read_text())}
PALETTE = {
    "board": "#22392d", "recess": "#1a2e25", "edge": "#304d39", "trace": "#415b40",
    "metal": "#676e4b", "ink": "#b1b79b", "dark": "#18241d", "body": "#50634b",
    "led": "#98aa6b", "fluid": "#5a8177", "amber": "#8b875a",
}
image = Image.new("RGB", (VALUES["WIDTH"], VALUES["HEIGHT"]), PALETTE["board"])
draw = ImageDraw.Draw(image)

def rect(x, y, w, h, color):
    draw.rectangle((x, y, x + w - 1, y + h - 1), fill=PALETTE[color])

def outline(x, y, w, h, color):
    draw.rectangle((x, y, x + w - 1, y + h - 1), outline=PALETTE[color])

def line(points, color="trace"):
    draw.line(points, fill=PALETTE[color], width=1)

def pad(x, y):
    rect(x - 1, y - 1, 3, 3, "metal"); rect(x, y, 1, 1, "board")

def resistor(x, y, vertical=False):
    if vertical:
        rect(x, y, 3, 9, "metal"); rect(x, y + 2, 3, 5, "dark"); rect(x, y + 4, 3, 1, "body")
    else:
        rect(x, y, 9, 3, "metal"); rect(x + 2, y, 5, 3, "dark"); rect(x + 4, y, 1, 3, "body")

def capacitor(x, y):
    rect(x, y, 5, 8, "dark"); rect(x + 1, y + 1, 3, 6, "body")
    rect(x + 1, y, 3, 1, "metal"); rect(x + 1, y + 7, 3, 1, "metal")

def chip(x, y, w=16, h=12):
    rect(x, y, w, h, "dark"); rect(x + 1, y + 1, w - 2, 1, "body")
    for pin in range(2, w - 1, 3):
        rect(x + pin, y - 2, 1, 2, "metal"); rect(x + pin, y + h, 1, 2, "metal")
    rect(x + 3, y + 3, 1, 1, "ink")

def socket(cx, cy, w, h, family, level):
    x, y = cx - w // 2, cy - h // 2
    rim = "fluid" if family.startswith("fluid") else "body"
    outline(x - 4, y - 4, w + 8, h + 8, rim)
    rect(x, y, w, h, "recess")
    inset = 4 if level == 0 else 3
    for px in range(x + inset, x + w - inset, 4):
        rect(px, y - 3, 2, 3, "metal"); rect(px, y + h, 2, 3, "metal")
    for py in range(y + inset, y + h - inset, 4):
        rect(x - 3, py, 3, 2, "metal"); rect(x + w, py, 3, 2, "metal")
    if level > 0:
        for px in [x - 4, x + w + 1]:
            for py in [y - 4, y + h + 1]: rect(px, py, 3, 3, "body")
    if level == 2:
        rect(x - 5, y + h // 2 - 4, 3, 8, "metal")
        rect(x + w + 2, y + h // 2 - 4, 3, 8, "metal")
    if family.endswith("slots"):
        for px in range(x + 3, x + w - 3, 5): rect(px, y - 6, 2, 1, rim)
    else:
        rect(x + w // 2 - 4, y - 6, 8, 1, rim)

def bus(points):
    line(points, "metal")
    for offset in [-4, 4]:
        line([(x + offset, y + offset) for x, y in points])

width, height = image.size
outline(4, 4, width - 8, height - 8, "dark")
outline(5, 5, width - 10, height - 10, "edge")
for x, y in [(14, 14), (width - 15, 14), (14, height - 15), (width - 15, height - 15)]:
    outline(x - 4, y - 4, 9, 9, "metal"); rect(x - 2, y - 2, 5, 5, "dark")

cpu_x, cpu_y = VALUES["CPU_X"], VALUES["CPU_Y"]
monitor_x, fluid_x = VALUES["MONITOR_X"], VALUES["FLUID_X"]
bus([(monitor_x, cpu_y), (fluid_x, cpu_y)])
for fluid in [False, True]:
    for slots in [True, False]:
        y = VALUES["TOP_BUS_Y" if slots else "BOTTOM_BUS_Y"]
        start = VALUES["FLUID_START_X" if fluid else "ITEM_START_X"]
        direction = 1 if fluid else -1
        end = start + direction * 9 * VALUES["TIER_SPACING"]
        origin = fluid_x if fluid else cpu_x
        rail = y + (-34 if slots else 34)
        bus([(origin, cpu_y), (origin, rail), (end, rail)])
        for tier in range(1, 11):
            size = 28 if tier <= 3 else 34 if tier <= 7 else 40
            cx = start + direction * (tier - 1) * VALUES["TIER_SPACING"]
            family = ("fluid_" if fluid else "item_") + ("slots" if slots else "capacity")
            line([(cx, rail), (cx, y + (-size // 2 - 3 if slots else size // 2 + 3))], "metal")
            socket(cx, y, size, size, family, 0 if tier <= 3 else 1 if tier <= 7 else 2)

socket(cpu_x, cpu_y, 52, 52, "cpu", 2)
outline(cpu_x - 32, cpu_y - 32, 64, 64, "metal")
for x in [cpu_x - 34, cpu_x + 28]:
    for y in [cpu_y - 34, cpu_y + 28]:
        rect(x, y, 6, 6, "body"); rect(x + 2, y + 2, 2, 2, "dark")
socket(monitor_x, cpu_y, 48, 36, "monitor", 1)
rect(monitor_x - 19, cpu_y - 24, 38, 2, "body")
socket(fluid_x, cpu_y, 36, 48, "fluid_controller", 1)
for y in [cpu_y - 30, cpu_y + 28]:
    rect(fluid_x - 10, y, 20, 2, "fluid")

# Auxiliary memory and the quiet chest-shaped silkscreen on the item side.
line([(196, 254), (208, 254), (216, 246), (490, 246), (510, 256), (606, 256)])
outline(162, 246, 22, 16, "body"); line([(162, 252), (183, 252)], "body")
rect(172, 251, 3, 5, "metal")
for x in [232, 262, 302, 344]:
    chip(x, 228, 20, 12); chip(x + 8, 266, 16, 10)
    capacitor(x - 8, 242); resistor(x + 22, 247, True)
    pad(x + 8, 250)
for x, y in [(460, 220), (438, 285), (594, 219), (575, 281)]:
    chip(x, y, 14, 10); resistor(x - 14, y + 5); capacitor(x + 19, y)

# Small monitor pulse, and jumpers feeding the display module.
outline(500, 239, 32, 21, "body")
line([(504, 253), (510, 253), (510, 245), (517, 245), (517, 250), (527, 250)], "led")
for x in [553, 557, 561]: rect(x, 250, 2, 2, "metal")
rect(552, 253, 11, 3, "dark"); rect(553, 253, 6, 1, "body")

# Tank and pump doodle: two-dimensional lines, kept to the same restrained palette.
outline(1204, 232, 20, 35, "fluid")
line([(1204, 240), (1223, 240)], "fluid"); line([(1204, 259), (1223, 259)], "fluid")
line([(1160, 252), (1160, 266), (1204, 266)], "fluid")
outline(1155, 244, 11, 11, "fluid"); rect(1159, 248, 3, 3, "body")
line([(1124, 250), (1155, 250)], "fluid")
line([(1237, 250), (1237, 272), (1298, 272), (1306, 264)], "trace")
for x in [938, 976, 1020, 1082, 1273, 1314]:
    chip(x, 231 if x % 3 else 275, 14, 10)
    capacitor(x + 20, 245); resistor(x - 11, 260)
    pad(x + 8, 266)
line([(890, 272), (918, 272), (928, 282), (1026, 282)])

# A few supporting components above and below the long buses, placed as groups rather than tiles.
groups = [(192, 88), (284, 80), (414, 85), (572, 72), (948, 76), (1118, 89), (1314, 74),
          (211, 434), (342, 423), (498, 441), (616, 427), (904, 437), (1070, 424), (1238, 439)]
for i, (x, y) in enumerate(groups):
    chip(x, y, 12 if i % 2 else 18, 10)
    capacitor(x - 10, y + 3); resistor(x + 23, y + 4)
    line([(x + 8, y + 14), (x + 8, y + 22), (x + 31, y + 22), (x + 39, y + 30)])
    pad(x + 39, y + 30)
    if i % 4 == 0: rect(x + 36, y + 3, 2, 2, "led")

for x, y in [(680, 98), (752, 403), (848, 219), (848, 287)]:
    for offset in [0, 5, 10]: capacitor(x + offset, y)
for x, y in [(670, 322), (835, 105), (780, 405)]:
    rect(x, y, 8, 5, "dark"); rect(x + 1, y + 1, 6, 1, "body")
    for offset in [1, 4, 7]: rect(x + offset, y - 2, 1, 2, "metal")

destination = ROOT / "src/main/resources/assets/logisticspipes/textures/gui/requesttable/requesttable_board.png"
destination.parent.mkdir(parents=True, exist_ok=True)
image.save(destination, optimize=True)
print(f"Saved {destination}: {width}x{height}, {len(image.getcolors(width * height))} colors.")
