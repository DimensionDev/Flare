package dev.dimension.flare.data.datastore.model

internal object AiPromptDefaults {
    const val TRANSLATE_PROMPT: String =
        "You are a translation engine. Output only the translated template.\n" +
            "The target language is {target_language}.\n" +
            "The input is a plain-text translation template extracted from a social post.\n" +
            "Header lines like <<<B0>>>, <<<E0>>>, <<<I key C>>>, and <<<F content>>> are control lines.\n" +
            "Keep every control line exactly unchanged.\n" +
            "Inline markers like {{T0}} and {{L1}} are control markers.\n" +
            "Keep every control marker exactly unchanged and in the same order.\n" +
            "Translate every natural-language segment that appears after a {{Tn}} marker into natural {target_language}.\n" +
            "Copying the original source text after a {{Tn}} marker is wrong unless that segment " +
            "is already naturally written in {target_language}.\n" +
            "If you are unsure, still provide your best translation in {target_language} instead of leaving the source text unchanged.\n" +
            "Do not add any text after a {{Ln}} marker.\n" +
            "For item headers, use S only when the source text is already in {target_language}; otherwise keep C and translate.\n" +
            "Return ONLY the translated template without JSON, markdown code fences, comments, or explanations.\n" +
            "Example input:\n" +
            "<<<B0>>>\n" +
            "{{T0}}Hello {{L1}}{{T2}}from Tokyo\n" +
            "<<<E0>>>\n" +
            "Example output:\n" +
            "<<<B0>>>\n" +
            "{{T0}}你好 {{L1}}{{T2}}来自东京\n" +
            "<<<E0>>>\n" +
            "Translate the following template to {target_language}:\n" +
            "{source_text}"

    const val TLDR_PROMPT: String =
        "Summarize the following text in {target_language}\n" +
            "Respond in raw text, limit the response to 200 characters.\n" +
            "Text: \"{source_text}\""
}
