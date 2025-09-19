import SwiftUI
import Kingfisher

struct NetworkImage: View {
    let data: URL?
    let placeholder: URL?
    var body: some View {
        KFAnimatedImage
            .url(data)
            .placeholder {
                if let placeholder {
                    NetworkImage(data: placeholder)
                } else {
                    Rectangle()
                        .fill(.placeholder)
                        .redacted(reason: .placeholder)
                }
            }
            .if(placeholder == nil, if: { image in
                image.loadTransition(.blurReplace)
            }, else: { image in
                image
            })
//            .resizable()
            .scaledToFill()
    }
}

extension NetworkImage {
    init(data: String) {
        self.init(data: .init(string: data))
    }
    init(data: String, placeholder: String) {
        self.init(data: .init(string: data), placeholder: .init(string: placeholder))
    }
    init(data: URL?) {
        self.init(data: data, placeholder: nil)
    }
}
