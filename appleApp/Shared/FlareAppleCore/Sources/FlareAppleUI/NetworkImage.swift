import SwiftUI
import Kingfisher

public struct NetworkImage: View {
    private let data: URL?
    private let placeholder: URL?
    private let customHeader: [String: String]?

    public var body: some View {
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
                .scaledToFill()
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
                .scaledToFill()
        }
    }
}

public extension NetworkImage {
    init(data: String?, customHeader: [String: String]? = nil) {
        self.init(data: data.flatMap(URL.init(string:)), placeholder: nil, customHeader: customHeader)
    }
    init(data: String, customHeader: [String: String]? = nil) {
        self.init(data: .init(string: data), placeholder: nil, customHeader: customHeader)
    }
    init(data: String, placeholder: String, customHeader: [String: String]? = nil) {
        self.init(data: .init(string: data), placeholder: .init(string: placeholder), customHeader: customHeader)
    }
    init(data: URL?) {
        self.init(data: data, placeholder: nil, customHeader: nil)
    }
    init(data: URL?, customHeader: [String: String]?) {
        self.init(data: data, placeholder: nil, customHeader: customHeader)
    }
}
