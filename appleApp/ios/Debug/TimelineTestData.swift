#if DEBUG
import KotlinSharedUI

@MainActor
func makeTimelineTestRow(_ index: Int, liked: Bool = false, imageHeight: Float = 600,
                     mediaCount: Int? = nil, textRepeatCount: Int? = nil) -> UiTimelineV2 {
    let text = "Post \(index): " + String(repeating: "Rotation should preserve this reading item while text wraps and media resizes. ", count: textRepeatCount ?? (index % 4 + 1))
    let style = RenderTextStyle(link: nil, bold: false, italic: false, strikethrough: false, monospace: false, code: false, underline: false, small: false, time: false, imageLink: nil)
    let rich = UiRichText(renderRuns: [RenderContent.Text(runs: [RenderRun.Text(text: text, style: style)], block: RenderBlockStyle())], isRtl: false, raw: text, innerText: text, imageUrls: [])
    let action = ActionMenu.Item(updateKey: "like", icon: liked ? .unlike : .like, text: nil, count: UiNumber(value: liked ? 2 : 1), color: liked ? .red : .contentColor, clickEvent: ClickEventNoop.shared, actionFamily: .like, enabled: true)
    let images: [UiMedia] = (0..<(mediaCount ?? (index % 3 == 0 ? 1 : 4))).map { i in UiMediaImage(url: "https://example.invalid/fixture-\(i).png", previewUrl: "https://example.invalid/fixture-\(i).png", description: nil, height: imageHeight, width: 800, sensitive: false, customHeaders: nil, proxy: ProxyRef.Unbound.shared) }
    let profile = UiProfile(key: MicroBlogKey(id: "user", host: "example.invalid"), handle: UiHandle(raw: "reader", host: "example.invalid", displayOverride: nil), avatar: nil as UiMediaImage?, nameInternal: rich, platformId: "mastodon", platformIcon: IconType.Material(icon: .world), clickEvent: ClickEventNoop.shared, banner: nil, description: nil, sourceLanguages: [], translationDisplayState: .hidden, matrices: UiProfile.Matrices(fansCount: 0, followsCount: 0, statusesCount: 0, platformFansCount: nil), mark: [], bottomContent: nil, pluginId: nil)
    let post = UiTimelineV2.Post(platformId: "mastodon", images: images, sensitive: false, contentWarning: nil, user: profile, platformIcon: IconType.Material(icon: .world), sourceLanguages: [], translationDisplayState: .hidden, content: UiTranslatableText(original: rich, translation: nil), actions: [action], poll: nil, statusKey: MicroBlogKey(id: "case-\(index)", host: "example.invalid"), card: nil, createdAt: KotlinInstant.companion.fromEpochMilliseconds(epochMilliseconds: 0).toUi(), emojiReactions: [], sourceChannel: nil, visibility: nil, replyToHandle: nil, references: [], clickEvent: ClickEventNoop.shared, mediaClickPolicy: .openStatusMedia, accountType: AccountType.Guest.shared, itemKey: "case-\(index)", pluginId: nil, authorSubtitleInternal: nil)
    return UiTimelineV2.TimelinePostItem(post: post, presentation: UiTimelineV2.PostPresentation(message: nil, inlineParents: [], quotes: [], repost: nil, notificationKey: nil), itemKey: "case-\(index)")
}
#endif
