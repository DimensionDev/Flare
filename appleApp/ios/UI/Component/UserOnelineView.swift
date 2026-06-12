import SwiftUI
import KotlinSharedUI
import FlareAppleUI

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
        HStack {
            if showAvatar {
                AvatarView(data: data.avatar?.url, customHeader: data.avatar?.customHeaders)
                    .frame(width: 20, height: 20)
                    .onTapGesture {
                        onClicked?()
                    }
            }
            HStack {
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
}

public extension UserOnelineView {
    init(data: UiProfile) where TrailingContent == EmptyView {
        self.init(data: data, showAvatar: true) {
            EmptyView()
        }
    }
}
