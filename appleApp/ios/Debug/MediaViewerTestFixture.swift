#if DEBUG
import AVFoundation
import FlareAppleCore
import FlareAppleUI
import Kingfisher
import KotlinSharedUI
import LazyPager
import SwiftUI

/// Offline fixtures exercise the real viewer and translation settings.
struct MediaViewerTestFixture: View {
    @State private var presented = false
    @State private var dismissed = false
    @State private var medias: [any UiMedia] = []
    private let video = ProcessInfo.processInfo.arguments.contains("--video")
    private let indicator = ProcessInfo.processInfo.arguments.contains("--indicator")
    private let withoutPost = ProcessInfo.processInfo.arguments.contains("--without-post")
    private let hidePostActions = ProcessInfo.processInfo.arguments.contains("--hide-post-actions")
    private let translationSettings = ProcessInfo.processInfo.arguments.contains("--translation-settings")

    var body: some View {
        VStack {
            if !medias.isEmpty {
                Button("Open media") { presented = true }
                    .accessibilityIdentifier("media-fixture-open")
            }
            if dismissed { Text("Media dismissed") }
        }
        .task {
            await configureTranslationFixture()
            var items = [await makeMedia()]
            if indicator { items.append(await makeMedia(index: 1)) }
            medias = items
        }
        .fullScreenCover(isPresented: $presented, onDismiss: { dismissed = true }) {
            NavigationStack {
                if translationSettings {
                    TranslationConfigScreen()
                } else if !medias.isEmpty {
                    MediaViewerScreen(
                        medias: medias, initialIndex: 0, preview: nil,
                        post: withoutPost ? nil : makePost(medias: medias)
                    )
                    .environment(\.globalAppearance, GlobalAppearance.companion.Default)
                    .environment(\.timelineAppearance, TimelinePresentationAppearancePatchHelper.shared.setPostActionStyle(
                        patch: AppearancePatch.companion.EMPTY,
                        value: hidePostActions ? .hidden : .leftAligned
                    ).toTimelineAppearance())
                }
            }
            .background(ClearFullScreenBackground())
            .colorScheme(.dark)
        }
    }

    @MainActor
    private func configureTranslationFixture() async {
        let settings = KotlinPresenter(presenter: ImageTranslationSettingsPresenter())
        if ProcessInfo.processInfo.arguments.contains("--image-translation-prefill-fixture") {
            let basic = KotlinPresenter(presenter: AiConfigPresenter(agentConfig: false))
            basic.state.setOpenAIServerUrl(value: "http://127.0.0.1:18473/v1")
            basic.state.setOpenAIApiKey(value: "prefill-fixture-key")
            basic.state.setOpenAIModel(value: "prefill-fixture-image")
            for _ in 0..<250 {
                if basic.state.openAIModel == "prefill-fixture-image"
                    && basic.state.openAIApiKey == "prefill-fixture-key"
                    && basic.state.openAIServerUrl == "http://127.0.0.1:18473/v1" { break }
                try? await Task.sleep(for: .milliseconds(20))
            }
            settings.state.selectProvider(provider: .openAiimages)
            for _ in 0..<250 {
                if settings.state.config.provider == .openAiimages { break }
                try? await Task.sleep(for: .milliseconds(20))
            }
            let state = settings.state
            state.setUseSharedConfig(value: true)
            state.setField(id: "baseUrl", value: "")
            state.setField(id: "apiKey", value: "")
            state.setField(id: "model", value: "")
            let prompt = "Keep the image prompt " + UUID().uuidString
            state.setField(id: "prompt", value: prompt)
            for _ in 0..<250 {
                if settings.state.useSharedConfig
                    && settings.state.config.value(id: "model") == "prefill-fixture-image"
                    && settings.state.config.value(id: "prompt") == prompt { return }
                try? await Task.sleep(for: .milliseconds(20))
            }
            assertionFailure("Could not prepare persisted empty fields with shared AI configuration")
            return
        }
        if translationSettings {
            settings.state.selectProvider(provider: .gemini)
            for _ in 0..<250 {
                if settings.state.config.provider == .gemini { break }
                try? await Task.sleep(for: .milliseconds(20))
            }
            settings.state.setField(id: "baseUrl", value: "http://127.0.0.1:18473/v1beta")
            for _ in 0..<250 {
                if settings.state.config.value(id: "baseUrl") == "http://127.0.0.1:18473/v1beta" { break }
                try? await Task.sleep(for: .milliseconds(20))
            }
        }
        if !ProcessInfo.processInfo.arguments.contains("--image-translation-fixture") {
            settings.state.selectProvider(provider: .disabled)
            for _ in 0..<250 {
                if settings.state.config.provider == .disabled { return }
                try? await Task.sleep(for: .milliseconds(20))
            }
            assertionFailure("Could not disable providers for the offline fixture")
            return
        }
        settings.state.selectProvider(provider: .openAiimages)
        for _ in 0..<250 {
            if settings.state.config.provider == .openAiimages { break }
            try? await Task.sleep(for: .milliseconds(20))
        }
        let state = settings.state
        state.setUseSharedConfig(value: false)
        let prompt = "Translate visible text into {targetLanguage}. Offline fixture " + UUID().uuidString
        state.setField(id: "prompt", value: prompt)
        state.setField(id: "baseUrl", value: "http://127.0.0.1:18473/v1")
        state.setField(id: "apiKey", value: "offline-fixture-key")
        state.setField(id: "model", value: "offline-fixture")
        for _ in 0..<250 {
            if settings.state.config.configured && settings.state.config.value(id: "baseUrl") == "http://127.0.0.1:18473/v1" && settings.state.config.value(id: "prompt") == prompt { return }
            try? await Task.sleep(for: .milliseconds(20))
        }
        assertionFailure("Could not configure the local image translation fixture")
    }

    private func makeMedia(index: Int = 0) async -> any UiMedia {
        let image = UIGraphicsImageRenderer(size: CGSize(width: 320, height: 240)).image { context in
            UIColor.systemTeal.setFill()
            context.fill(CGRect(x: 0, y: 0, width: 320, height: 240))
        }
        let imageURL = FileManager.default.temporaryDirectory.appendingPathComponent("media-fixture-\(index).png")
        try? image.pngData()?.write(to: imageURL)
        try? await ImageCache.default.store(image, forKey: imageURL.absoluteString, toDisk: false)
        guard video else {
            return UiMediaImage(url: imageURL.absoluteString, previewUrl: imageURL.absoluteString, description: "Fixture image", height: 240, width: 320, sensitive: false, customHeaders: nil)
        }
        let videoURL = FileManager.default.temporaryDirectory.appendingPathComponent("media-fixture.mp4")
        try? FileManager.default.removeItem(at: videoURL)
        do {
            let writer = try AVAssetWriter(url: videoURL, fileType: .mp4)
            let input = AVAssetWriterInput(mediaType: .video, outputSettings: [AVVideoCodecKey: AVVideoCodecType.h264, AVVideoWidthKey: 32, AVVideoHeightKey: 32])
            let adaptor = AVAssetWriterInputPixelBufferAdaptor(assetWriterInput: input, sourcePixelBufferAttributes: [kCVPixelBufferPixelFormatTypeKey as String: kCVPixelFormatType_32ARGB, kCVPixelBufferWidthKey as String: 32, kCVPixelBufferHeightKey as String: 32])
            writer.add(input)
            writer.startWriting()
            writer.startSession(atSourceTime: .zero)
            for frame in 0..<4 {
                while !input.isReadyForMoreMediaData { try await Task.sleep(for: .milliseconds(10)) }
                var buffer: CVPixelBuffer?
                CVPixelBufferCreate(nil, 32, 32, kCVPixelFormatType_32ARGB, nil, &buffer)
                if let buffer {
                    CVPixelBufferLockBaseAddress(buffer, [])
                    memset(CVPixelBufferGetBaseAddress(buffer), 120, CVPixelBufferGetDataSize(buffer))
                    CVPixelBufferUnlockBaseAddress(buffer, [])
                    adaptor.append(buffer, withPresentationTime: CMTime(value: Int64(frame), timescale: 1))
                }
            }
            input.markAsFinished()
            await writer.finishWriting()
        } catch {
            assertionFailure("Could not create video fixture: \(error)")
        }
        return UiMediaVideo(url: videoURL.absoluteString, thumbnailUrl: imageURL.absoluteString, description: "Fixture video", height: 240, width: 320, customHeaders: nil, downloadUrl: nil)
    }

    private func richText(_ text: String) -> UiRichText {
        let style = RenderTextStyle(link: nil, bold: false, italic: false, strikethrough: false, monospace: false, code: false, underline: false, small: false, time: false, imageLink: nil)
        return UiRichText(renderRuns: [RenderContent.Text(runs: [RenderRun.Text(text: text, style: style)], block: RenderBlockStyle())], isRtl: false, raw: text, innerText: text, imageUrls: [])
    }

    private func makePost(medias: [any UiMedia]) -> UiTimelineV2.Post {
        let profile = UiProfile(key: MicroBlogKey(id: "reader", host: "example.invalid"), handle: UiHandle(raw: "reader", host: "example.invalid"), avatar: nil as UiMediaImage?, nameInternal: richText("Media Test User"), platformId: "mastodon", platformIcon: .world, clickEvent: ClickEventNoop.shared, banner: nil, description: nil, sourceLanguages: [], translationDisplayState: .hidden, matrices: UiProfile.Matrices(fansCount: 0, followsCount: 0, statusesCount: 0, platformFansCount: nil), mark: [], bottomContent: nil)
        let action = ActionMenu.Item(updateKey: "like", icon: .like, text: nil, count: UiNumber(value: 1), color: .contentColor, clickEvent: ClickEventNoop.shared, actionFamily: .like, enabled: true)
        return UiTimelineV2.Post(platformId: "mastodon", images: medias, sensitive: false, contentWarning: nil, user: profile, platformIcon: .world, sourceLanguages: [], translationDisplayState: .hidden, content: UiTranslatableText(original: richText(String(repeating: "Post details for the native media sheet. ", count: 40)), translation: nil), actions: [action], poll: nil, statusKey: MicroBlogKey(id: "media-fixture", host: "example.invalid"), card: nil, createdAt: KotlinInstant.companion.fromEpochMilliseconds(epochMilliseconds: 0).toUi(), emojiReactions: [], sourceChannel: nil, visibility: nil, replyToHandle: nil, references: [], clickEvent: ClickEventNoop.shared, mediaClickPolicy: .openStatusMedia, accountType: AccountType.Guest.shared, itemKey: "media-fixture")
    }
}
#endif
