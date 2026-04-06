package dev.dimension.flare.readability

/**
 * All regular expressions used by the readability algorithm.
 * Ported from Mozilla's Readability.js.
 */
internal object RegExps {
    val unlikelyCandidates =
        Regex("""-ad-|ai2html|banner|breadcrumbs|combx|comment|community|cover-wrap|disqus|extra|footer|gdpr|header|legends|menu|related|remark|replies|rss|shoutbox|sidebar|skyscraper|social|sponsor|supplemental|ad-break|agegate|pagination|pager|popup|yom-remote""", RegexOption.IGNORE_CASE)

    val okMaybeItsACandidate =
        Regex("""and|article|body|column|content|main|mathjax|shadow""", RegexOption.IGNORE_CASE)

    val positive =
        Regex("""article|body|content|entry|hentry|h-entry|main|page|pagination|post|text|blog|story""", RegexOption.IGNORE_CASE)

    val negative =
        Regex("""-ad-|hidden|^hid$| hid$| hid |^hid |banner|combx|comment|com-|contact|footer|gdpr|masthead|media|meta|outbrain|promo|related|scroll|share|shoutbox|sidebar|skyscraper|sponsor|shopping|tags|widget""", RegexOption.IGNORE_CASE)

    val extraneous =
        Regex("""print|archive|comment|discuss|e[-]?mail|share|reply|all|login|sign|single|utility""", RegexOption.IGNORE_CASE)

    val byline =
        Regex("""byline|author|dateline|writtenby|p-author""", RegexOption.IGNORE_CASE)

    val normalize = Regex("""\s{2,}""")

    val videos =
        Regex("""//(www\.)?((dailymotion|youtube|youtube-nocookie|player\.vimeo|v\.qq|bilibili|live\.bilibili)\.com|(archive|upload\.wikimedia)\.org|player\.twitch\.tv)""", RegexOption.IGNORE_CASE)

    val shareElements =
        Regex("""(\b|_)(share|sharedaddy)(\b|_)""", RegexOption.IGNORE_CASE)

    val nextLink =
        Regex("""(next|weiter|continue|>([^|]|$)|»([^|]|$))""", RegexOption.IGNORE_CASE)

    val prevLink =
        Regex("""(prev|earl|old|new|<|«)""", RegexOption.IGNORE_CASE)

    val tokenize = Regex("""\W+""")

    val whitespace = Regex("""^\s*$""")

    val hasContent = Regex("""\S$""")

    val hashUrl = Regex("""^#.+""")

    val srcsetUrl = Regex("""(\S+)(\s+[\d.]+[xw])?(\s*(?:,|$))""")

    val b64DataUrl = Regex("""^data:\s*([^\s;,]+)\s*;\s*base64\s*,""", RegexOption.IGNORE_CASE)

    // Commas as used in Latin, Sindhi, Chinese and various other scripts.
    val commas = Regex("""\u002C|\u060C|\uFE50|\uFE10|\uFE11|\u2E41|\u2E34|\u2E32|\uFF0C""")

    // See: https://schema.org/Article
    val jsonLdArticleTypes =
        Regex("""^Article|AdvertiserContentArticle|NewsArticle|AnalysisNewsArticle|AskPublicNewsArticle|BackgroundNewsArticle|OpinionNewsArticle|ReportageNewsArticle|ReviewNewsArticle|Report|SatiricalArticle|ScholarlyArticle|MedicalScholarlyArticle|SocialMediaPosting|BlogPosting|LiveBlogPosting|DiscussionForumPosting|TechArticle|APIReference$""")

    // used to see if a node's content matches words commonly used for ad blocks or loading indicators
    val adWords =
        Regex("""^(ad(vertising|vertisement)?|pub(licité)?|werb(ung)?|广告|Реклама|Anuncio)$""", RegexOption.IGNORE_CASE)

    val loadingWords =
        Regex("""^((loading|正在加载|Загрузка|chargement|cargando)(…|\.\.\.)?)$""", RegexOption.IGNORE_CASE)
}
