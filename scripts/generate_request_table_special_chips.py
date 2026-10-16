"""Create the three native GUI chip designs and small animation strips (requires Pillow).

Run from the repository root: python scripts/generate_request_table_special_chips.py
Only special-chip textures are written; the edited motherboard and storage chips are preserved.
"""
from pathlib import Path
import json
import re

from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[1]
DESTINATION = ROOT / "src/main/resources/assets/logisticspipes/textures/gui/requesttable/chips/special"
LAYOUT = (ROOT / "src/main/java/logisticspipes/crafting/requesttable/gui/upgrade/RequestTableBoardLayout.java").read_text()
VALUES = {key: int(value) for key, value in re.findall(r"static final int (\w+) = (\d+);", LAYOUT)}
PALETTE = {
    "shadow": "#121c19", "edge": "#304238", "face": "#26372e", "bevel": "#50634b",
    "metal": "#676e4b", "ink": "#b1b79b", "die": "#1a2e25", "fluid": "#5a8177",
    "light": "#98b8a4",
}
FONT = {
    "C": ("111", "100", "100", "100", "111"), "D": ("110", "101", "101", "101", "110"),
    "E": ("111", "100", "110", "100", "111"), "F": ("111", "100", "110", "100", "100"),
    "L": ("100", "100", "100", "100", "111"), "M": ("101", "111", "111", "101", "101"),
    "N": ("101", "111", "111", "111", "101"), "O": ("111", "101", "101", "101", "111"),
    "P": ("110", "101", "110", "100", "100"), "R": ("110", "101", "110", "101", "101"),
    "S": ("111", "100", "111", "001", "111"), "T": ("111", "010", "010", "010", "010"),
    "W": ("101", "101", "111", "111", "101"), "A": ("010", "101", "111", "101", "101"),
}


def rect(draw, x, y, width, height, color):
    draw.rectangle((x, y, x + width - 1, y + height - 1), fill=color)


def text(draw, value, center, y, color, scale=1):
    x = (center * 2 - (len(value) * 4 - 1) * scale) // 2
    for character in value:
        for row, pixels in enumerate(FONT[character]):
            for column, pixel in enumerate(pixels):
                if pixel == "1":
                    rect(draw, x + column * scale, y + row * scale, scale, scale, color)
        x += 4 * scale


def package(width, height):
    image = Image.new("RGBA", (width, height), (0, 0, 0, 0))
    draw = ImageDraw.Draw(image)
    # A one-pixel contact margin makes the package fit the painted socket opening exactly.
    for pin in range(4, width - 3, 4):
        rect(draw, pin, 0, 2, 2, PALETTE["metal"])
        rect(draw, pin, 0, 1, 1, PALETTE["ink"])
        rect(draw, pin, height - 2, 2, 2, PALETTE["metal"])
        rect(draw, pin, height - 1, 1, 1, PALETTE["ink"])
    for pin in range(4, height - 3, 4):
        rect(draw, 0, pin, 2, 2, PALETTE["metal"])
        rect(draw, 0, pin, 1, 1, PALETTE["ink"])
        rect(draw, width - 2, pin, 2, 2, PALETTE["metal"])
        rect(draw, width - 1, pin, 1, 1, PALETTE["ink"])
    rect(draw, 1, 1, width - 2, height - 2, PALETTE["shadow"])
    rect(draw, 2, 2, width - 4, height - 4, PALETTE["bevel"])
    rect(draw, 3, 3, width - 6, height - 6, PALETTE["face"])
    rect(draw, 2, height - 3, width - 4, 1, PALETTE["edge"])
    rect(draw, width - 3, 2, 1, height - 4, PALETTE["edge"])
    for x, y in ((1, 1), (width - 2, 1), (1, height - 2), (width - 2, height - 2)):
        rect(draw, x, y, 1, 1, (0, 0, 0, 0))
    rect(draw, 4, 4, 2, 2, PALETTE["metal"])
    return image


def central():
    size = VALUES["CPU_CHIP_SIZE"]
    image = package(size, size)
    draw = ImageDraw.Draw(image)
    draw.rectangle((10, 10, size - 11, size - 11), outline=PALETTE["shadow"])
    draw.rectangle((11, 11, size - 12, size - 12), outline=PALETTE["bevel"])
    rect(draw, 12, 12, size - 24, size - 24, PALETTE["die"])
    for pin in (17, 26, 35):
        draw.line(((5, pin), (8, pin), (8, pin + 2), (10, pin + 2)), fill=PALETTE["metal"])
        draw.line(((size - 6, pin), (size - 9, pin), (size - 9, pin + 2), (size - 11, pin + 2)), fill=PALETTE["metal"])
        draw.line(((pin, 5), (pin, 8), (pin + 2, 8), (pin + 2, 10)), fill=PALETTE["metal"])
        draw.line(((pin, size - 6), (pin, size - 9), (pin + 2, size - 9), (pin + 2, size - 11)), fill=PALETTE["metal"])
    text(draw, "LP", size // 2, 18, PALETTE["ink"], 2)
    text(draw, "CORE", size // 2, 33, PALETTE["metal"])
    return image


def monitor():
    width, height = VALUES["MONITOR_CHIP_WIDTH"], VALUES["MONITOR_CHIP_HEIGHT"]
    image = package(width, height)
    draw = ImageDraw.Draw(image)
    text(draw, "MON", width // 2, 5, PALETTE["ink"])
    rect(draw, 8, 12, 34, 17, PALETTE["shadow"])
    draw.rectangle((9, 13, 40, 27), outline=PALETTE["bevel"])
    rect(draw, 10, 14, 30, 13, PALETTE["die"])
    draw.line(((13, 22), (20, 22), (23, 17), (26, 25), (29, 22), (36, 22)), fill=PALETTE["bevel"])
    for x in (8, 12, 16):
        rect(draw, x, 32, 2, 1, PALETTE["metal"])
    text(draw, "STAT", 32, 30, PALETTE["metal"])
    frames = []
    for x, y in ((13, 22), (17, 22), (20, 22), (23, 17), (26, 25), (29, 22), (32, 22), (36, 22)):
        layer = Image.new("RGBA", image.size, (0, 0, 0, 0))
        rect(ImageDraw.Draw(layer), x, y, 1, 1, PALETTE["light"])
        frames.append(layer)
    return image, frames


def fluid():
    width, height = VALUES["FLUID_CHIP_WIDTH"], VALUES["FLUID_CHIP_HEIGHT"]
    image = package(width, height)
    draw = ImageDraw.Draw(image)
    text(draw, "FLD", width // 2, 5, PALETTE["ink"])
    rect(draw, 11, 13, 16, 24, PALETTE["shadow"])
    draw.rectangle((12, 14, 25, 35), outline=PALETTE["bevel"])
    rect(draw, 13, 15, 12, 20, PALETTE["die"])
    rect(draw, 14, 24, 10, 10, PALETTE["fluid"])
    rect(draw, 14, 24, 10, 1, PALETTE["light"])
    rect(draw, 15, 11, 8, 2, PALETTE["metal"])
    for y in range(16, 35, 3):
        rect(draw, 28, y, 2, 1, PALETTE["metal"])
    rect(draw, 6, 40, 2, 2, PALETTE["metal"])
    text(draw, "FLOW", width // 2, 40, PALETTE["metal"])
    frames = []
    for y in (32, 30, 28, 26, 24, 22):
        layer = Image.new("RGBA", image.size, (0, 0, 0, 0))
        rect(ImageDraw.Draw(layer), 18, y, 2, 2, PALETTE["light"])
        frames.append(layer)
    return image, frames


def save(name, image):
    image.save(DESTINATION / f"{name}.png", optimize=True)
    (DESTINATION / f"{name}.png.mcmeta").write_text(json.dumps({"texture": {"blur": False, "clamp": True}}, indent=2) + "\n")


def save_strip(name, frames):
    width, height = frames[0].size
    strip = Image.new("RGBA", (width, height * len(frames)), (0, 0, 0, 0))
    for index, frame in enumerate(frames):
        strip.alpha_composite(frame, (0, height * index))
    save(name, strip)


def main():
    DESTINATION.mkdir(parents=True, exist_ok=True)
    save("central", central())
    monitoring, monitor_frames = monitor()
    fluids, fluid_frames = fluid()
    save("monitoring", monitoring)
    save("fluid", fluids)
    save_strip("monitoring_activity", monitor_frames)
    save_strip("fluid_activity", fluid_frames)
    print("Saved 54x54 central, 50x38 monitoring and 38x50 fluid chips; animations use 8 and 6 frames.")


if __name__ == "__main__":
    main()
