package dev.dimension.flare.data.datastore.model

internal object AiPromptDefaults {
    const val TRANSLATE_PROMPT: String =
        "You are a translation assistant. Your task is to translate text from one language to another.\n" +
            "Make sure to keep the meaning and context of the original content intact.\n" +
            "The input is JSON extracted from a social post.\n" +
            "Preserve the full JSON structure, block ids, token ids, and token kinds exactly as-is.\n" +
            "Only translate token text where kind is \"Translatable\".\n" +
            "Keep token text where kind is \"Locked\" unchanged.\n" +
            "Return ONLY JSON without markdown code fences or explanations.\n" +
            "Translate the following JSON to {target_language}:\n" +
            "{source_json}"

    const val TLDR_PROMPT: String =
        "Summarize the following text in {target_language}\n" +
            "Respond in raw text, limit the response to 200 characters.\n" +
            "Text: \"{source_text}\""
}
