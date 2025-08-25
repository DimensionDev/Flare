//
//  ProfileHeaderSwiftUIViewV2.swift
//  iosApp
//
//  Created by Flare Team on 2025-08-22.
//  Profile SwiftUI重构 - Header组件
//  借鉴IceCubesApp的Header设计，保持Flare现有数据结构
//

import Kingfisher
import MarkdownUI
import shared
import SwiftUI

/// Profile Header组件V2
/// 借鉴IceCubesApp的AccountDetailHeaderView设计
/// 使用Flare现有的ProfileUserInfo数据结构
struct ProfileHeaderSwiftUIViewV2: View {
    // MARK: - Properties

    /// 用户信息（使用现有ProfileUserInfo结构）
    let userInfo: ProfileUserInfo
    /// 滚动代理，用于滚动到TabBar
    let scrollProxy: ScrollViewProxy?
    /// Profile Presenter，用于关注按钮等交互
    let presenter: ProfilePresenter?

    // MARK: - Environment

    /// Flare主题系统
    @Environment(FlareTheme.self) private var theme

    // MARK: - Body

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            // 背景图片区域
            backgroundImageSection

            // 用户信息区域
            userInfoSection

            // 用户名和简介区域
            userDetailsSection
        }
        .background(theme.primaryBackgroundColor)
    }
}

// MARK: - View Components

extension ProfileHeaderSwiftUIViewV2 {
    /// 背景图片区域
    private var backgroundImageSection: some View {
        ZStack(alignment: .bottomTrailing) {
            Rectangle()
                .frame(height: 150)
                .overlay {
                    if let bannerUrl = userInfo.profile.banner, !bannerUrl.isEmpty {
                        // 使用KFImage专业图片加载，配置与UIKit版本一致
                        KFImage(URL(string: bannerUrl))
                            .setProcessor(DownsamplingImageProcessor(size: CGSize(width: UIScreen.main.bounds.width * 2, height: 300)))
                            .scaleFactor(UIScreen.main.scale)
                            .memoryCacheExpiration(.seconds(180))
                            .diskCacheExpiration(.days(3))
                            .placeholder {
                                // 背景图片加载占位
                                LinearGradient(
                                    colors: [theme.tintColor.opacity(0.3), theme.tintColor.opacity(0.1)],
                                    startPoint: .topLeading,
                                    endPoint: .bottomTrailing
                                )
                            }
                            .resizable()
                            .scaledToFill()
                            .overlay {
                                // 添加渐变遮罩确保内容可读性
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
                        // 默认渐变背景
                        LinearGradient(
                            colors: [theme.tintColor.opacity(0.3), theme.tintColor.opacity(0.1)],
                            startPoint: .topLeading,
                            endPoint: .bottomTrailing
                        )
                    }
                }
                .clipped()

            // 关系状态标签（如果有）
            if let relation = userInfo.relation {
                relationshipStatusLabel(relation)
                    .padding(8)
            }
        }
    }

    /// 用户信息区域（头像 + 统计信息 + 关注按钮）
    private var userInfoSection: some View {
        HStack(alignment: .top, spacing: 12) {
            // 头像
            avatarView
                .offset(y: -40)

            Spacer()

            VStack(alignment: .trailing, spacing: 8) {
                // 关注按钮（仅非本人Profile显示）
                if !userInfo.isMe {
                    followButtonView
                }

                // 统计信息
                statisticsView
            }
            .padding(.top, 8)
        }
        .padding(.horizontal, 16)
    }

    /// 用户名和简介区域
    private var userDetailsSection: some View {
        VStack(alignment: .leading, spacing: 8) {
            // 显示名称（使用Markdown渲染）
            Markdown(userInfo.profile.name.markdown)
                .markdownInlineImageProvider(.emoji)
                .font(.title2)
                .fontWeight(.bold)
                .foregroundColor(theme.labelColor)

            // 用户名和标记
            HStack(spacing: 8) {
                Text("@\(userInfo.profile.handleWithoutFirstAt)")
                    .font(.subheadline)
                    .foregroundColor(theme.labelColor.opacity(0.6))

                // 用户标记系统
                userMarksView
            }

            // 用户简介（使用Markdown渲染）
            if let description = userInfo.profile.description_?.markdown, !description.isEmpty {
                Markdown(description)
                    .markdownInlineImageProvider(.emoji)
                    .font(.body)
                    .foregroundColor(theme.labelColor)
            }

            // 自定义字段（如果有）
            if !userInfo.fields.isEmpty {
                customFieldsView
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(.horizontal, 16)
        .padding(.bottom, 16)
    }

    /// 头像视图（使用KFImage专业缓存）
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

    /// 关注按钮视图
    private var followButtonView: some View {
        FollowButtonView(
            presenter: presenter,
            userKey: userInfo.profile.key
        )
        .frame(width: 80, height: 30)
    }

    /// 统计信息视图
    private var statisticsView: some View {
        HStack(spacing: 20) {
            StatisticButton(
                title: "Posts",
                count: Int(userInfo.profile.matrices.statusesCount)
            ) {
                // 点击滚动到TabBar位置
                scrollToTabBar()
            }

            // 使用格式化的关注数和粉丝数
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
                // TODO: 导航到Following页面
                FlareLog.debug("📱 [ProfileHeaderV2] 点击Following")
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
                // TODO: 导航到Followers页面
                FlareLog.debug("📱 [ProfileHeaderV2] 点击Followers")
            }
        }
    }

    /// 用户标记视图
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

    /// 获取标记对应的图标
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

    /// 自定义字段视图
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

    /// 关系状态标签
    private func relationshipStatusLabel(_ relation: UiRelation) -> some View {
        // 根据关系状态显示不同的标签
        let statusText = getRelationshipStatusText(relation)

        return Text(statusText)
            .font(.caption)
            .fontWeight(.semibold)
            .padding(.horizontal, 8)
            .padding(.vertical, 4)
            .background(.ultraThinMaterial)
            .cornerRadius(4)
    }

    /// 获取关系状态文本
    private func getRelationshipStatusText(_: UiRelation) -> String {
        // 根据UiRelation的具体实现返回对应文本
        // 这里需要根据实际的UiRelation结构来实现
        "Relationship Status" // 占位文本
    }

    /// 滚动到TabBar位置
    private func scrollToTabBar() {
        guard let proxy = scrollProxy else { return }

        withAnimation(.easeInOut(duration: 0.5)) {
            proxy.scrollTo("tabbar", anchor: .top)
        }

        FlareLog.debug("📜 [ProfileHeaderV2] 滚动到TabBar")
    }
}

/// 统计信息按钮组件
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

    /// 格式化数字显示
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
