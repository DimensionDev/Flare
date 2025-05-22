import shared
import SwiftUI

struct DMRoomItemView: View {
    let room: UiDMRoom
    @Environment(FlareTheme.self) private var theme

    var body: some View {
        HStack(spacing: 12) {
            if !room.hasUser {
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
                ZStack {
                    if room.users.count == 1 {
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

                        if room.users.count > 1 {
                            Text("\(room.users.count)")
                                .font(.caption2)
                                .padding(4)
                                // .background(Color(UIColor.systemBackground))
                                .clipShape(Circle())
                                .offset(x: 20, y: 20)
                        }
                    }
                }
                .frame(width: 50, height: 50)
            }

            VStack(alignment: .leading, spacing: 4) {
                HStack {
                    if room.hasUser {
                        if room.users.count == 1 {
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
                            Text(room.getFormattedTitle())
                                .font(.headline)
                                .lineLimit(1)
                        }
                    } else {
                        if let currentUser = UserManager.shared.getCurrentUser() {
                            Text(currentUser.name.raw)
                                .font(.headline)
                                .lineLimit(1)
                        } else {
                            Text("My DM")
                                .font(.headline)
                                .lineLimit(1)
                        }
                    }

                    Spacer()

                    if let lastMessage = room.lastMessage {
                        Text(formatTime(lastMessage.timestamp))
                            .font(.caption)
                            .foregroundColor(.gray)
                    }
                }

                Text(room.lastMessageText.isEmpty ? "" : room.lastMessageText)
                    .font(.subheadline)
                    .foregroundColor(.gray)
                    .lineLimit(1)
            }

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

    private func formatTime(_ date: Date) -> String {
        let formatter = DateFormatter()
        formatter.timeStyle = .short
        return formatter.string(from: date)
    }
}

extension UiDMRoom {
    func getFormattedTitle() -> String {
        if hasUser {
            if users.count == 1 {
                if UserManager.shared.isCurrentUser(user: users[0]) {
                    return "Me"
                }

                return users[0].name.raw
            } else if users.count > 1 {
                let firstUser = users[0].name.raw
                let secondUser = users[1].name.raw
                return users.count > 2
                    ? "\(firstUser), \(secondUser)..."
                    : "\(firstUser), \(secondUser)"
            }
        }

        return "Chat"
    }
}

struct ForEachWithIndex<Content: View>: View {
    let startIndex: Int32
    let count: Int32
    let content: (Int32) -> Content

    init(_ startIndex: Int32, count: Int32, @ViewBuilder content: @escaping (Int32) -> Content) {
        self.startIndex = startIndex
        self.count = count
        self.content = content
    }

    var body: some View {
        ForEach(0 ..< Int(count), id: \.self) { index in
            content(Int32(index) + startIndex)
        }
    }
}
