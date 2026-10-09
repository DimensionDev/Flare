"""Regenerate the portable X emoji regex from the official twemoji-parser package.

Usage: python3 .github/scripts/generate_x_emoji_regex.py <twemoji-parser-11.0.2.tgz>
Source: https://registry.npmjs.org/twemoji-parser/-/twemoji-parser-11.0.2.tgz
"""

import json
from pathlib import Path
import re
import sys
import tarfile


def codepoint(high, low):
    return chr(0x10000 + (int(high, 16) - 0xD800) * 0x400 + int(low, 16) - 0xDC00)


def surrogate_class(match):
    high, body = match.groups()
    token = r"\\u([dD][c-fC-F][0-9a-fA-F]{2})(?:-\\u([dD][c-fC-F][0-9a-fA-F]{2}))?"
    assert re.fullmatch("(?:" + token + ")+", body)
    points = []
    for start, end in re.findall(token, body):
        # Native/Wasm Regex cannot match supplementary character ranges.
        points.extend(codepoint(high, f"{low:04x}") for low in range(int(start, 16), int(end or start, 16) + 1))
    return "[" + "".join(points) + "]"


if len(sys.argv) != 2:
    raise SystemExit(__doc__)

with tarfile.open(sys.argv[1]) as package:
    metadata = json.loads(package.extractfile("package/package.json").read())
    assert metadata["name"] == "twemoji-parser" and metadata["version"] == "11.0.2"
    source = package.extractfile("package/dist/lib/regex.js").read().decode()
    license_text = package.extractfile("package/LICENSE.md").read().decode().strip()

pattern = re.search(r"exports.default = /(.*)/g;", source)[1]
# JS matches UTF-16 units; JVM/Native/Wasm regexes need supplementary code points.
pattern = re.sub(r"\\u([dD][89abAB][0-9a-fA-F]{2})\[([^]]+)\]", surrogate_class, pattern)
pattern = re.sub(
    r"\\u([dD][89abAB][0-9a-fA-F]{2})\\u([dD][c-fC-F][0-9a-fA-F]{2})",
    lambda pair: codepoint(*pair.groups()), pattern,
)
assert not re.search(r"\\u[dD][89a-fA-F][0-9a-fA-F]{2}", pattern)
assert "[👨👩]" in pattern and "[🏻🏼🏽🏾🏿]" in pattern

tokens = re.findall(r"\\u[0-9a-fA-F]{4}|.", pattern)
chunks = [""]
for token in tokens:
    if len(chunks[-1]) + len(token) > 100:
        chunks.append("")
    chunks[-1] += token
assert "".join(chunks) == pattern

output = Path(__file__).resolve().parents[2] / (
    "social/xqt/src/commonMain/kotlin/dev/dimension/flare/data/datasource/xqt/XEmojiRegex.kt"
)
output.write_text(
    "package dev.dimension.flare.data.datasource.xqt\n\n/*\n" + license_text + "\n*/\n\n"
    "// Generated from twemoji-parser 11.0.2 by .github/scripts/generate_x_emoji_regex.py.\n"
    "// Surrogate pairs/classes become code points; ranges are expanded for Native/Wasm Regex.\n"
    "private val xEmojiRegex =\n    Regex(\n"
    + " +\n".join('        """' + chunk + '"""' for chunk in chunks)
    + "\n    )\n\n"
    "internal fun xEmojiLengthAt(text: String, index: Int): Int =\n"
    "    xEmojiRegex.matchAt(text, index)?.value?.length ?: 0\n"
)
