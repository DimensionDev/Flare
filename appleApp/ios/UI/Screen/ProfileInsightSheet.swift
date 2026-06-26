import SwiftUI
import KotlinSharedUI
import FlareAppleCore
import FlareAppleUI

struct ProfileInsightSheet: View {
    @Environment(\.dismiss) private var dismiss
    @StateObject private var presenter: KotlinPresenter<ProfileInsightPresenterState>
    let accountType: AccountType
    let onNavigate: (Route) -> Void

    var body: some View {
        AgentChatView(
            messages: Array(presenter.state.messages),
            isRunning: presenter.state.room.isRunning,
            canSend: presenter.state.canSend,
            errorMessage: presenter.state.room.errorMessage,
            runningTrace: presenter.state.room.currentTrace?.localizedLabel ?? String(localized: "profile_insight_analyzing", defaultValue: "Analyzing this profile…"),
            inputPlaceholder: String(localized: "agent_chat_input_placeholder"),
            onInputChange: presenter.state.setInput,
            onSend: presenter.state.sendMessage,
            onInputRequestOptionSelected: presenter.state.selectInputRequestOption,
            onPostClick: { post in
                onNavigate(.statusDetail(post.accountType, post.statusKey))
            },
            onUserClick: { user in
                if let route = agentRoute(for: user) {
                    onNavigate(route)
                }
            }
        )
        .navigationTitle(String(localized: "profile_insight_title", defaultValue: "Profile insight"))
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .cancellationAction) {
                Button(
                    role: .cancel
                ) {
                    dismiss()
                } label: {
                    Label {
                        Text("Cancel")
                    } icon: {
                        Image(fontAwesome: .xmark)
                    }
                }
            }
        }
    }
}

extension ProfileInsightSheet {
    init(
        accountType: AccountType,
        userKey: MicroBlogKey,
        onNavigate: @escaping (Route) -> Void = { _ in }
    ) {
        self.accountType = accountType
        self.onNavigate = onNavigate
        self._presenter = .init(
            wrappedValue: .init(
                presenter: ProfileInsightPresenter(
                    accountType: accountType,
                    userKey: userKey
                )
            )
        )
    }
}

struct ProfileInsightUserPreview: View {
    let profile: UiProfile
    let onClick: (() -> Void)?

    init(profile: UiProfile, onClick: (() -> Void)? = nil) {
        self.profile = profile
        self.onClick = onClick
    }

    var body: some View {
        UserCompatView(data: profile)
            .padding(12)
            .overlay(
                RoundedRectangle(cornerRadius: 12)
                    .stroke(Color(.separator), lineWidth: 1)
            )
            .contentShape(RoundedRectangle(cornerRadius: 12))
            .if(onClick != nil) { view in
                view.onTapGesture {
                    onClick?()
                }
            }
    }
}

private func agentRoute(for user: UiProfile) -> Route? {
    guard let event = user.clickEvent as? ClickEventDeeplink else {
        return nil
    }
    return Route.fromDeepLink(url: event.url)
}
