import FlareAppleCore
import KotlinSharedUI
import SwiftUI

private let faviconCornerRadius: CGFloat = 4

public struct TimelineTabTitle: View {
    private let title: UiText

    public init(title: UiText) {
        self.title = title
    }

    public var body: some View {
        Text(title.text)
    }
}

public struct TabIcon: View {
    private let icon: IconType
    private let size: CGFloat
    private let iconOnly: Bool

    public init(
        icon: IconType,
        size: CGFloat = 20,
        iconOnly: Bool = false
    ) {
        self.icon = icon
        self.size = size
        self.iconOnly = iconOnly
    }

    public var body: some View {
        switch onEnum(of: icon) {
        case .material(let material):
            MaterialTabIcon(icon: material.icon)
                .frame(width: size, height: size)
        case .avatar(let avatar):
            AvatarTabIcon(userKey: avatar.accountKey, accountType: AccountType.Specific(accountKey: avatar.accountKey))
                .frame(width: size, height: size)
                .id("\(avatar.accountKey.id)@\(avatar.accountKey.host)")
        case .url(let url):
            NetworkImage(data: url.url)
                .frame(width: size, height: size)
                .clipShape(RoundedRectangle(cornerRadius: faviconCornerRadius, style: .continuous))
        case .mixed(let mixed):
            if iconOnly {
                MaterialTabIcon(icon: mixed.icon)
                    .frame(width: size, height: size)
            } else {
                ZStack(
                    alignment: .bottomTrailing
                ) {
                    AvatarTabIcon(userKey: mixed.accountKey, accountType: AccountType.Specific(accountKey: mixed.accountKey))
                        .frame(width: size, height: size)
                        .id("\(mixed.accountKey.id)@\(mixed.accountKey.host)")
                    MaterialTabIcon(icon: mixed.icon)
                        .padding(2)
                        .background(Color.white)
                        .foregroundStyle(Color.black)
                        .clipShape(.circle)
                        .frame(width: size / 2, height: size / 2)
                }
                .frame(width: size, height: size)
            }
        case .favIcon(let favIcon):
            FavTabIcon(host: favIcon.host)
                .frame(width: size, height: size)
                .clipShape(RoundedRectangle(cornerRadius: faviconCornerRadius, style: .continuous))
                .id(favIcon.host)
        }
    }
}

public extension TabIcon {
    init(
        tabItem: UiTimelineTabItem,
        size: CGFloat = 20,
        iconOnly: Bool = false
    ) {
        self.init(icon: tabItem.icon, size: size, iconOnly: iconOnly)
    }
}

public struct MaterialTabIcon: View {
    private let icon: UiIcon

    public init(icon: UiIcon) {
        self.icon = icon
    }

    public var body: some View {
        Image(fontAwesome: icon.fontAwesomeIcon)
            .resizable()
            .scaledToFit()
    }
}

public struct AvatarTabIcon: View {

    @StateObject private var presenter: KotlinPresenter<UserState>

    public init(userKey: MicroBlogKey, accountType: AccountType) {
        self._presenter = .init(wrappedValue: .init(presenter: UserPresenter(accountType: accountType, userKey: userKey)))
    }

    public var body: some View {
        StateView(state: presenter.state.user) { user in
            AvatarView(data: user.avatar?.url, customHeader: user.avatar?.customHeaders)
        } loadingContent: {
            Image(fontAwesome: .globe)
                .resizable()
                .scaledToFit()
                .redacted(reason: .placeholder)
        }
    }
}

public struct FavTabIcon: View {
    @StateObject private var presenter: KotlinPresenter<UiState<NSString>>

    public init(host: String) {
        self._presenter = .init(wrappedValue: .init(presenter: FavIconPresenter(host: host)))
    }

    public var body: some View {
        StateView(state: presenter.state) { url in
            NetworkImage(data: .init(url))
                .clipShape(RoundedRectangle(cornerRadius: faviconCornerRadius, style: .continuous))
        } loadingContent: {
            Image(fontAwesome: .globe)
                .resizable()
                .scaledToFit()
                .redacted(reason: .placeholder)
        }
    }
}
