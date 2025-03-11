import shared
import SwiftUI

struct ListIconView: View {
    let imageUrl: String
    let size: CGFloat

    init(imageUrl: String, size: CGFloat = 40) {
        self.imageUrl = imageUrl
        self.size = size
    }

    var body: some View {
        AsyncImage(url: URL(string: imageUrl)) { phase in
            switch phase {
            case .empty:
                ProgressView()
                    .frame(width: size, height: size)
            case let .success(image):
                image.resizable()
                    .aspectRatio(contentMode: .fill)
            case .failure:
                Image(systemName: "person.circle.fill")
                    .resizable()
                    .foregroundColor(.gray.opacity(0.3))
            @unknown default:
                EmptyView()
            }
        }
        .frame(width: size, height: size)
        .clipShape(Circle())
    }
}
