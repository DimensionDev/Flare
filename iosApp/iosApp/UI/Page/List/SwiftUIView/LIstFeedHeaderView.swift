import Kingfisher
import shared
import SwiftUI

@MainActor
struct ListFeedHeaderView {
    /// header banner background
    static func ListFeedHeaderBackgroundView(listInfo: UiList, gradientColors: [Color]) -> some View {
        ZStack {
            if let avatarString = listInfo.avatar,
               !avatarString.isEmpty,
               let url = URL(string: avatarString)
            {
                KFImage(url)
                    .resizable()
                    .aspectRatio(contentMode: .fit)
                    .frame(maxWidth: .infinity, alignment: .center)
                    .clipped()
                    .overlay(Color.black.opacity(0.2))
            } else {
                ListFeedGradientBackground(gradientColors: gradientColors, isListView: true)
            }
        }
    }

    /// banner background color
    static func ListFeedGradientBackground(gradientColors: [Color], isListView: Bool = true) -> some View {
        ZStack {
            LinearGradient(
                gradient: Gradient(colors: gradientColors),
                startPoint: .topLeading,
                endPoint: .bottomTrailing
            )
            .frame(height: 140)

            ForEach(0 ..< 6, id: \.self) { _ in
                Circle()
                    .fill(Color.white.opacity(Double.random(in: 0.05 ... 0.15)))
                    .frame(width: CGFloat.random(in: 30 ... 80),
                           height: CGFloat.random(in: 30 ... 80))
                    .offset(x: CGFloat.random(in: -150 ... 150),
                            y: CGFloat.random(in: -100 ... 100))
                    .blur(radius: CGFloat.random(in: 3 ... 8))
            }

            if isListView {
                Image(systemName: "list.bullet.rectangle")
                    .resizable()
                    .scaledToFit()
                    .frame(width: 80, height: 80)
                    .foregroundColor(.white.opacity(0.8))
                    .shadow(color: .black.opacity(0.3), radius: 5, x: 0, y: 2)
            } else {
                Image(systemName: "square.grid.2x2")
                    .resizable()
                    .scaledToFit()
                    .frame(width: 80, height: 80)
                    .foregroundColor(.white.opacity(0.8))
                    .shadow(color: .black.opacity(0.3), radius: 5, x: 0, y: 2)
            }
        }
    }

    /// creater info
    static func ListFeedCreatorProfileView(creator: UiUserV2) -> some View {
        HStack(spacing: 8) {
            if let url = URL(string: creator.avatar) {
                KFImage(url)
                    .placeholder {
                        Circle()
                            .fill(Color.gray.opacity(0.2))
                    }
                    .resizable()
                    .aspectRatio(contentMode: .fill)
                    .frame(width: 45, height: 45)
                    .clipShape(Circle())
            } else {
                Image(systemName: "person.circle.fill")
                    .resizable()
                    .frame(width: 45, height: 45)
                    .foregroundColor(.gray)
            }

            VStack(alignment: .leading, spacing: 2) {
                Text(creator.name.raw)
                    .font(.headline)
                    .lineLimit(1)

                Text("\(creator.handleWithoutFirstAt)")
                    .font(.subheadline)
                    .foregroundColor(.gray)
                    .lineLimit(1)
            }
        }
    }

    /// unknown
    static func ListFeedUnknownCreatorView() -> some View {
        HStack(spacing: 8) {
            Image(systemName: "person.circle.fill")
                .resizable()
                .frame(width: 45, height: 45)
                .foregroundColor(.gray)

            Text("")
                .font(.headline)
        }
    }

    ///  members count
    static func ListFeedMemberCountsView(count: Int64, isListView: Bool = true) -> some View {
        HStack(spacing: 16) {
            VStack(alignment: .center, spacing: 2) {
                Text("\(Int(count))")
                    .font(.headline)
                    .bold()
                Text(isListView ? "members" : "subscribers")
                    .font(.caption)
                    .foregroundColor(.gray)
            }
        }
    }

    static func getGradientColors(for id: String) -> [Color] {
        ListGradientGenerator.getGradient(for: id)
    }
}

@MainActor
extension View {
    func listFeedHeaderStyle() -> some View {
        listRowInsets(EdgeInsets())
            .listRowSeparator(.hidden)
    }

    func listFeedContentStyle() -> some View {
        listRowInsets(EdgeInsets(top: 0, leading: 16, bottom: 0, trailing: 16))
            .listRowSeparator(.hidden)
    }
}
