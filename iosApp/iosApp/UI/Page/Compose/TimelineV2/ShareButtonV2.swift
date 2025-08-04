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

enum ShareType {
    case sharePost
    case shareAsImage
}

struct ShareButtonV2: View {
    @Environment(\.colorScheme) var colorScheme
    @Environment(\.appSettings) var appSettings
    @Environment(\.isInCaptureMode) private var isInCaptureMode: Bool

    @Environment(FlareRouter.self) var router
    @Environment(FlareTheme.self) private var theme

    @State private var showTextForSelection: Bool = false
    @State private var showTranslation: Bool = false
    @State private var showSelectUrlSheet: Bool = false

    let item: TimelineItem
    let onShare: (ShareType) -> Void

    private var statusUrl: URL? {
        guard !item.url.isEmpty else { return nil }
        return URL(string: item.url)
    }

    var body: some View {
        if !isInCaptureMode {
            Menu {
                Button(action: {
                    ToastView(icon: UIImage(systemName: "checkmark.circle"), message: NSLocalizedString("Report Success", comment: "")).show()
                }) {
                    Label("Report", systemImage: "exclamationmark.triangle")
                }
                Divider()
                Button(action: {
                    UIPasteboard.general.string = item.content.raw
                    ToastView(icon: UIImage(systemName: "checkmark.circle"), message: NSLocalizedString("Copy Success", comment: "")).show()
                }) {
                    Label("Copy Text ", systemImage: "doc.on.doc")
                }

                Button(action: {
                    UIPasteboard.general.string = item.content.markdown
                    ToastView(icon: UIImage(systemName: "checkmark.circle"), message: NSLocalizedString("Copy Success", comment: "")).show()
                }) {
                    Label("Copy Text (MarkDown)", systemImage: "doc.on.doc")
                }

                if !item.images.isEmpty {
                    Button(action: {
                        showSelectUrlSheet = true
                    }) {
                        Label("Copy Media Link", systemImage: "photo.on.rectangle")
                    }
                }

                Button(action: {
                    showTextForSelection = true
                }) {
                    Label("Copy Any", systemImage: "text.cursor")
                }
                .buttonStyle(PlainButtonStyle())

                if let url = statusUrl {
                    Button(action: {
                        UIPasteboard.general.string = url.absoluteString
                        ToastView(icon: UIImage(systemName: "checkmark.circle"), message: NSLocalizedString("Copy Success", comment: "")).show()
                    }) {
                        Label("Copy Tweet Link", systemImage: "link")
                    }
                    Divider()
                    Button(action: {
                        router.handleDeepLink(url)
                    }) {
                        Label("Open in Browser", systemImage: "safari")
                    }
                }

                Divider()

//            Menu {
                Button(action: {
                    onShare(.sharePost)
                }) {
                    Label("Share Post", systemImage: "square.and.arrow.up")
                }

                Button(action: {
                    onShare(.shareAsImage)
                }) {
                    Label("Share as Image", systemImage: "camera")
                }
//            } label: {
//                Label("Share", systemImage: "square.and.arrow.up")
//            }
                Divider()
                #if canImport(_Translation_SwiftUI)
                    Button(action: {
                        showTranslation = true
                    }) {
                        Label("System Translate", systemImage: "character.bubble")
                    }
                #endif

                Divider()

                Button(action: {
                    FlareLog.debug("ShareButtonV2 Save Media tapped")
                    ToastView(
                        icon: UIImage(systemName: "arrow.down.to.line"),
                        message: String(localized: "download to App \n Download Manager")
                    ).show()
                }) {
                    Label("Save Media", systemImage: "arrow.down.to.line")
                }

                if !item.images.isEmpty {
                    Button(action: {
                        showSelectUrlSheet = true
                    }) {
                        Label("Copy Media URLs", systemImage: "link")
                    }
                }
            } label: {
                HStack {
                    Spacer()
                    Image(systemName: "square.and.arrow.up")
                        .renderingMode(.template)
                        .font(.system(size: 16))
                    Spacer()
                }
                .contentShape(Rectangle())
            }
            #if canImport(_Translation_SwiftUI)
            .addTranslateView(isPresented: $showTranslation, text: item.content.raw)
            #endif
            .sheet(isPresented: $showTextForSelection) {
                let imageURLsString = item.images.map(\.url).joined(separator: "\n")
                let selectableContent = AttributedString(item.content.markdown + "\n" + imageURLsString)

                StatusRowSelectableTextView(content: selectableContent)
                    .tint(.accentColor)
            }
            .sheet(isPresented: $showSelectUrlSheet) {
                let urlsString = item.images.map(\.url).joined(separator: "\n")
                StatusRowSelectableTextView(content: AttributedString(urlsString))
                    .tint(.accentColor)
            }
        }
    }
}
