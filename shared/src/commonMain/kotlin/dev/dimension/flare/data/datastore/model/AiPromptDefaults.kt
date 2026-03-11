package dev.dimension.flare.data.datastore.model

internal object AiPromptDefaults {
    const val TRANSLATE_PROMPT: String =
        "You are a translation assistant. Your task is to translate text from one language to another.\n" +
            "Make sure to keep the meaning and context of the original content intact.\n" +
            "The input is plain text extracted from a social post.\n" +
            "Keep mentions, hashtags, URLs, and code-like text unchanged when possible.\n" +
            "Return ONLY translated plain text without markdown code fences or explanations.\n" +
            "Translate the following text to {target_language}:\n" +
            "{source_text}"

    const val TLDR_PROMPT: String =
        "Summarize the following text in {target_language}\n" +
            "Respond in raw text, limit the response to 200 characters.\n" +
            "Text: \"{source_text}\""
}
