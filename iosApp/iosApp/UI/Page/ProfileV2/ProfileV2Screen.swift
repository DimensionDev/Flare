import SwiftUI

struct ProfileV2Screen: View {
    let toProfileMedia: (MicroBlogKey) -> Void
    let accountType: AccountType
    let userKey: MicroBlogKey?

    @State private var refreshCount: Int = 0

    init(accountType: AccountType, userKey: MicroBlogKey?, toProfileMedia: @escaping (MicroBlogKey) -> Void) {
        self.toProfileMedia = toProfileMedia
        self.accountType = accountType
        self.userKey = userKey
    }

    var body: some View {
        NavigationView {
            ProfileV2HeaderPageScrollView(displaysSymbols: false) {
                ProfileV2HeaderView()
            } labels: {
                ProfileV2PageLabel(title: "Posts", symbolImage: "square.grid.3x3")
                ProfileV2PageLabel(title: "Timeline", symbolImage: "list.bullet")
                ProfileV2PageLabel(title: "Media", symbolImage: "photo.stack")
            } pages: {
                ProfileV2PostsTabView()
                ProfileV2TimelineTabView()
                ProfileV2MediaTabView()
            } onRefresh: { index in
                await handleRefresh(for: index)
            }
        }
    }

    private func handleRefresh(for index: Int) async {
        print("🔄 [Profile V2] Refresh tab \(index)")
        refreshCount += 1

        try? await Task.sleep(nanoseconds: 1_000_000_000)

        let tabNames = ["Posts", "Timeline", "Media"]
        let tabName = index < tabNames.count ? tabNames[index] : "Unknown"
        print("✅ [Profile V2] \(tabName) tab refreshed (count: \(refreshCount))")
    }
}

struct ProfileV2HeaderView: View {
    var body: some View {
        VStack(spacing: 16) {
            Circle()
                .fill(.blue.gradient)
                .frame(width: 80, height: 80)
                .overlay {
                    Text("👤")
                        .font(.largeTitle)
                }

            VStack(spacing: 8) {
                Text("Profile V2 User")
                    .font(.title2)
                    .fontWeight(.bold)

                Text("@profilev2user")
                    .font(.subheadline)
                    .foregroundColor(.secondary)

                Text("This is Profile V2 test user bio. Testing the new Instagram-style scrolling architecture.")
                    .font(.caption)
                    .multilineTextAlignment(.center)
                    .foregroundColor(.secondary)
                    .padding(.horizontal)
            }

            HStack(spacing: 24) {
                ProfileV2StatView(title: "Posts", count: "123")
                ProfileV2StatView(title: "Followers", count: "1.2K")
                ProfileV2StatView(title: "Following", count: "456")
            }

            HStack(spacing: 12) {
                Button("Follow") {
                    print("Follow button tapped")
                }
                .buttonStyle(.borderedProminent)

                Button("Message") {
                    print("Message button tapped")
                }
                .buttonStyle(.bordered)
            }
        }
        .padding()
        .background(
            RoundedRectangle(cornerRadius: 20)
                .fill(.blue.gradient.opacity(0.1))
        )
        .padding(.horizontal)
    }
}

struct ProfileV2StatView: View {
    let title: String
    let count: String

    var body: some View {
        VStack(spacing: 4) {
            Text(count)
                .font(.headline)
                .fontWeight(.bold)

            Text(title)
                .font(.caption)
                .foregroundColor(.secondary)
        }
    }
}

struct ProfileV2PostsTabView: View {
    var body: some View {
        ProfileV2DummyTabView(
            color: .red,
            title: "Posts",
            icon: "square.grid.3x3",
            count: 20
        )
    }
}

struct ProfileV2TimelineTabView: View {
    var body: some View {
        ProfileV2DummyTabView(
            color: .green,
            title: "Timeline",
            icon: "list.bullet",
            count: 15
        )
    }
}

struct ProfileV2MediaTabView: View {
    var body: some View {
        ProfileV2DummyTabView(
            color: .orange,
            title: "Media",
            icon: "photo.stack",
            count: 10
        )
    }
}

struct ProfileV2DummyTabView: View {
    let color: Color
    let title: String
    let icon: String
    let count: Int

    var body: some View {
        LazyVStack(spacing: 12) {
            HStack {
                Image(systemName: icon)
                    .foregroundColor(color)
                Text("\(title) Content")
                    .font(.headline)
                    .foregroundColor(color)
                Spacer()
                Text("\(count) items")
                    .font(.caption)
                    .foregroundColor(.secondary)
            }
            .padding(.horizontal)
            .padding(.top)

            // 内容区域（颜色方块）
            ForEach(0 ..< count, id: \.self) { index in
                RoundedRectangle(cornerRadius: 12)
                    .fill(color.gradient)
                    .frame(height: 60)
                    .overlay {
                        VStack {
                            Text("\(title) Item \(index + 1)")
                                .font(.subheadline)
                                .fontWeight(.medium)
                                .foregroundColor(.white)

                            Text("Tap to interact")
                                .font(.caption)
                                .foregroundColor(.white.opacity(0.8))
                        }
                    }
                    .onTapGesture {
                        print("🔘 [Profile V2] Tapped \(title) item \(index + 1)")
                    }
            }
        }
        .padding(.horizontal)
    }
}
