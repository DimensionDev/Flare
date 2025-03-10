import shared
import SwiftUI

struct ListRowView: View {
    let list: UiList

    var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            Text(list.title).font(.headline)

            if let description = list.description_, !description.isEmpty {
                Text(description)
                    .font(.subheadline)
                    .foregroundColor(.gray)
                    .lineLimit(2)
            }

            HStack(spacing: 12) {
                Label("0", systemImage: "person.fill")
                    .font(.caption)
                    .foregroundColor(.secondary)

                if false { // list.isPrivate 属性不存在，暂时使用固定值
                    Label("私密", systemImage: "lock.fill")
                        .font(.caption)
                        .foregroundColor(.secondary)
                }
            }
            .padding(.top, 4)
        }
        .padding(.vertical, 8)
    }
}

// MARK: - 列表成员项视图

struct MemberRowView: View {
    let user: UiUserV2

    var body: some View {
        HStack(spacing: 12) {
            UserAvatarView(imageUrl: user.avatar)

            VStack(alignment: .leading, spacing: 4) {
                Text(user.name.raw)
                    .font(.headline)

                Text("@\(user.handle)")
                    .font(.subheadline)
                    .foregroundColor(.gray)

                // bio 属性不存在，注释掉相关代码
                // if let bio = user.bio, !bio.isEmpty {
                //     Text(bio)
                //         .font(.caption)
                //         .foregroundColor(.secondary)
                //         .lineLimit(2)
                //         .padding(.top, 4)
                // }
            }

            Spacer()
        }
        .padding(.vertical, 8)
    }
}

// MARK: - 时间线项视图

struct TimelineRowView: View {
    let timeline: UiTimeline

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            // 从UiTimeline获取关联的用户
            if let content = timeline.content as? UiTimelineItemContentStatus,
               let user = content.user
            {
                // 作者信息
                HStack {
                    UserAvatarView(imageUrl: user.avatar)

                    VStack(alignment: .leading, spacing: 2) {
                        Text(user.name.raw)
                            .font(.headline)

                        Text("@\(user.handle)")
                            .font(.subheadline)
                            .foregroundColor(.gray)
                    }

                    Spacer()

                    Text(formatDate(content.createdAt))
                        .font(.caption)
                        .foregroundColor(.gray)
                }

                // 内容
                Text(content.content.raw)
                    .font(.body)

                // 媒体内容（如果有）
                if !content.images.isEmpty {
                    ScrollView(.horizontal, showsIndicators: false) {
                        HStack(spacing: 8) {
                            ForEach(0 ..< content.images.count, id: \.self) { index in
                                let media = content.images[index]
                                AsyncImage(url: URL(string: media.url)) { phase in
                                    if let image = phase.image {
                                        image
                                            .resizable()
                                            .aspectRatio(contentMode: .fill)
                                    } else if phase.error != nil {
                                        Image(systemName: "photo")
                                            .foregroundColor(.gray)
                                    } else {
                                        ProgressView()
                                    }
                                }
                                .frame(width: 200, height: 150)
                                .cornerRadius(8)
                            }
                        }
                    }
                }

                // 交互按钮
                HStack(spacing: 24) {
                    Button(action: {}) {
                        Label("0", systemImage: "bubble.left")
                    }
                    .buttonStyle(.plain)

                    Button(action: {}) {
                        Label("0", systemImage: "arrow.2.squarepath")
                    }
                    .buttonStyle(.plain)

                    Button(action: {}) {
                        Label("0", systemImage: "heart")
                    }
                    .buttonStyle(.plain)
                }
                .padding(.top, 4)
            } else {
                Text("无法显示该内容")
                    .foregroundColor(.secondary)
            }
        }
        .padding(.vertical, 8)
    }

    // 格式化日期的辅助方法
    private func formatDate(_: Any?) -> String {
        "刚刚" // 简化实现
    }
}

// MARK: - 列表头部视图

struct ListHeaderView: View {
    let list: UiList

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack {
                VStack(alignment: .leading) {
                    Text(list.title)
                        .font(.title2)
                        .fontWeight(.bold)

                    if let description = list.description_, !description.isEmpty {
                        Text(description)
                            .font(.body)
                            .foregroundColor(.secondary)
                    }
                }

                Spacer()

                if false { // list.isPrivate 不存在，暂时使用固定值
                    Image(systemName: "lock.fill")
                        .foregroundColor(.secondary)
                }
            }

            HStack {
                if let creator = list.creator {
                    Text("创建者: \(creator.name.raw)")
                        .font(.caption)
                } else {
                    Text("创建者: 未知")
                        .font(.caption)
                }

                Spacer()

                Text("0 成员") // list.memberCount 不存在，使用固定值
                    .font(.caption)
            }
            .foregroundColor(.secondary)
            .padding(.top, 4)
        }
    }
}
