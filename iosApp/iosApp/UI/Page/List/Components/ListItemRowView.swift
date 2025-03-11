import Kingfisher
import shared
import SwiftUI

//  list 行
public struct ListItemRowView: View {
    let list: UiList
    let isPinned: Bool
    let showCreator: Bool
    let showMemberCount: Bool
    let onTap: (() -> Void)?
    let onPinTap: (() -> Void)?

    public init(
        list: UiList,
        isPinned: Bool,
        showCreator: Bool = true,
        showMemberCount: Bool = true,
        onTap: (() -> Void)? = nil,
        onPinTap: (() -> Void)? = nil,
        onEditTap _: (() -> Void)? = nil
    ) {
        self.list = list
        self.isPinned = isPinned
        self.showCreator = showCreator
        self.showMemberCount = showMemberCount
        self.onTap = onTap
        self.onPinTap = onPinTap
    }

    public var body: some View {
        Button(action: {
            onTap?()
        }) {
            HStack(spacing: 12) {
                ListIconView(imageUrl: list.avatar ?? "", size: 50)

                // 列表信息
                VStack(alignment: .leading, spacing: 4) {
                    HStack(spacing: 4) {
                        Text(list.title)
                            .font(.headline)
                            .lineLimit(1)

                        // 成员数量（可选）
                        if showMemberCount, Int(list.likedCount) > 0 {
                            Text("·\(list.likedCount) members")
                                .font(.subheadline)
                                .foregroundColor(.gray)
                                .lineLimit(1)
                        }
                    }

                    if showCreator, let creator = list.creator {
                        HStack(spacing: 4) {
                            let avatarUrl = creator.avatar
                            if avatarUrl != nil, let url = URL(string: avatarUrl ?? "") {
                                KFImage(url)
                                    .placeholder {
                                        Circle()
                                            .fill(Color.gray.opacity(0.2))
                                    }
                                    .resizable()
                                    .aspectRatio(contentMode: .fill)
                                    .frame(width: 16, height: 16)
                                    .clipShape(Circle())
                            } else {
                                Image(systemName: "person.circle.fill")
                                    .resizable()
                                    .frame(width: 16, height: 16)
                                    .foregroundColor(.gray)
                            }

                            Text(creator.name.raw)
                                .font(.caption)
                                .foregroundColor(.gray)
                                .lineLimit(1)
                                .fixedSize(horizontal: false, vertical: true)
                        }
                    }
                }
                .frame(maxWidth: .infinity, alignment: .leading)

                HStack(spacing: 16) {
                    if let onPinTap {
                        Button(action: onPinTap) {
                            Image(systemName: isPinned ? "pin.fill" : "pin")
                                .foregroundColor(.blue)
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
