import XCTest
import UIKit
import UniformTypeIdentifiers
import FlareAppleCore
@testable import Flare

@MainActor
final class ComposeImageInputTests: XCTestCase {
    private let gif = Data(base64Encoded: "R0lGODlhAQABAIAAAAAAAP///yH5BAEAAAAALAAAAAABAAEAAAIBRAA7")!

    func testPickerRefreshPreservesImportedImageIdentityAndAltText() {
        let model = MediaViewModel()
        model.setInitialImage(data: gif, fileName: "existing.gif")
        let original = model.items[0]
        original.altText = "Existing description"
        model.update()
        XCTAssertTrue(model.items.first === original)
        XCTAssertEqual(model.items.first?.altText, "Existing description")
        model.remove(item: original)
        XCTAssertTrue(model.items.isEmpty)
    }

    func testNativeImageProviderPreservesGifAndWaitsForSingleDispatch() async throws {
        let model = MediaViewModel()
        model.maxSize = 1
        model.importImage(provider())
        model.importImage(provider())
        XCTAssertTrue(model.items.isEmpty)
        XCTAssertTrue(model.hasPendingImports)
        let dispatched = expectation(description: "dispatch after import")
        var dispatchCount = 0
        let dispatch: (@escaping (Bool) -> Void) -> Void = { callback in
            dispatchCount += 1
            XCTAssertEqual(model.items.count, 1)
            callback(true)
        }
        model.dispatchWhenReady(dispatch) { _ in dispatched.fulfill() }
        model.dispatchWhenReady(dispatch) { _ in XCTFail("duplicate submission") }
        await fulfillment(of: [dispatched], timeout: 5)
        XCTAssertEqual(dispatchCount, 1)
        let file = try XCTUnwrap(model.items.first?.file)
        XCTAssertEqual(try Data(contentsOf: file.url), gif)
        XCTAssertEqual(model.items.first?.mimeType, "image/gif")
        XCTAssertTrue(model.items.first?.fileName.hasSuffix(".gif") == true)
    }

    func testCapacityChangesAndPollKeepExistingAttachments() async {
        let model = MediaViewModel()
        model.setInitialImage(data: gif, fileName: "existing.gif")
        model.importImage(provider())
        model.maxSize = 1
        await waitForImports(model)
        XCTAssertEqual(model.items.count, 1)
        model.maxSize = 4
        model.importImage(provider())
        model.imagesAllowed = { false }
        await waitForImports(model)
        XCTAssertEqual(model.items.count, 1)
        model.importImage(provider())
        XCTAssertFalse(model.hasPendingImports)
    }

    func testCorruptImageBlocksSendAndRetryRecovers() async {
        let model = MediaViewModel()
        var errors = 0
        model.onError = { _ in errors += 1 }
        model.importImage(provider(data: Data("not an image".utf8)))
        let blocked = expectation(description: "blocked")
        model.dispatchWhenReady({ _ in XCTFail("sent without failed attachment") }) { success in
            XCTAssertFalse(success)
            blocked.fulfill()
        }
        await fulfillment(of: [blocked], timeout: 5)
        XCTAssertEqual(errors, 1)
        model.importImage(provider())
        let retried = expectation(description: "retry")
        model.dispatchWhenReady({ $0(true) }) { success in
            XCTAssertTrue(success)
            retried.fulfill()
        }
        await fulfillment(of: [retried], timeout: 5)
    }

    func testCancelRejectsLateResultsAndPendingSubmission() async {
        let model = MediaViewModel()
        model.importImage(provider())
        model.dispatchWhenReady({ _ in XCTFail("dispatched after close") }) { _ in }
        model.cancelImports()
        try? await Task.sleep(for: .milliseconds(100))
        XCTAssertFalse(model.hasPendingImports)
        XCTAssertTrue(model.items.isEmpty)
    }

    func testTextFirstMixedDropCanRetryAFailedImageImport() async {
        let model = MediaViewModel()
        model.importImage(provider(data: Data("not an image".utf8)))
        await waitForImports(model)
        var text = ""
        model.importDrop([NSItemProvider(object: "independent text" as NSString), provider()]) { text += $0 }
        let retried = expectation(description: "mixed retry")
        model.dispatchWhenReady({ callback in
            XCTAssertEqual(text, "independent text")
            XCTAssertEqual(model.items.count, 1)
            callback(true)
        }) { success in
            XCTAssertTrue(success)
            retried.fulfill()
        }
        await fulfillment(of: [retried], timeout: 5)
    }

    func testPasteConsumesImageAlternativeAndLeavesIndependentTextToUIKit() {
        let input = ComposeImageInput()
        let textView = UITextView()
        input.install(on: textView)
        XCTAssertTrue(textView.pasteDelegate === input)
        XCTAssertTrue(textView.pasteConfiguration!.acceptableTypeIdentifiers.contains(UTType.image.identifier))
        var imports = 0
        input.receiveImage = { _ in imports += 1 }
        let imageProvider = provider()
        imageProvider.registerObject("https://example.com/image.gif" as NSString, visibility: .all)
        let image = PasteItem(imageProvider)
        let text = PasteItem(NSItemProvider(object: "independent text" as NSString))
        input.textPasteConfigurationSupporting(textView, transform: image)
        input.textPasteConfigurationSupporting(textView, transform: text)
        XCTAssertEqual(imports, 1)
        XCTAssertEqual(image.result, "none")
        XCTAssertEqual(text.result, "default")
    }

    func testAttachmentTextIsIncludedBeforeDispatch() async {
        let model = MediaViewModel()
        var text = ""
        model.importImage(provider())
        model.importText(NSItemProvider(object: "independent text" as NSString)) { text += $0 }
        let done = expectation(description: "both imported")
        model.dispatchWhenReady({ callback in
            XCTAssertEqual(text, "independent text")
            XCTAssertEqual(model.items.count, 1)
            callback(true)
        }) { _ in done.fulfill() }
        await fulfillment(of: [done], timeout: 5)
    }

    func testUIKitPasteKeepsBodyWhenOnlyAnImageIsPasted() async {
        let model = MediaViewModel()
        let input = ComposeImageInput()
        let textView = UITextView()
        textView.text = "keep body"
        textView.selectedRange = NSRange(location: 0, length: 9)
        input.receiveImage = { model.importImage($0) }
        input.install(on: textView)
        UIPasteboard.general.items = [[UTType.gif.identifier: gif]]
        textView.paste(nil)
        await waitForImports(model)
        XCTAssertEqual(model.items.count, 1)
        XCTAssertEqual(textView.text, "keep body")
    }

    func testIndependentTextAndImagesKeepProviderOrderWhenLoadsFinishOutOfOrder() async throws {
        let model = MediaViewModel()
        var second = gif
        second[13] = 255
        model.importImage(provider(delay: 0.05))
        model.importImage(provider(data: second))
        var text = ""
        for (value, delay) in [("first", 0.05), ("second", 0.0)] {
            let provider = NSItemProvider()
            provider.registerDataRepresentation(forTypeIdentifier: UTType.utf8PlainText.identifier, visibility: .all) { completion in
                DispatchQueue.global().asyncAfter(deadline: .now() + delay) { completion(Data(value.utf8), nil) }
                return nil
            }
            model.importText(provider) { text += $0 }
        }
        let done = expectation(description: "ordered imports")
        model.dispatchWhenReady({ $0(true) }) { _ in done.fulfill() }
        await fulfillment(of: [done], timeout: 5)
        XCTAssertEqual(text, "firstsecond")
        XCTAssertEqual(try model.items.map { try Data(contentsOf: XCTUnwrap($0.file).url) }, [gif, second])
    }

    func testImportedFileStaysAliveUntilDraftCopyFinishes() async throws {
        let model = MediaViewModel()
        model.importImage(provider())
        let dispatched = expectation(description: "dispatched")
        var finish: ((Bool) -> Void)?
        model.dispatchWhenReady({ finish = $0; dispatched.fulfill() }) { _ in }
        await fulfillment(of: [dispatched], timeout: 5)
        let url = try XCTUnwrap(model.items.first?.file?.url)
        model.remove(item: model.items[0])
        model.cancelImports()
        XCTAssertTrue(FileManager.default.fileExists(atPath: url.path))
        finish?(true)
        finish = nil
        XCTAssertFalse(FileManager.default.fileExists(atPath: url.path))
    }

    func testFileAndImageObjectProvidersUseTheSameImportPath() async throws {
        let url = FileManager.default.temporaryDirectory.appendingPathComponent("\(UUID()).gif")
        try gif.write(to: url)
        defer { try? FileManager.default.removeItem(at: url) }
        let fileProvider = try XCTUnwrap(NSItemProvider(contentsOf: url))
        let imageProvider = NSItemProvider(object: try XCTUnwrap(UIImage(data: gif)))
        XCTAssertTrue(ComposeImageInput.isImage(fileProvider))
        XCTAssertTrue(ComposeImageInput.isImage(imageProvider))
        let model = MediaViewModel()
        model.importImage(fileProvider)
        model.importImage(imageProvider)
        await waitForImports(model)
        XCTAssertEqual(model.items.count, 2)
        XCTAssertEqual(try Data(contentsOf: XCTUnwrap(model.items.first?.file).url), gif)
    }

    func testOldDraftCompletionDoesNotDismissOrUnlockNewSubmission() async {
        let model = MediaViewModel()
        var first: ((Bool) -> Void)?
        var second: ((Bool) -> Void)?
        var dismissals = 0
        let firstReady = expectation(description: "first dispatch")
        model.dispatchWhenReady({ first = $0; firstReady.fulfill() }) { _ in dismissals += 1 }
        await fulfillment(of: [firstReady], timeout: 5)
        model.cancelImports()
        let secondReady = expectation(description: "second dispatch")
        model.dispatchWhenReady({ second = $0; secondReady.fulfill() }) { _ in dismissals += 1 }
        await fulfillment(of: [secondReady], timeout: 5)
        first?(true)
        model.dispatchWhenReady({ _ in XCTFail("duplicate submission") }) { _ in }
        await Task.yield()
        XCTAssertEqual(dismissals, 0)
        second?(true)
        XCTAssertEqual(dismissals, 1)
    }

    private func provider(data: Data? = nil, delay: TimeInterval = 0) -> NSItemProvider {
        let bytes = data ?? gif
        let provider = NSItemProvider()
        provider.registerDataRepresentation(forTypeIdentifier: UTType.gif.identifier, visibility: .all) { completion in
            if delay == 0 { completion(bytes, nil) }
            else { DispatchQueue.global().asyncAfter(deadline: .now() + delay) { completion(bytes, nil) } }
            return nil
        }
        return provider
    }

    private func waitForImports(_ model: MediaViewModel) async {
        for _ in 0..<500 {
            if !model.hasPendingImports { return }
            try? await Task.sleep(for: .milliseconds(10))
        }
        XCTFail("import did not finish")
    }
}

@MainActor
private final class PasteItem: NSObject, UITextPasteItem {
    let itemProvider: NSItemProvider
    let localObject: Any? = nil
    let defaultAttributes: [NSAttributedString.Key: Any] = [:]
    var result: String?
    init(_ provider: NSItemProvider) { itemProvider = provider }
    func setNoResult() { result = "none" }
    func setDefaultResult() { result = "default" }
    func setResult(string: String) { result = string }
    func setResult(attributedString: NSAttributedString) { result = attributedString.string }
    func setResult(attachment: NSTextAttachment) { result = "attachment" }
}
