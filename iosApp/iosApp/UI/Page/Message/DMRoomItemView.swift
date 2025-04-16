import shared
import SwiftUI

 
struct DMRoomItemView: View {
    let room: UiDMRoom

    var body: some View {
        HStack(spacing: 12) {
            // 对话头像部分
            if !room.hasUser {
                // 没有用户时显示当前用户的头像
                if let currentUser = UserManager.shared.getCurrentUser() {
                    AsyncImage(url: URL(string: currentUser.avatar)) { phase in
                        switch phase {
                        case .empty:
                            Circle().fill(Color.accentColor.opacity(0.2))
                        case let .success(image):
                            image.resizable().scaledToFill()
                        case .failure:
                            Circle()
                                .fill(Color.accentColor.opacity(0.2))
                                .overlay(
                                    Image(systemName: "person.circle.fill")
                                        .resizable()
                                        .scaledToFit()
                                        .padding(10)
                                        .foregroundColor(.accentColor)
                                )
                        @unknown default:
                            Circle().fill(Color.accentColor.opacity(0.2))
                        }
                    }
                    .frame(width: 50, height: 50)
                    .clipShape(Circle())
                } else {
                    // 备选方案：如果无法获取当前用户
                    Circle()
                        .fill(Color.accentColor.opacity(0.2))
                        .frame(width: 50, height: 50)
                        .overlay(
                            Image(systemName: "person.circle.fill")
                                .resizable()
                                .scaledToFit()
                                .padding(10)
                                .foregroundColor(.accentColor)
                        )
                }
            } else {
                // 用户头像处理：单个用户或多个用户
                ZStack {
                    if room.users.count == 1 {
                        // 单个用户头像
                        AsyncImage(url: URL(string: room.users[0].avatar)) { phase in
                            switch phase {
                            case .empty:
                                Circle().fill(Color.gray.opacity(0.3))
                            case let .success(image):
                                image.resizable().scaledToFill()
                            case .failure:
                                Circle().fill(Color.gray.opacity(0.3))
                                    .overlay(
                                        Image(systemName: "person.circle.fill")
                                            .resizable()
                                            .scaledToFit()
                                            .padding(15)
                                            .foregroundColor(.gray)
                                    )
                            @unknown default:
                                Circle().fill(Color.gray.opacity(0.3))
                            }
                        }
                        .frame(width: 50, height: 50)
                        .clipShape(Circle())
                    } else {
                        // 多个用户时显示叠加的头像（最多显示前两个）
                        ForEach(0 ..< min(2, room.users.count), id: \.self) { index in
                            AsyncImage(url: URL(string: room.users[index].avatar)) { phase in
                                switch phase {
                                case .empty:
                                    Circle().fill(Color.gray.opacity(0.3))
                                case let .success(image):
                                    image.resizable().scaledToFill()
                                case .failure:
                                    Circle().fill(Color.gray.opacity(0.3))
                                @unknown default:
                                    Circle().fill(Color.gray.opacity(0.3))
                                }
                            }
                            .frame(width: index == 0 ? 50 : 40, height: index == 0 ? 50 : 40)
                            .clipShape(Circle())
                            .offset(x: CGFloat(index * 12), y: CGFloat(index * 12))
                        }

                        // 如果用户数超过1个，显示用户数量
                        if room.users.count > 1 {
                            Text("\(room.users.count)")
                                .font(.caption2)
                                .padding(4)
                                .background(Color(UIColor.systemBackground))
                                .clipShape(Circle())
                                .offset(x: 20, y: 20)
                        }
                    }
                }
                .frame(width: 50, height: 50)
            }

            // 对话信息
            VStack(alignment: .leading, spacing: 4) {
                HStack {
                    // 对话名称 - 显示真实用户名或当前用户名
                    if room.hasUser {
                        if room.users.count == 1 {
                            // 一个用户时显示名称和用户名
                            VStack(alignment: .leading, spacing: 0) {
                                Text(room.users[0].name.raw)
                                    .font(.headline)
                                    .lineLimit(1)

                                Text(room.users[0].handle)
                                    .font(.caption)
                                    .foregroundColor(.gray)
                                    .lineLimit(1)
                            }
                        } else {
                            // 多个用户时只显示名称列表
                            Text(room.getFormattedTitle())
                                .font(.headline)
                                .lineLimit(1)
                        }
                    } else {
                        // 没有参与者时显示当前用户信息
                        if let currentUser = UserManager.shared.getCurrentUser() {
                            Text(currentUser.name.raw)
                                .font(.headline)
                                .lineLimit(1)
                        } else {
                            Text("我的私信")
                                .font(.headline)
                                .lineLimit(1)
                        }
                    }

                    Spacer()

                    // 显示时间戳
                    if let lastMessage = room.lastMessage {
                        Text(formatTime(lastMessage.timestamp))
                            .font(.caption)
                            .foregroundColor(.gray)
                    }
                }

                // 最后一条消息
                Text(room.lastMessageText.isEmpty ? "" : room.lastMessageText)
                    .font(.subheadline)
                    .foregroundColor(.gray)
                    .lineLimit(1)
            }

            // 未读数
            if room.unreadCount > 0 {
                Spacer()
                Text("\(room.unreadCount)")
                    .font(.caption)
                    .padding(.horizontal, 8)
                    .padding(.vertical, 4)
                    .background(Color.accentColor)
                    .foregroundColor(.white)
                    .clipShape(Capsule())
            }
        }
    }

    // 格式化时间显示
    private func formatTime(_ date: Date) -> String {
        let formatter = DateFormatter()
        formatter.timeStyle = .short
        return formatter.string(from: date)
    }
}

 

extension UiDMRoom {
    /// 获取格式化的对话标题
    func getFormattedTitle() -> String {
        if hasUser {
            if users.count == 1 {
                // 单个用户时，检查是否是自己
                if UserManager.shared.isCurrentUser(user: users[0]) {
                    return "我"
                }
                // 否则使用用户名称
                return users[0].name.raw
            } else if users.count > 1 {
                // 多个用户时组合名称
                let firstUser = users[0].name.raw
                let secondUser = users[1].name.raw
                return users.count > 2
                    ? "\(firstUser), \(secondUser)..."
                    : "\(firstUser), \(secondUser)"
            }
        }

        // 默认标题
        return "Chat"
    }
}
