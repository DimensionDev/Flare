import SwiftUI
import KotlinSharedUI

public struct UserOnelineView<TrailingContent: View>: View {
    private let data: UiProfile
    private let showAvatar: Bool
    private let trailing: () -> TrailingContent
    private let onClicked: (() -> Void)?

    public init(
        data: UiProfile,
        showAvatar: Bool,
        @ViewBuilder trailing: @escaping () -> TrailingContent,
        onClicked: (() -> Void)? = nil
    ) {
        self.data = data
        self.showAvatar = showAvatar
        self.trailing = trailing
        self.onClicked = onClicked
    }

    public var body: some View {
        HStack(spacing: 4) {
            if showAvatar {
                AvatarView(data: data.avatar?.url, customHeader: data.avatar?.customHeaders)
                    .frame(width: 20, height: 20)
                    .accessibilityLabel(Text(verbatim: profileActionLabel))
                    .onTapGesture {
                        onClicked?()
                    }
            }
            HStack(spacing: 4) {
                RichText(text: data.name)
                Text(data.handle.canonical)
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            trailing()
        }
        .lineLimit(1)
    }

    private var profileActionLabel: String {
        String(
            format: String(
                localized: "profile_open_user",
                defaultValue: "Open profile for %@"
            ),
            locale: .current,
            data.handle.canonical
        )
    }
}

public extension UserOnelineView {
    init(data: UiProfile) where TrailingContent == EmptyView {
        self.init(data: data, showAvatar: true) {
            EmptyView()
        }
    }
}
