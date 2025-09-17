import SwiftUI
import Kingfisher

struct NetworkImage: View {
    let data: URL?
    var body: some View {
        KFImage
            .url(data)
            .placeholder {
                Rectangle()
                    .fill(.placeholder)
                    .redacted(reason: .placeholder)
            }
            .resizable()
            .scaledToFill()
    }
}

extension NetworkImage {
    init(data: String) {
        self.init(data: .init(string: data))
    }
}
