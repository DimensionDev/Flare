import XCTest

final class StatusTextContentTests: XCTestCase {
    func testTranslationKeepsItsCacheIdentityWhenBilingualDisplayChanges() {
        let translationOnly = visible(showOriginal: false)
        let bilingual = visible(showOriginal: true)

        XCTAssertEqual(translationOnly.map(\.text), ["译文"])
        XCTAssertEqual(bilingual.map(\.text), ["Original", "译文"])
        XCTAssertEqual(translationOnly[0].cacheKeyOffset, bilingual[1].cacheKeyOffset)
        XCTAssertNotEqual(translationOnly[0].cacheKeyOffset, bilingual[0].cacheKeyOffset)
    }

    func testCachedRowKeepsBothTextsAcrossToggleAndUnchangedRefresh() {
        // Model RichTextUIView's same-key fast path across repeated configurations
        // of the same post, whose renderHash does not change with this setting.
        var renderedTextByKey: [Int: String] = [:]
        func render(showOriginal: Bool) -> [String] {
            visible(showOriginal: showOriginal).map { content in
                if let rendered = renderedTextByKey[content.cacheKeyOffset] {
                    return rendered
                }
                renderedTextByKey[content.cacheKeyOffset] = content.text
                return content.text
            }
        }

        XCTAssertEqual(render(showOriginal: false), ["译文"])
        XCTAssertEqual(render(showOriginal: true), ["Original", "译文"])
        XCTAssertEqual(render(showOriginal: true), ["Original", "译文"])
        XCTAssertEqual(render(showOriginal: false), ["译文"])
        XCTAssertEqual(render(showOriginal: true), ["Original", "译文"])
    }

    func testHiddenTranslationKeepsOriginalIdentity() {
        for showOriginal in [false, true] {
            let hidden = visible(showOriginal: showOriginal, translationDisplayed: false)
            XCTAssertEqual(hidden.map(\.text), ["Original"])
            XCTAssertEqual(hidden[0].cacheKeyOffset, visible(showOriginal: true)[0].cacheKeyOffset)
        }
    }

    func testMissingTranslationFallsBackToOriginalInEveryDisplayMode() {
        for showOriginal in [false, true] {
            for translationDisplayed in [false, true] {
                let content = visible(
                    showOriginal: showOriginal,
                    translationDisplayed: translationDisplayed,
                    translation: nil
                )
                XCTAssertEqual(content.map(\.text), ["Original"])
                XCTAssertEqual(content[0].cacheKeyOffset, visible(showOriginal: true)[0].cacheKeyOffset)
            }
        }
    }

    func testFilteringAnEmptyOriginalDoesNotChangeTranslationIdentity() {
        let bilingual = StatusTextContent.visible(
            original: "",
            translation: "译文",
            translationDisplayed: true,
            showOriginalWithTranslation: true
        ).filter { !$0.text.isEmpty }

        XCTAssertEqual(bilingual.map(\.text), ["译文"])
        XCTAssertEqual(bilingual[0].cacheKeyOffset, visible(showOriginal: false)[0].cacheKeyOffset)
    }

    private func visible(
        showOriginal: Bool,
        translationDisplayed: Bool = true,
        translation: String? = "译文"
    ) -> [StatusTextContent<String>] {
        StatusTextContent.visible(
            original: "Original",
            translation: translation,
            translationDisplayed: translationDisplayed,
            showOriginalWithTranslation: showOriginal
        )
    }
}
