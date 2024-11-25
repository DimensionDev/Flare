import SwiftUI
import shared

struct ProfileHeaderView: View {
    let user: UiProfile
    let relation: UiState<UiRelation>
    let isMe: UiState<Bool>
    let onFollow: (MicroBlogKey, UiRelation) -> Void
    
    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            ProfileBannerView(bannerUrl: user.bannerUrl)
            
            HStack(alignment: .top) {
                ProfileAvatarView(avatarUrl: user.avatarUrl)
                    .offset(y: -40)
                
                Spacer()
                
                if case .success(let isMe) = onEnum(of: isMe), !isMe {
                    ProfileFollowButton(relation: relation) {
                        if case .success(let relationData) = onEnum(of: relation) {
                            onFollow(user.key, relationData)
                        }
                    }
                }
            }
            .padding(.horizontal)
            
            ProfileInfoView(user: user)
                .padding(.horizontal)
            
            Divider()
        }
    }
}

// MARK: - Banner View
private struct ProfileBannerView: View {
    let bannerUrl: String?
    
    var body: some View {
        if let bannerUrl = bannerUrl {
            AsyncImage(url: URL(string: bannerUrl)) { phase in
                switch phase {
                case .empty:
                    defaultBannerView
                case .success(let image):
                    image
                        .resizable()
                        .aspectRatio(contentMode: .fill)
                case .failure:
                    defaultBannerView
                @unknown default:
                    defaultBannerView
                }
            }
            .frame(height: 150)
            .clipped()
        }
    }
    
    private var defaultBannerView: some View {
        Color.gray.opacity(0.3)
    }
}

// MARK: - Avatar View
private struct ProfileAvatarView: View {
    let avatarUrl: String
    
    var body: some View {
        AsyncImage(url: URL(string: avatarUrl)) { phase in
            switch phase {
            case .empty:
                defaultAvatarView
            case .success(let image):
                image
                    .resizable()
                    .aspectRatio(contentMode: .fill)
            case .failure:
                defaultAvatarView
            @unknown default:
                defaultAvatarView
            }
        }
        .frame(width: 80, height: 80)
        .clipShape(Circle())
        .overlay(Circle().stroke(Color(.systemBackground), width: 4))
        .shadow(radius: 2)
    }
    
    private var defaultAvatarView: some View {
        Color.gray.opacity(0.3)
    }
}

// MARK: - Follow Button
private struct ProfileFollowButton: View {
    let relation: UiState<UiRelation>
    let onTap: () -> Void
    @State private var isPressed = false
    
    var body: some View {
        switch onEnum(of: relation) {
        case .loading:
            ProgressView()
        case .success(let relation):
            Button(action: onTap) {
                Text(relation.following ? "Following" : "Follow")
                    .font(.subheadline.bold())
                    .foregroundColor(buttonTextColor(isFollowing: relation.following))
                    .padding(.horizontal, 16)
                    .padding(.vertical, 8)
                    .background(buttonBackground(isFollowing: relation.following))
                    .overlay(buttonBorder(isFollowing: relation.following))
            }
            .buttonStyle(.plain)
            .clipShape(RoundedRectangle(cornerRadius: 16))
            .scaleEffect(isPressed ? 0.95 : 1.0)
            .animation(.easeInOut(duration: 0.1), value: isPressed)
            .onLongPressGesture(minimumDuration: .infinity, maximumDistance: .infinity,
                               pressing: { pressing in
                withAnimation {
                    isPressed = pressing
                }
            }, perform: {})
        default:
            EmptyView()
        }
    }
    
    private func buttonTextColor(isFollowing: Bool) -> Color {
        isFollowing ? .primary : .white
    }
    
    private func buttonBackground(isFollowing: Bool) -> Color {
        isFollowing ? .clear : .accentColor
    }
    
    private func buttonBorder(isFollowing: Bool) -> some View {
        RoundedRectangle(cornerRadius: 16)
            .stroke(Color.primary, lineWidth: isFollowing ? 1 : 0)
    }
}

// MARK: - User Info View
private struct ProfileInfoView: View {
    let user: UiProfile
    
    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text(user.name)
                .font(.title3.bold())
            
            Text("@\(user.userName)")
                .font(.subheadline)
                .foregroundColor(.secondary)
            
            if let description = user.description {
                Text(description)
                    .font(.body)
            }
            
            HStack(spacing: 24) {
                StatView(count: user.followingCount, label: "Following")
                StatView(count: user.followersCount, label: "Followers")
            }
            .padding(.top, 4)
        }
    }
}

// MARK: - Stat View
private struct StatView: View {
    let count: Int32
    let label: String
    
    var body: some View {
        HStack(spacing: 4) {
            Text("\(count)")
                .font(.subheadline.bold())
            Text(label)
                .font(.subheadline)
                .foregroundColor(.secondary)
        }
    }
}
