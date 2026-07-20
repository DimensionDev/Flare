import FlareAppleCore
@preconcurrency import KotlinSharedUI
import SwiftUI

public struct ComposeActionBarContent<MediaControl: View>: View {
    private let isPollEnabled: Bool
    private let hasMedia: Bool
    private let canAddPoll: Bool
    private let canUseContentWarning: Bool
    private let visibility: UiTimelineV2.PostVisibility?
    private let allVisibilities: [UiTimelineV2.PostVisibility]
    private let emojiState: UiState<EmojiData>
    private let isEmojiPresented: Binding<Bool>
    private let languageCodes: [String]
    private let selectedLanguages: Binding<[String]>
    private let maxLanguageSelectionCount: Int
    private let remainingTextLength: Int?
    private let onTogglePoll: () -> Void
    private let onToggleContentWarning: () -> Void
    private let onSelectVisibility: (UiTimelineV2.PostVisibility) -> Void
    private let onSelectEmoji: (UiEmoji) -> Void
    private let mediaControl: () -> MediaControl

    public init(
        isPollEnabled: Bool,
        hasMedia: Bool,
        canAddPoll: Bool,
        canUseContentWarning: Bool,
        visibility: UiTimelineV2.PostVisibility?,
        allVisibilities: [UiTimelineV2.PostVisibility],
        emojiState: UiState<EmojiData>,
        isEmojiPresented: Binding<Bool>,
        languageCodes: [String],
        selectedLanguages: Binding<[String]>,
        maxLanguageSelectionCount: Int,
        remainingTextLength: Int?,
        onTogglePoll: @escaping () -> Void,
        onToggleContentWarning: @escaping () -> Void,
        onSelectVisibility: @escaping (UiTimelineV2.PostVisibility) -> Void,
        onSelectEmoji: @escaping (UiEmoji) -> Void,
        @ViewBuilder mediaControl: @escaping () -> MediaControl
    ) {
        self.isPollEnabled = isPollEnabled
        self.hasMedia = hasMedia
        self.canAddPoll = canAddPoll
        self.canUseContentWarning = canUseContentWarning
        self.visibility = visibility
        self.allVisibilities = allVisibilities
        self.emojiState = emojiState
        self.isEmojiPresented = isEmojiPresented
        self.languageCodes = languageCodes
        self.selectedLanguages = selectedLanguages
        self.maxLanguageSelectionCount = maxLanguageSelectionCount
        self.remainingTextLength = remainingTextLength
        self.onTogglePoll = onTogglePoll
        self.onToggleContentWarning = onToggleContentWarning
        self.onSelectVisibility = onSelectVisibility
        self.onSelectEmoji = onSelectEmoji
        self.mediaControl = mediaControl
    }

    public var body: some View {
        HStack {
            ScrollView(.horizontal) {
                HStack(spacing: 16) {
                    if !isPollEnabled {
                        mediaControl()
                    }

                    if canAddPoll && !hasMedia {
                        Button(action: onTogglePoll) {
                            Label {
                                Text("compose_poll_type", bundle: FlareAppleUILocalization.bundle)
                            } icon: {
                                Image(fontAwesome: .squarePollHorizontal)
                            }
                        }
                    }

                    if let visibility, !allVisibilities.isEmpty {
                        ComposeVisibilityMenu(
                            visibility: visibility,
                            allVisibilities: allVisibilities,
                            onSelect: onSelectVisibility
                        )
                    }

                    if canUseContentWarning {
                        Button(action: onToggleContentWarning) {
                            Label {
                                Text("compose_cw_placeholder", bundle: FlareAppleUILocalization.bundle)
                            } icon: {
                                Image(fontAwesome: .circleExclamation)
                            }
                        }
                    }

                    ComposeEmojiButton(
                        emojiState: emojiState,
                        isPresented: isEmojiPresented,
                        onSelect: onSelectEmoji
                    )

                    ComposeLanguageMenu(
                        languageCodes: languageCodes,
                        selectedLanguages: selectedLanguages,
                        maxLanguageSelectionCount: maxLanguageSelectionCount
                    )
                }
                .font(.title)
                .buttonStyle(.plain)
                .labelStyle(.iconOnly)
            }
            .scrollIndicators(.hidden)

            Spacer()

            if let remainingTextLength {
                Text("\(remainingTextLength)")
                    .foregroundStyle(remainingTextLength < 0 ? .red : .gray)
                    .monospacedDigit()
            }
        }
    }
}

public struct ComposeVisibilityMenu: View {
    private let visibility: UiTimelineV2.PostVisibility
    private let allVisibilities: [UiTimelineV2.PostVisibility]
    private let onSelect: (UiTimelineV2.PostVisibility) -> Void

    public init(
        visibility: UiTimelineV2.PostVisibility,
        allVisibilities: [UiTimelineV2.PostVisibility],
        onSelect: @escaping (UiTimelineV2.PostVisibility) -> Void
    ) {
        self.visibility = visibility
        self.allVisibilities = allVisibilities
        self.onSelect = onSelect
    }

    public var body: some View {
        Menu {
            ForEach(allVisibilities, id: \.self) { visibility in
                Button {
                    onSelect(visibility)
                } label: {
                    Label {
                        Text(
                            LocalizedStringKey(visibility.composeTitleKey),
                            bundle: FlareAppleUILocalization.bundle
                        )
                    } icon: {
                        StatusVisibilityView(data: visibility)
                    }
                    Text(
                        LocalizedStringKey(visibility.composeDescriptionKey),
                        bundle: FlareAppleUILocalization.bundle
                    )
                }
            }
        } label: {
            StatusVisibilityView(data: visibility)
        }
    }
}

public struct ComposeEmojiButton: View {
    private let emojiState: UiState<EmojiData>
    private let isPresented: Binding<Bool>
    private let onSelect: (UiEmoji) -> Void

    public init(
        emojiState: UiState<EmojiData>,
        isPresented: Binding<Bool>,
        onSelect: @escaping (UiEmoji) -> Void
    ) {
        self.emojiState = emojiState
        self.isPresented = isPresented
        self.onSelect = onSelect
    }

    public var body: some View {
        StateView(state: emojiState) { emojis in
            Button {
                isPresented.wrappedValue = true
            } label: {
                Label {
                    Text(verbatim: "Emoji")
                } icon: {
                    Image(fontAwesome: .faceSmile)
                }
            }
            .popover(isPresented: isPresented, arrowEdge: .bottom) {
                NavigationStack {
                    EmojiPopup(data: emojis, onItemClicked: onSelect)
                        .toolbar {
                            ToolbarItem(placement: .cancellationAction) {
                                Button(role: .cancel) {
                                    isPresented.wrappedValue = false
                                } label: {
                                    Label {
                                        Text("Cancel", bundle: FlareAppleUILocalization.bundle)
                                    } icon: {
                                        Image(fontAwesome: .xmark)
                                    }
                                }
                            }
                        }
                    #if os(macOS)
                        .frame(width: 400, height: 300)
                    #endif
                }
            }
        }
    }
}

private struct ComposeLanguageMenu: View {
    let languageCodes: [String]
    let selectedLanguages: Binding<[String]>
    let maxLanguageSelectionCount: Int

    var body: some View {
        if !languageCodes.isEmpty {
            Menu {
                ForEach(languageCodes, id: \.self) { code in
                    Button {
                        toggle(code)
                    } label: {
                        HStack {
                            Text(Locale.current.localizedString(forLanguageCode: code) ?? code)
                            if selectedLanguages.wrappedValue.contains(code) {
                                Image(systemName: "checkmark")
                            }
                        }
                    }
                }
            } label: {
                if let first = selectedLanguages.wrappedValue.first,
                   selectedLanguages.wrappedValue.count == 1 {
                    Text(first.uppercased())
                        .font(.caption)
                        .bold()
                        .padding(4)
                        .overlay {
                            RoundedRectangle(cornerRadius: 4)
                                .stroke(Color.primary, lineWidth: 1)
                        }
                } else {
                    Image(systemName: "globe")
                }
            }
        }
    }

    private func toggle(_ code: String) {
        if maxLanguageSelectionCount > 1 {
            if selectedLanguages.wrappedValue.contains(code) {
                if selectedLanguages.wrappedValue.count > 1 {
                    selectedLanguages.wrappedValue.removeAll { $0 == code }
                }
            } else if selectedLanguages.wrappedValue.count < maxLanguageSelectionCount {
                selectedLanguages.wrappedValue.append(code)
            }
        } else {
            selectedLanguages.wrappedValue = [code]
        }
    }
}

private extension UiTimelineV2.PostVisibility {
    var composeTitleKey: String {
        switch self {
        case .public:
            "status_visibility_public"
        case .home:
            "home_tab_home_title"
        case .followers:
            "matrix_followers"
        case .specified:
            "status_visibility_specified"
        case .channel:
            "status_visibility_public"
        }
    }

    var composeDescriptionKey: String {
        switch self {
        case .public:
            "status_visibility_public_description"
        case .home:
            "status_visibility_home_description"
        case .followers:
            "status_visibility_followers_description"
        case .specified:
            "status_visibility_specified_description"
        case .channel:
            "status_visibility_public_description"
        }
    }
}
