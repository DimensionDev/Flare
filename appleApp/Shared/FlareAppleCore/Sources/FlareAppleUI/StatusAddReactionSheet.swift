import SwiftUI
import KotlinSharedUI
import FlareAppleCore

public struct StatusAddReactionSheet: View {
    private let accountType: AccountType
    @Environment(\.dismiss) private var dismiss
    @StateObject private var presenter: KotlinPresenter<AddReactionState>

    public var body: some View {
        StateView(state: presenter.state.emojis) { emojiData in
            EmojiPopup(data: emojiData) { emoji in
                presenter.state.select(emoji: emoji)
                dismiss()
            }
        } loadingContent: {
            ProgressView()
        }
        .toolbar {
            ToolbarItem(placement: .cancellationAction) {
                Button(
                    role: .cancel
                ) {
                    dismiss()
                } label: {
                    Label {
                        Text("Cancel", bundle: FlareAppleUILocalization.bundle)
                    } icon: {
                        Image(fontAwesome: .xmark)
                    }
                }
            }
        }
    }
}

public extension StatusAddReactionSheet {
    init(
        accountType: AccountType,
        statusKey: MicroBlogKey,
    ) {
        self.accountType = accountType
        self._presenter = .init(wrappedValue: .init(presenter: AddReactionPresenter(accountType: accountType, statusKey: statusKey)))
    }
}
