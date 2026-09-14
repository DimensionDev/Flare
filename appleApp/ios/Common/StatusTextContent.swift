struct StatusTextContent<Text> {
    let text: Text
    // Stable text identity, independent of its position in the visible list.
    let cacheKeyOffset: Int

    static func visible(
        original: Text,
        translation: Text?,
        translationDisplayed: Bool,
        showOriginalWithTranslation: Bool
    ) -> [Self] {
        let originalContent = Self(text: original, cacheKeyOffset: 0)
        guard translationDisplayed, let translation else {
            return [originalContent]
        }
        let translatedContent = Self(text: translation, cacheKeyOffset: 1)
        return showOriginalWithTranslation ? [originalContent, translatedContent] : [translatedContent]
    }
}
