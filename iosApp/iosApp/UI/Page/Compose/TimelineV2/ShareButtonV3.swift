import Awesome
import Generated
import JXPhotoBrowser
import Kingfisher
import MarkdownUI
import os.log
import SwiftDate
import SwiftUI
import UIKit

#if canImport(_Translation_SwiftUI)
    import Translation
#endif

enum MoreActionType {
    case sharePost
    case shareAsImage

    case showTextForSelection
    case copyText
    case copyMarkdown
    case copyTweetLink

    case copyMediaLink
    case copyMediaURLs
    case saveMedia

    case translate
    case openInBrowser
    case report
}

struct ShareButtonV3: View, Equatable {
    @Environment(\.isInCaptureMode) private var isInCaptureMode: Bool

    let item: TimelineItem
    let onMoreAction: (MoreActionType) -> Void

    @State private var isMenuContentReady = false

    static func == (lhs: ShareButtonV3, rhs: ShareButtonV3) -> Bool {
        lhs.item.id == rhs.item.id
    }

    private var shareIcon: some View {
        HStack {
            Spacer()
            Image(systemName: "square.and.arrow.up")
                .renderingMode(.template)
                .font(.system(size: 16))
            Spacer()
        }
        .contentShape(Rectangle())
    }

    var body: some View {
        if !isInCaptureMode {
            Menu {
                if isMenuContentReady {
                    LazyMoreMenuContent(item: item, onMoreAction: onMoreAction)
                } else {
                    Button("") {}
                        .onAppear {
                            isMenuContentReady = true
                        }
                }
            } label: {
                shareIcon
            }
        }
    }
}

struct LazyMoreMenuContent: View {
    @Environment(\.colorScheme) var colorScheme
    @Environment(\.appSettings) var appSettings
    @Environment(FlareRouter.self) var router
    @Environment(FlareTheme.self) private var theme

    let item: TimelineItem
    let onMoreAction: (MoreActionType) -> Void

    private var statusUrl: URL? {
        guard !item.url.isEmpty else { return nil }
        return URL(string: item.url)
    }

    private var imageURLsString: String {
        item.images.map(\.url).joined(separator: "\n")
    }

    private var selectableContent: AttributedString {
        AttributedString(item.content.markdown + "\n" + imageURLsString)
    }

    var body: some View {
        Group {
            Button(action: {
                onMoreAction(.report)
            }) {
                Label("Report", systemImage: "exclamationmark.triangle")
            }

            Divider()

            Button(action: {
                onMoreAction(.copyText)
            }) {
                Label("Copy Text", systemImage: "doc.on.doc")
            }

            Button(action: {
                onMoreAction(.copyMarkdown)
            }) {
                Label("Copy Text (MarkDown)", systemImage: "doc.on.doc")
            }

            if !item.images.isEmpty {
                Button(action: {
                    onMoreAction(.copyMediaLink)
                }) {
                    Label("Copy Media Link", systemImage: "photo.on.rectangle")
                }
            }

            Button(action: {
                onMoreAction(.showTextForSelection)
            }) {
                Label("Copy Any", systemImage: "text.cursor")
            }

            if let url = statusUrl {
                Button(action: {
                    onMoreAction(.copyTweetLink)
                }) {
                    Label("Copy Tweet Link", systemImage: "link")
                }

                Divider()

                Button(action: {
                    onMoreAction(.openInBrowser)
                }) {
                    Label("Open in Browser", systemImage: "safari")
                }
            }

            Divider()

            Button(action: {
                onMoreAction(.sharePost)
            }) {
                Label("Share Post", systemImage: "square.and.arrow.up")
            }

            Button(action: {
                onMoreAction(.shareAsImage)
            }) {
                Label("Share as Image", systemImage: "camera")
            }

            Divider()

            #if canImport(_Translation_SwiftUI)
                Button(action: {
                    onMoreAction(.translate)
                }) {
                    Label("System Translate", systemImage: "character.bubble")
                }
            #else

                Button(action: {
                    onMoreAction(.translate)
                }) {
                    Label("System Translate (Unavailable)", systemImage: "character.bubble")
                }
                .disabled(true)
            #endif

            if !item.images.isEmpty {
                Button(action: {
                    onMoreAction(.saveMedia)
                }) {
                    Label("Save Media", systemImage: "arrow.down.to.line")
                }

                Button(action: {
                    onMoreAction(.copyMediaURLs)
                }) {
                    Label("Copy Media URLs", systemImage: "link")
                }
            }
        }
    }
}
