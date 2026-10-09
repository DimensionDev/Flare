package dev.dimension.flare.common

/*
UNICODE LICENSE V3

COPYRIGHT AND PERMISSION NOTICE

Copyright © 1991-2026 Unicode, Inc.

NOTICE TO USER: Carefully read the following legal agreement. BY
DOWNLOADING, INSTALLING, COPYING OR OTHERWISE USING DATA FILES, AND/OR
SOFTWARE, YOU UNEQUIVOCALLY ACCEPT, AND AGREE TO BE BOUND BY, ALL OF THE
TERMS AND CONDITIONS OF THIS AGREEMENT. IF YOU DO NOT AGREE, DO NOT
DOWNLOAD, INSTALL, COPY, DISTRIBUTE OR USE THE DATA FILES OR SOFTWARE.

Permission is hereby granted, free of charge, to any person obtaining a
copy of data files and any associated documentation (the "Data Files") or
software and any associated documentation (the "Software") to deal in the
Data Files or Software without restriction, including without limitation
the rights to use, copy, modify, merge, publish, distribute, and/or sell
copies of the Data Files or Software, and to permit persons to whom the
Data Files or Software are furnished to do so, provided that either (a)
this copyright and permission notice appear with all copies of the Data
Files or Software, or (b) this copyright and permission notice appear in
associated Documentation.

THE DATA FILES AND SOFTWARE ARE PROVIDED "AS IS", WITHOUT WARRANTY OF ANY
KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT OF
THIRD PARTY RIGHTS.

IN NO EVENT SHALL THE COPYRIGHT HOLDER OR HOLDERS INCLUDED IN THIS NOTICE
BE LIABLE FOR ANY CLAIM, OR ANY SPECIAL INDIRECT OR CONSEQUENTIAL DAMAGES,
OR ANY DAMAGES WHATSOEVER RESULTING FROM LOSS OF USE, DATA OR PROFITS,
WHETHER IN AN ACTION OF CONTRACT, NEGLIGENCE OR OTHER TORTIOUS ACTION,
ARISING OUT OF OR IN CONNECTION WITH THE USE OR PERFORMANCE OF THE DATA
FILES OR SOFTWARE.

Except as contained in this notice, the name of a copyright holder shall
not be used in advertising or otherwise to promote the sale, use or other
dealings in these Data Files or Software without prior written
authorization of the copyright holder.
*/

// Each entry packs a range start (upper bits), GCB (bits 0–3), InCB (bits 4–5), and Extended_Pictographic (bit 6).
// GCB: Other=0, CR=1, LF=2, Control=3, Extend=4, ZWJ=5, RI=6, Prepend=7, SpacingMark=8, L=9, V=10, T=11, LV=12, LVT=13.
// InCB: None=0, Consonant=1, Extend=2, Linker=3.
internal fun graphemeProperties(point: Int): Int {
    // Hangul LV/LVT alternate every 28 syllables; calculate them instead of storing their ranges.
    if (point in 0xAC00..0xD7A3) return if ((point - 0xAC00) % 28 == 0) 12 else 13
    var lower = 0
    var upper = graphemePropertyStarts.lastIndex
    while (lower <= upper) {
        val middle = (lower + upper) ushr 1
        if (graphemePropertyStarts[middle] ushr 7 <= point) {
            lower = middle + 1
        } else {
            upper = middle - 1
        }
    }
    return graphemePropertyStarts[upper] and 0x7F
}
