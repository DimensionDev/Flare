import Kingfisher
import shared
import SwiftUI

public struct ListItemRowView: View {
    let list: UiList
    let isPinned: Bool
    let showCreator: Bool
    let showMemberCount: Bool
    let defaultUser: UiUserV2?
    let onTap: (() -> Void)?
    let onPinTap: (() -> Void)?
    @Environment(FlareTheme.self) private var theme

    public init(
        list: UiList,
        isPinned: Bool,
        showCreator: Bool = true,
        showMemberCount: Bool = true,
        defaultUser: UiUserV2? = nil,
        onTap: (() -> Void)? = nil,
        onPinTap: (() -> Void)? = nil,
        onEditTap _: (() -> Void)? = nil
    ) {
        self.list = list
        self.isPinned = isPinned
        self.showCreator = showCreator
        self.showMemberCount = showMemberCount
        self.defaultUser = defaultUser
        self.onTap = onTap
        self.onPinTap = onPinTap
    }

    public var body: some View {
        Button(action: {
            FlareHapticManager.shared.buttonPress()
            onTap?()
        }) {
            HStack(spacing: 12) {
                ListIconView(imageUrl: list.avatar ?? "", size: 50, listId: list.id)

                VStack(alignment: .leading, spacing: 4) {
                    HStack(spacing: 4) {
                        Text(list.title)
                            .font(.headline)
                            .lineLimit(1)

                        if showMemberCount, Int(list.likedCount) > 0 {
                            Text("·\(list.likedCount) members")
                                .font(.subheadline)
                                .foregroundColor(.gray)
                                .lineLimit(1)
                        }
                    }

                    if showCreator {
                        HStack(spacing: 4) {
                            if let user = defaultUser {
                                listUserInfoView(avatar: user.avatar, name: user.name.raw)
                            } else if let creator = list.creator {
                                listUserInfoView(avatar: creator.avatar, name: creator.name.raw)
                            }
                        }
                    }
                }
                .frame(maxWidth: .infinity, alignment: .leading)

                HStack(spacing: 16) {
                    if let onPinTap {
                        Button(action: onPinTap) {
                            Image(systemName: isPinned ? "pin.fill" : "pin")
                                .foregroundColor(theme.tintColor)
                                .font(.system(size: 14))
                        }
                        .buttonStyle(BorderlessButtonStyle())
                    }
                }
            }
            .padding(.vertical, 8)
        }
        .buttonStyle(PlainButtonStyle())
    }
}
