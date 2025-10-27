import SwiftUI
import Kingfisher

struct NetworkImage: View {
    let data: URL?
    let placeholder: URL?
    let customHeader: [String: String]?
    var body: some View {
        KFAnimatedImage
            .url(data)
            .requestModifier({ request in
                if let customHeader {
                    for (key, value) in customHeader {
                        request.setValue(value, forHTTPHeaderField: key)
                    }
                }
            })
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
    init(data: String, customHeader: [String: String]? = nil) {
        self.init(data: .init(string: data), placeholder: nil, customHeader: customHeader)
    }
    init(data: String, placeholder: String) {
        self.init(data: .init(string: data), placeholder: .init(string: placeholder), customHeader: nil)
    }
    init(data: URL?) {
        self.init(data: data, placeholder: nil, customHeader: nil)
    }
}
