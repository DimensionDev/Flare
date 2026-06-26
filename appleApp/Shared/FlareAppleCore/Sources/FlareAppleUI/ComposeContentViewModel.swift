import Foundation
@preconcurrency import KotlinSharedUI
import SwiftUI

@Observable
public final class ComposeContentViewModel {
    public var text: String
    public var contentWarning: String
    public var enableContentWarning: Bool
    public var showEmoji: Bool
    public var pollViewModel: ComposePollViewModel
    public var languages: [String]

    public init(
        text: String = "",
        contentWarning: String = "",
        enableContentWarning: Bool = false,
        showEmoji: Bool = false,
        pollViewModel: ComposePollViewModel = ComposePollViewModel(),
        languages: [String]? = nil
    ) {
        self.text = text
        self.contentWarning = contentWarning
        self.enableContentWarning = enableContentWarning
        self.showEmoji = showEmoji
        self.pollViewModel = pollViewModel
        self.languages = languages ?? Self.defaultLanguages
    }

    public static var defaultLanguages: [String] {
        if let code = Locale.current.language.languageCode?.identifier {
            return [code]
        }
        return ["en"]
    }

    public var hasDraftContent: Bool {
        !text.isEmpty ||
        !contentWarning.isEmpty ||
        pollViewModel.enabled
    }

    public func toggleContentWarning() {
        enableContentWarning.toggle()
    }

    public func togglePoll() {
        pollViewModel.toggleEnabled()
    }

    public func addEmoji(emoji: UiEmoji) {
        showEmoji = false
    }

    public func makeComposeData(
        visibility: UiTimelineV2.PostVisibility,
        medias: [ComposeData.Media],
        sensitive: Bool,
        composeStatus: ComposeStatus?,
        localOnly: Bool = false
    ) -> ComposeData {
        ComposeData(
            content: text,
            visibility: visibility,
            language: languages,
            medias: medias,
            sensitive: sensitive,
            spoilerText: contentWarning,
            poll: pollViewModel.makeComposePoll(),
            localOnly: localOnly,
            referenceStatus: referenceStatus(from: composeStatus)
        )
    }

    public func applyDraft(_ draft: UiDraft) -> ComposeDraftRestoreResult {
        let data = draft.data
        text = data.content
        contentWarning = data.spoilerText ?? ""
        enableContentWarning = !(data.spoilerText ?? "").isEmpty
        if !data.language.isEmpty {
            languages = data.language
        }
        pollViewModel.apply(poll: data.poll)
        return ComposeDraftRestoreResult(
            sensitive: data.sensitive,
            visibility: data.visibility
        )
    }

    private func referenceStatus(from composeStatus: ComposeStatus?) -> ComposeData.ReferenceStatus? {
        if let composeStatus {
            ComposeData.ReferenceStatus(composeStatus: composeStatus)
        } else {
            nil
        }
    }
}

public struct ComposeDraftRestoreResult {
    public let sensitive: Bool
    public let visibility: UiTimelineV2.PostVisibility

    public init(
        sensitive: Bool,
        visibility: UiTimelineV2.PostVisibility
    ) {
        self.sensitive = sensitive
        self.visibility = visibility
    }
}
