package dev.dimension.flare.data.datasource.xqt

/*
Copyright (c) 2018 Twitter, Inc.

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in
all copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
THE SOFTWARE.
*/

// Generated from twemoji-parser 11.0.2 by .github/scripts/generate_x_emoji_regex.py.
// Surrogate pairs/classes become code points; ranges are expanded for Native/Wasm Regex.
private val xEmojiRegex =
    Regex(
        """(?:[👨👩])(?:[🏻🏼🏽🏾🏿])?\u200d(?:\u2695\ufe0f|\u2696\ufe0f|\u2708\ufe0f|[🌾🍳🎓🎤🎨🏫🏭]|[💻💼🔧🔬🚀🚒]|[🦰🦱🦲🦳])|(?:[🏋""" +
        """🏌]|[🕴🕵]|\u26f9)((?:[🏻🏼🏽🏾🏿]|\ufe0f)\u200d[\u2640\u2642]\ufe0f)|(?:[🏃🏄🏊]|[👮👱👳👷💁💂💆💇🙅🙆🙇🙋🙍🙎🚣🚴🚵🚶]|[🤦🤵🤷🤸🤹🤽🤾""" +
        """🦸🦹🧖🧗🧘🧙🧚🧛🧜🧝])(?:[🏻🏼🏽🏾🏿])?\u200d[\u2640\u2642]\ufe0f|(?:👨\u200d\u2764\ufe0f\u200d💋\u200d👨|👨\u200d👨""" +
        """\u200d👦\u200d👦|👨\u200d👨\u200d👧\u200d[👦👧]|👨\u200d👩\u200d👦\u200d👦|👨\u200d👩\u200d👧\u200d[👦👧]|👩\u200d""" +
        """\u2764\ufe0f\u200d💋\u200d[👨👩]|👩\u200d👩\u200d👦\u200d👦|👩\u200d👩\u200d👧\u200d[👦👧]|👨\u200d\u2764\ufe0f""" +
        """\u200d👨|👨\u200d👦\u200d👦|👨\u200d👧\u200d[👦👧]|👨\u200d👨\u200d[👦👧]|👨\u200d👩\u200d[👦👧]|👩\u200d\u2764\ufe0f""" +
        """\u200d[👨👩]|👩\u200d👦\u200d👦|👩\u200d👧\u200d[👦👧]|👩\u200d👩\u200d[👦👧]|🏳\ufe0f\u200d🌈|🏴\u200d\u2620\ufe0f|""" +
        """👁\u200d🗨|👨\u200d[👦👧]|👩\u200d[👦👧]|👯\u200d\u2640\ufe0f|👯\u200d\u2642\ufe0f|🤼\u200d\u2640\ufe0f|🤼\u200d""" +
        """\u2642\ufe0f|🧞\u200d\u2640\ufe0f|🧞\u200d\u2642\ufe0f|🧟\u200d\u2640\ufe0f|🧟\u200d\u2642\ufe0f)|[#*0-9""" +
        """]\ufe0f?\u20e3|(?:[©®\u2122\u265f]\ufe0f)|(?:[🀄🅰🅱🅾🅿🈂🈚🈯🈷🌡🌤🌥🌦🌧🌨🌩🌪🌫🌬🌶🍽🎖🎗🎙🎚🎛🎞🎟🏍🏎🏔🏕🏖🏗🏘🏙🏚🏛🏜🏝🏞🏟🏳🏵🏷]|[🐿👁📽🕉🕊🕯""" +
        """🕰🕳🕶🕷🕸🕹🖇🖊🖋🖌🖍🖥🖨🖱🖲🖼🗂🗃🗄🗑🗒🗓🗜🗝🗞🗡🗣🗨🗯🗳🗺🛋🛍🛎🛏🛠🛡🛢🛣🛤🛥🛩🛰🛳]|[\u203c\u2049\u2139\u2194-\u2199\u21a9\u21aa\u231a""" +
        """\u231b\u2328\u23cf\u23ed-\u23ef\u23f1\u23f2\u23f8-\u23fa\u24c2\u25aa\u25ab\u25b6\u25c0\u25fb-\u25fe""" +
        """\u2600-\u2604\u260e\u2611\u2614\u2615\u2618\u2620\u2622\u2623\u2626\u262a\u262e\u262f\u2638-\u263a""" +
        """\u2640\u2642\u2648-\u2653\u2660\u2663\u2665\u2666\u2668\u267b\u267f\u2692-\u2697\u2699\u269b\u269c""" +
        """\u26a0\u26a1\u26aa\u26ab\u26b0\u26b1\u26bd\u26be\u26c4\u26c5\u26c8\u26cf\u26d1\u26d3\u26d4\u26e9""" +
        """\u26ea\u26f0-\u26f5\u26f8\u26fa\u26fd\u2702\u2708\u2709\u270f\u2712\u2714\u2716\u271d\u2721\u2733""" +
        """\u2734\u2744\u2747\u2757\u2763\u2764\u27a1\u2934\u2935\u2b05-\u2b07\u2b1b\u2b1c\u2b50\u2b55\u3030""" +
        """\u303d\u3297\u3299])(?:\ufe0f|(?!\ufe0e))|(?:(?:[🏋🏌]|[🕴🕵🖐]|[\u261d\u26f7\u26f9\u270c\u270d])(?:""" +
        """\ufe0f|(?!\ufe0e))|(?:[🎅🏂🏃🏄🏇🏊]|[👂👃👆👇👈👉👊👋👌👍👎👏👐👦👧👨👩👮👰👱👲👳👴👵👶👷👸👼💁💂💃💅💆💇💪🕺🖕🖖🙅🙆🙇🙋🙌🙍🙎🙏🚣🚴🚵🚶🛀🛌]|[🤘🤙🤚🤛🤜🤞🤟🤦🤰🤱🤲🤳🤴""" +
        """🤵🤶🤷🤸🤹🤽🤾🦵🦶🦸🦹🧑🧒🧓🧔🧕🧖🧗🧘🧙🧚🧛🧜🧝]|[\u270a\u270b]))(?:[🏻🏼🏽🏾🏿])?|(?:🏴󠁧󠁢󠁥󠁮󠁧󠁿|🏴󠁧󠁢󠁳󠁣󠁴󠁿|🏴󠁧󠁢󠁷󠁬󠁳󠁿|🇦[🇨🇩🇪🇫🇬🇮🇱🇲🇴🇶🇷🇸🇹🇺🇼🇽""" +
        """🇿]|🇧[🇦🇧🇩🇪🇫🇬🇭🇮🇯🇱🇲🇳🇴🇶🇷🇸🇹🇻🇼🇾🇿]|🇨[🇦🇨🇩🇫🇬🇭🇮🇰🇱🇲🇳🇴🇵🇷🇺🇻🇼🇽🇾🇿]|🇩[🇪🇬🇯🇰🇲🇴🇿]|🇪[🇦🇨🇪🇬🇭🇷🇸🇹🇺]|🇫[🇮🇯🇰🇲🇴🇷]|🇬[🇦🇧🇩🇪🇫🇬🇭🇮🇱🇲🇳🇵""" +
        """🇶🇷🇸🇹🇺🇼🇾]|🇭[🇰🇲🇳🇷🇹🇺]|🇮[🇨🇩🇪🇱🇲🇳🇴🇶🇷🇸🇹]|🇯[🇪🇲🇴🇵]|🇰[🇪🇬🇭🇮🇲🇳🇵🇷🇼🇾🇿]|🇱[🇦🇧🇨🇮🇰🇷🇸🇹🇺🇻🇾]|🇲[🇦🇨🇩🇪🇫🇬🇭🇰🇱🇲🇳🇴🇵🇶🇷🇸🇹🇺🇻🇼🇽🇾🇿]|🇳""" +
        """[🇦🇨🇪🇫🇬🇮🇱🇴🇵🇷🇺🇿]|🇴🇲|🇵[🇦🇪🇫🇬🇭🇰🇱🇲🇳🇷🇸🇹🇼🇾]|🇶🇦|🇷[🇪🇴🇸🇺🇼]|🇸[🇦🇧🇨🇩🇪🇬🇭🇮🇯🇰🇱🇲🇳🇴🇷🇸🇹🇻🇽🇾🇿]|🇹[🇦🇨🇩🇫🇬🇭🇯🇰🇱🇲🇳🇴🇷🇹🇻🇼🇿]|🇺[🇦🇬🇲🇳""" +
        """🇸🇾🇿]|🇻[🇦🇨🇪🇬🇮🇳🇺]|🇼[🇫🇸]|🇽🇰|🇾[🇪🇹]|🇿[🇦🇲🇼]|[🃏🆎🆑🆒🆓🆔🆕🆖🆗🆘🆙🆚🇦🇧🇨🇩🇪🇫🇬🇭🇮🇯🇰🇱🇲🇳🇴🇵🇶🇷🇸🇹🇺🇻🇼🇽🇾🇿🈁🈲🈳🈴🈵🈶🈸🈹🈺🉐🉑🌀🌁🌂🌃🌄🌅🌆🌇🌈🌉🌊🌋""" +
        """🌌🌍🌎🌏🌐🌑🌒🌓🌔🌕🌖🌗🌘🌙🌚🌛🌜🌝🌞🌟🌠🌭🌮🌯🌰🌱🌲🌳🌴🌵🌷🌸🌹🌺🌻🌼🌽🌾🌿🍀🍁🍂🍃🍄🍅🍆🍇🍈🍉🍊🍋🍌🍍🍎🍏🍐🍑🍒🍓🍔🍕🍖🍗🍘🍙🍚🍛🍜🍝🍞🍟🍠🍡🍢🍣🍤🍥🍦🍧🍨🍩🍪🍫🍬🍭🍮🍯🍰🍱🍲🍳🍴🍵🍶🍷🍸🍹🍺🍻🍼""" +
        """🍾🍿🎀🎁🎂🎃🎄🎆🎇🎈🎉🎊🎋🎌🎍🎎🎏🎐🎑🎒🎓🎠🎡🎢🎣🎤🎥🎦🎧🎨🎩🎪🎫🎬🎭🎮🎯🎰🎱🎲🎳🎴🎵🎶🎷🎸🎹🎺🎻🎼🎽🎾🎿🏀🏁🏅🏆🏈🏉🏏🏐🏑🏒🏓🏠🏡🏢🏣🏤🏥🏦🏧🏨🏩🏪🏫🏬🏭🏮🏯🏰🏴🏸🏹🏺🏻🏼🏽🏾🏿]|[🐀🐁🐂🐃🐄🐅🐆""" +
        """🐇🐈🐉🐊🐋🐌🐍🐎🐏🐐🐑🐒🐓🐔🐕🐖🐗🐘🐙🐚🐛🐜🐝🐞🐟🐠🐡🐢🐣🐤🐥🐦🐧🐨🐩🐪🐫🐬🐭🐮🐯🐰🐱🐲🐳🐴🐵🐶🐷🐸🐹🐺🐻🐼🐽🐾👀👄👅👑👒👓👔👕👖👗👘👙👚👛👜👝👞👟👠👡👢👣👤👥👪👫👬👭👯👹👺👻👽👾👿💀💄💈💉💊💋💌💍💎""" +
        """💏💐💑💒💓💔💕💖💗💘💙💚💛💜💝💞💟💠💡💢💣💤💥💦💧💨💩💫💬💭💮💯💰💱💲💳💴💵💶💷💸💹💺💻💼💽💾💿📀📁📂📃📄📅📆📇📈📉📊📋📌📍📎📏📐📑📒📓📔📕📖📗📘📙📚📛📜📝📞📟📠📡📢📣📤📥📦📧📨📩📪📫📬📭📮📯📰📱📲📳""" +
        """📴📵📶📷📸📹📺📻📼📿🔀🔁🔂🔃🔄🔅🔆🔇🔈🔉🔊🔋🔌🔍🔎🔏🔐🔑🔒🔓🔔🔕🔖🔗🔘🔙🔚🔛🔜🔝🔞🔟🔠🔡🔢🔣🔤🔥🔦🔧🔨🔩🔪🔫🔬🔭🔮🔯🔰🔱🔲🔳🔴🔵🔶🔷🔸🔹🔺🔻🔼🔽🕋🕌🕍🕎🕐🕑🕒🕓🕔🕕🕖🕗🕘🕙🕚🕛🕜🕝🕞🕟🕠🕡🕢🕣🕤🕥🕦🕧""" +
        """🖤🗻🗼🗽🗾🗿😀😁😂😃😄😅😆😇😈😉😊😋😌😍😎😏😐😑😒😓😔😕😖😗😘😙😚😛😜😝😞😟😠😡😢😣😤😥😦😧😨😩😪😫😬😭😮😯😰😱😲😳😴😵😶😷😸😹😺😻😼😽😾😿🙀🙁🙂🙃🙄🙈🙉🙊🚀🚁🚂🚃🚄🚅🚆🚇🚈🚉🚊🚋🚌🚍🚎🚏🚐🚑🚒🚓🚔🚕""" +
        """🚖🚗🚘🚙🚚🚛🚜🚝🚞🚟🚠🚡🚢🚤🚥🚦🚧🚨🚩🚪🚫🚬🚭🚮🚯🚰🚱🚲🚳🚷🚸🚹🚺🚻🚼🚽🚾🚿🛁🛂🛃🛄🛅🛐🛑🛒🛫🛬🛴🛵🛶🛷🛸🛹]|[🤐🤑🤒🤓🤔🤕🤖🤗🤝🤠🤡🤢🤣🤤🤥🤧🤨🤩🤪🤫🤬🤭🤮🤯🤺🤼🥀🥁🥂🥃🥄🥅🥇🥈🥉🥊🥋🥌🥍🥎🥏🥐🥑""" +
        """🥒🥓🥔🥕🥖🥗🥘🥙🥚🥛🥜🥝🥞🥟🥠🥡🥢🥣🥤🥥🥦🥧🥨🥩🥪🥫🥬🥭🥮🥯🥰🥳🥴🥵🥶🥺🥼🥽🥾🥿🦀🦁🦂🦃🦄🦅🦆🦇🦈🦉🦊🦋🦌🦍🦎🦏🦐🦑🦒🦓🦔🦕🦖🦗🦘🦙🦚🦛🦜🦝🦞🦟🦠🦡🦢🦴🦷🧀🧁🧂🧐🧞🧟🧠🧡🧢🧣🧤🧥🧦🧧🧨🧩🧪🧫🧬🧭🧮🧯🧰""" +
        """🧱🧲🧳🧴🧵🧶🧷🧸🧹🧺🧻🧼🧽🧾🧿]|[\u23e9-\u23ec\u23f0\u23f3\u267e\u26ce\u2705\u2728\u274c\u274e\u2753-\u2755\u2795-""" +
        """\u2797\u27b0\u27bf\ue50a])|\ufe0f"""
    )

internal fun xEmojiLengthAt(text: String, index: Int): Int =
    xEmojiRegex.matchAt(text, index)?.value?.length ?: 0
