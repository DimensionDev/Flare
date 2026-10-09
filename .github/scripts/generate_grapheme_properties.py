"""Regenerate Unicode 16.0 grapheme data from an unpacked Unicode UCD directory.

Usage: python3 .github/scripts/generate_grapheme_properties.py <ucd-directory>
Source: https://www.unicode.org/Public/16.0.0/ucd/UCD.zip
"""

from pathlib import Path
import sys

if len(sys.argv) != 2:
    raise SystemExit(__doc__)


def entries(path):
    for line in path.read_text().splitlines():
        fields = [field.strip() for field in line.split("#")[0].split(";")]
        if len(fields) < 2:
            continue
        points = fields[0].split("..")
        yield int(points[0], 16), int(points[-1], 16), fields[1:]


ucd = Path(sys.argv[1])
assert "DerivedCoreProperties-16.0.0.txt" in (ucd / "DerivedCoreProperties.txt").read_text()
assert "Emoji Version 16.0" in (ucd / "emoji/emoji-data.txt").read_text()
categories = {
    "Other": 0, "CR": 1, "LF": 2, "Control": 3, "Extend": 4, "ZWJ": 5,
    "Regional_Indicator": 6, "Prepend": 7, "SpacingMark": 8,
    "L": 9, "V": 10, "T": 11, "LV": 12, "LVT": 13,
}
properties = bytearray(0x110000)
grapheme_data = ucd / "auxiliary/GraphemeBreakProperty.txt"
assert "GraphemeBreakProperty-16.0.0.txt" in grapheme_data.read_text()
for start, end, fields in entries(grapheme_data):
    category = categories[fields[0]]
    if category not in (12, 13):  # Hangul syllables are classified arithmetically.
        properties[start:end + 1] = bytes([category]) * (end - start + 1)
for start, end, fields in entries(ucd / "DerivedCoreProperties.txt"):
    if fields[0] == "InCB":
        flag = {"Consonant": 0x10, "Extend": 0x20, "Linker": 0x30}[fields[1]]
        for point in range(start, end + 1):
            properties[point] |= flag
for start, end, fields in entries(ucd / "emoji/emoji-data.txt"):
    if fields[0] == "Extended_Pictographic":
        for point in range(start, end + 1):
            properties[point] |= 0x40

boundaries = []
previous = -1
for point, flags in enumerate(properties):
    if flags != previous:
        boundaries.append((point << 7) | flags)
        previous = flags
assert boundaries == sorted(boundaries)
assert properties[0x1FAF1] == 0x40  # New emoji bases stay independent of system ICU.
assert properties[0x1F3FB] & 0x0F == categories["Extend"]

output = Path(__file__).resolve().parents[2] / (
    "shared/src/commonMain/kotlin/dev/dimension/flare/common/GraphemeProperties.kt"
)
header = output.read_text().split("private val graphemePropertyStarts =")[0]
rows = [
    "        " + ", ".join(f"0x{value:08X}" for value in boundaries[index:index + 10]) + ","
    for index in range(0, len(boundaries), 10)
]
output.write_text(header + "private val graphemePropertyStarts =\n    intArrayOf(\n" + "\n".join(rows) + "\n    )\n")
