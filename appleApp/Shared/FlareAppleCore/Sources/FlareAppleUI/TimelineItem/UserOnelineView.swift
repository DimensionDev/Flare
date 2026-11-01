import SwiftUI
import KotlinSharedUI
import FlareAppleCore

public struct UserOnelineView<TrailingContent: View>: View {
    private let data: UiProfile
    private let showAvatar: Bool
    private let subtitle: String
    private let trailing: () -> TrailingContent
    private let onClicked: (() -> Void)?

    public init(
        data: UiProfile,
        showAvatar: Bool,
        subtitle: String? = nil,
        @ViewBuilder trailing: @escaping () -> TrailingContent,
        onClicked: (() -> Void)? = nil
    ) {
        self.data = data
        self.showAvatar = showAvatar
        self.subtitle = subtitle ?? data.handle.display
        self.trailing = trailing
        self.onClicked = onClicked
    }

    public var body: some View {
        HStack(spacing: 4) {
            if showAvatar {
                AvatarView(data: data.avatar)
                    .frame(width: 20, height: 20)
                    .accessibilityLabel(
                        Text(verbatim: openProfileAccessibilityLabel(handle: data.handle.display))
                    )
                    .onTapGesture {
                        onClicked?()
                    }
            }
            HStack(spacing: 4) {
                RichText(text: data.name)
                Text(subtitle)
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            trailing()
        }
        .lineLimit(1)
    }
}

public extension UserOnelineView {
    init(data: UiProfile) where TrailingContent == EmptyView {
        self.init(data: data, showAvatar: true) {
            EmptyView()
        }
    }
}
