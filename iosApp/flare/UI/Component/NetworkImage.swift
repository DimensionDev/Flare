import SwiftUI
import Kingfisher

struct NetworkImage: View {
    let data: URL?
    let placeholder: URL?
    let customHeader: [String: String]?
    let contentMode: SwiftUI.ContentMode
    var body: some View {
        if data?.absoluteString.hasSuffix(".gif") == true {
            KFAnimatedImage(data)
                .fade(duration: 0.25)
                .requestModifier({ request in
                    if let customHeader {
                        for (key, value) in customHeader {
                            request.setValue(value, forHTTPHeaderField: key)
                        }
                    }
                })
                .placeholder {
                    if let placeholder {
                        NetworkImage(data: placeholder, customHeader: customHeader)
                    } else {
                        Rectangle()
                            .fill(.placeholder)
                            .redacted(reason: .placeholder)
                    }
                }
                .cancelOnDisappear(true)
    //            .if(placeholder == nil, if: { image in
    //                image.loadTransition(.opacity)
    //            }, else: { image in
    //                image
    //            })
    //            .resizable()
                .aspectRatio(contentMode: contentMode)
        } else {
            KFImage(data)
                .resizable()
                .fade(duration: 0.25)
                .requestModifier({ request in
                    if let customHeader {
                        for (key, value) in customHeader {
                            request.setValue(value, forHTTPHeaderField: key)
                        }
                    }
                })
                .placeholder {
                    if let placeholder {
                        NetworkImage(data: placeholder, customHeader: customHeader)
                    } else {
                        Rectangle()
                            .fill(.placeholder)
                            .redacted(reason: .placeholder)
                    }
                }
                .cancelOnDisappear(true)
    //            .if(placeholder == nil, if: { image in
    //                image.loadTransition(.opacity)
    //            }, else: { image in
    //                image
    //            })
    //            .resizable()
                .aspectRatio(contentMode: contentMode)
        }
    }
}

extension NetworkImage {
    init(data: String?, customHeader: [String: String]? = nil, contentMode: SwiftUI.ContentMode = .fill) {
        self.init(data: data.flatMap(URL.init(string:)), placeholder: nil, customHeader: customHeader, contentMode: contentMode)
    }
    init(data: String, customHeader: [String: String]? = nil, contentMode: SwiftUI.ContentMode = .fill) {
        self.init(data: .init(string: data), placeholder: nil, customHeader: customHeader, contentMode: contentMode)
    }
    init(data: String, placeholder: String, customHeader: [String: String]? = nil, contentMode: SwiftUI.ContentMode = .fill) {
        self.init(data: .init(string: data), placeholder: .init(string: placeholder), customHeader: customHeader, contentMode: contentMode)
    }
    init(data: URL?, contentMode: SwiftUI.ContentMode = .fill) {
        self.init(data: data, placeholder: nil, customHeader: nil, contentMode: contentMode)
    }
    init(data: URL?, customHeader: [String: String]?, contentMode: SwiftUI.ContentMode = .fill) {
        self.init(data: data, placeholder: nil, customHeader: customHeader, contentMode: contentMode)
    }
}
