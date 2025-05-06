import Generated
import Kingfisher
import shared
import SwiftUI

enum ListGradientGenerator {
    static let gradients: [[Color]] = [
        [.blue.opacity(0.7), .purple.opacity(0.5)],
        [.green.opacity(0.6), .blue.opacity(0.4)],
        [.purple.opacity(0.6), .pink.opacity(0.4)],
        [.orange.opacity(0.6), .yellow.opacity(0.4)],
        [.teal.opacity(0.6), .blue.opacity(0.4)],
    ]

    static func getGradientIndex(for id: String) -> Int {
        let hashValue = id.hashValue
        let positiveHash = abs(hashValue)
        return positiveHash % gradients.count
    }

    static func getGradient(for id: String) -> [Color] {
        let index = getGradientIndex(for: id)
        return gradients[index]
    }
}

struct ListIconView: View {
    let imageUrl: String
    let size: CGFloat
    let listId: String
    let cornerRadius: CGFloat

    init(imageUrl: String, size: CGFloat = 40, listId: String = "", cornerRadius: CGFloat? = nil) {
        self.imageUrl = imageUrl
        self.size = size
        self.listId = listId

        self.cornerRadius = cornerRadius ?? size / 5
    }

    var body: some View {
        if !imageUrl.isEmpty, let url = URL(string: imageUrl) {
            KFImage(url)
                .placeholder {
                    ProgressView()
                        .frame(width: size, height: size)
                }
                .onFailure { _ in
                }
                .resizable()
                .aspectRatio(contentMode: .fill)
                .frame(width: size, height: size)
                .clipShape(RoundedRectangle(cornerRadius: cornerRadius))
        } else {
            defaultIconView
        }
    }

    private var defaultIconView: some View {
        ZStack {
            RoundedRectangle(cornerRadius: cornerRadius)
                .fill(
                    LinearGradient(
                        gradient: Gradient(colors: ListGradientGenerator.getGradient(for: listId)),
                        startPoint: .topLeading,
                        endPoint: .bottomTrailing
                    )
                )

            Image(systemName: "list.bullet")
                .resizable()
                .scaledToFit()
                .frame(width: size * 0.45, height: size * 0.45)
                .foregroundColor(.white.opacity(0.8))
        }
        .frame(width: size, height: size)
    }
}
