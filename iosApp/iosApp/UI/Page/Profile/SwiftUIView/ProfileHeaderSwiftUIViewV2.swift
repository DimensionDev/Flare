import Kingfisher
import MarkdownUI
import shared
import SwiftUI

struct ProfileHeaderSwiftUIViewV2: View {
    let userInfo: ProfileUserInfo

    let scrollProxy: ScrollViewProxy?

    let presenter: ProfilePresenter?

    @Environment(FlareTheme.self) private var theme
    @Environment(FlareRouter.self) private var router

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            backgroundImageSection

            userInfoSection

            userDetailsSection
        }
        .background(theme.primaryBackgroundColor)
    }
}

extension ProfileHeaderSwiftUIViewV2 {
    private var backgroundImageSection: some View {
        ZStack(alignment: .bottomTrailing) {
            Rectangle()
                .frame(height: 150)
                .overlay {
                    if let bannerUrl = userInfo.profile.banner, !bannerUrl.isEmpty {
                        KFImage(URL(string: bannerUrl))
                            .setProcessor(DownsamplingImageProcessor(size: CGSize(width: UIScreen.main.bounds.width * 2, height: 300)))
                            .scaleFactor(UIScreen.main.scale)
                            .memoryCacheExpiration(.seconds(180))
                            .diskCacheExpiration(.days(3))
                            .placeholder {
                                LinearGradient(
                                    colors: [theme.tintColor.opacity(0.3), theme.tintColor.opacity(0.1)],
                                    startPoint: .topLeading,
                                    endPoint: .bottomTrailing
                                )
                            }
                            .resizable()
                            .scaledToFill()
                            .overlay {
                                LinearGradient(
                                    colors: [
                                        .black.opacity(0.2),
                                        .clear
                                    ],
                                    startPoint: .bottom,
                                    endPoint: .top
                                )
                            }
                    } else {
                        LinearGradient(
                            colors: [theme.tintColor.opacity(0.3), theme.tintColor.opacity(0.1)],
                            startPoint: .topLeading,
                            endPoint: .bottomTrailing
                        )
                    }
                }
                .clipped()
        }
    }

    private var userInfoSection: some View {
        HStack(alignment: .top, spacing: 12) {
            avatarView
                .offset(y: -40)

            Spacer()

            VStack(alignment: .trailing, spacing: 8) {
                if !userInfo.isMe {
                    followButtonView
                }

                statisticsView
            }
            .padding(.top, 8)
        }
        .padding(.horizontal, 16)
    }

    private var userDetailsSection: some View {
        VStack(alignment: .leading, spacing: 8) {
            Markdown(userInfo.profile.name.markdown)
                .markdownInlineImageProvider(.emoji)
                .font(.title2)
                .fontWeight(.bold)
                .foregroundColor(theme.labelColor)

            HStack(spacing: 8) {
                Text("@\(userInfo.profile.handleWithoutFirstAt)")
                    .font(.subheadline)
                    .foregroundColor(theme.labelColor.opacity(0.6))

                userMarksView
            }

            if let description = userInfo.profile.description_?.markdown, !description.isEmpty {
                Markdown(description)
                    .markdownInlineImageProvider(.emoji)
                    .font(.body)
                    .foregroundColor(theme.labelColor)
            }

            if !userInfo.fields.isEmpty {
                customFieldsView
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(.horizontal, 16)
        .padding(.bottom, 16)
    }

    private var avatarView: some View {
        KFImage(URL(string: userInfo.profile.avatar))
            .setProcessor(DownsamplingImageProcessor(size: CGSize(width: 160, height: 160)))
            .scaleFactor(UIScreen.main.scale)
            .downloadPriority(0.7)
            .backgroundDecode()
            .memoryCacheExpiration(.seconds(600))
            .diskCacheExpiration(.days(7))
            .fade(duration: 0.2)
            .placeholder {
                Circle()
                    .fill(theme.secondaryBackgroundColor)
                    .overlay(
                        Image(systemName: "person.fill")
                            .foregroundColor(theme.labelColor.opacity(0.6))
                            .font(.title)
                    )
            }
            .resizable()
            .scaledToFill()
            .frame(width: 80, height: 80)
            .clipShape(Circle())
            .overlay(
                Circle()
                    .stroke(theme.primaryBackgroundColor, lineWidth: 4)
            )
    }

    private var followButtonView: some View {
        FollowButtonView(
            presenter: presenter,
            userKey: userInfo.profile.key
        )
        .frame(width: 80, height: 30)
    }

    private var statisticsView: some View {
        HStack(spacing: 20) {
            StatisticButton(
                title: "Posts",
                count: Int(userInfo.profile.matrices.statusesCount)
            ) {
                scrollToTabBar()
            }

            VStack(spacing: 2) {
                Text(userInfo.followCount)
                    .font(.headline)
                    .fontWeight(.semibold)
                    .foregroundColor(theme.labelColor)
                Text(NSLocalizedString("following_title", comment: ""))
                    .font(.caption)
                    .foregroundColor(theme.labelColor.opacity(0.6))
            }
            .onTapGesture {
                let accountType = UserManager.shared.getCurrentAccountType() ?? AccountTypeGuest()
                router.navigate(to: .following(
                    accountType: accountType,
                    userKey: userInfo.profile.key,
                    userName: userInfo.profile.name.raw
                ))
                FlareLog.debug("📱 [ProfileHeaderV2] 导航到Following列表")
            }

            VStack(spacing: 2) {
                Text(userInfo.fansCount)
                    .font(.headline)
                    .fontWeight(.semibold)
                    .foregroundColor(theme.labelColor)
                Text(NSLocalizedString("fans_title", comment: ""))
                    .font(.caption)
                    .foregroundColor(theme.labelColor.opacity(0.6))
            }
            .onTapGesture {
                let accountType = UserManager.shared.getCurrentAccountType() ?? AccountTypeGuest()
                router.navigate(to: .followers(
                    accountType: accountType,
                    userKey: userInfo.profile.key,
                    userName: userInfo.profile.name.raw
                ))
                FlareLog.debug("📱 [ProfileHeaderV2] 导航到Followers列表")
            }
        }
    }

    private var userMarksView: some View {
        HStack(spacing: 4) {
            ForEach(userInfo.profile.mark, id: \.self) { mark in
                Image(systemName: markIcon(for: mark))
                    .foregroundColor(.gray.opacity(0.6))
                    .font(.caption)
                    .frame(width: 16, height: 16)
            }
        }
    }

    private func markIcon(for mark: UiProfile.Mark) -> String {
        switch mark {
        case .verified:
            "checkmark.circle.fill"
        case .locked:
            "lock.fill"
        case .bot:
            "cpu"
        case .cat:
            "cat"
        }
    }

    private var customFieldsView: some View {
        VStack(alignment: .leading, spacing: 4) {
            ForEach(Array(userInfo.fields.keys).sorted(), id: \.self) { (key: String) in
                if let value = userInfo.fields[key] {
                    HStack {
                        Text(key)
                            .font(.caption)
                            .foregroundColor(theme.labelColor.opacity(0.6))

                        Spacer()

                        Text(value.raw)
                            .font(.caption)
                            .foregroundColor(theme.labelColor)
                    }
                    .padding(.vertical, 2)
                }
            }
        }
        .padding(.top, 8)
    }

    private func scrollToTabBar() {
        guard let proxy = scrollProxy else { return }

        withAnimation(.easeInOut(duration: 0.5)) {
            proxy.scrollTo("tabbar", anchor: .top)
        }

        FlareLog.debug("📜 [ProfileHeaderV2] 滚动到TabBar")
    }
}

struct StatisticButton: View {
    let title: String
    let count: Int
    let action: () -> Void

    @Environment(FlareTheme.self) private var theme

    var body: some View {
        Button(action: action) {
            VStack(spacing: 2) {
                Text(formatCount(count))
                    .font(.headline)
                    .fontWeight(.bold)
                    .foregroundColor(theme.labelColor)

                Text(title)
                    .font(.caption)
                    .foregroundColor(theme.labelColor.opacity(0.6))
            }
        }
        .buttonStyle(.plain)
    }

    private func formatCount(_ count: Int) -> String {
        if count >= 1_000_000 {
            String(format: "%.1fM", Double(count) / 1_000_000.0)
        } else if count >= 1000 {
            String(format: "%.1fK", Double(count) / 1000.0)
        } else {
            "\(count)"
        }
    }
}
