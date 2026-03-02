package dev.dimension.flare.data.datastore.model

internal object AiPromptDefaults {
    const val TRANSLATE_PROMPT: String =
        "You are a translation assistant. Your task is to translate text from one language to another.\n" +
            "Make sure to keep the meaning and context of the original content intact.\n" +
            "The input is an HTML fragment. Preserve HTML structure and tags in your output.\n" +
            "Do NOT translate any link content: keep <a> inner text, href values, and URL-like text unchanged.\n" +
            "Return ONLY translated HTML fragment without markdown code fences or explanations.\n" +
            "Translate the following HTML to {target_language}:\n" +
            "{source_html}"

    const val TLDR_PROMPT: String =
        "Summarize the following text in {target_language}\n" +
            "Respond in raw text, limit the response to 200 characters.\n" +
            "Text: \"{source_text}\""
}
