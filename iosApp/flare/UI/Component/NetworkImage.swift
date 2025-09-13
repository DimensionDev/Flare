import SwiftUI
import Kingfisher

struct NetworkImage: View {
    let data: String
    var body: some View {
        KFImage
            .url(.init(string: data))
            .resizable()
            .scaledToFill()
    }
}
