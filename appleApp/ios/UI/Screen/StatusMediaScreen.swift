import SwiftUI
import FlareAppleUI
import KotlinSharedUI
import LazyPager
import AVKit
import Photos
import Kingfisher
import SwiftUIBackports
import VideoPlayer
import Combine
import UIKit
import FlareAppleCore

struct StatusMediaScreen: View {
    let accountType: AccountType
    let statusKey: MicroBlogKey
    let initialIndex: Int
    let preview: String?
    let initialMediaAspectRatio: CGFloat?
    let initialMediaIsImage: Bool
    @StateObject private var presenter: KotlinPresenter<StatusState>
    @State private var medias: [any UiMedia] = []

    var body: some View {
        MediaViewerScreen(
            medias: medias,
            initialIndex: initialIndex,
            preview: preview,
            previewAspectRatio: initialMediaAspectRatio,
            previewIsImage: initialMediaIsImage,
            shareContext: MediaViewerShareContext(
                statusKey: statusKey.description(),
                userHandle: statusUserHandle
            ),
            showsSupplementaryOverlay: true
        ) { _ in
            StateView(state: presenter.state.status) { timeline in
                if let content = timeline.timelineContentPost {
                    StatusView(
                        data: content,
                        isQuote: true,
                        showMedia: false,
                        maxLine: 3,
                        showExpandTextButton: false,
                        showParents: false
                    )
                }
            }
        }
        .onAppear {
            syncMediasIfNeeded(animated: false)
        }
        .onChange(of: presenter.state.status) { oldValue, newValue in
            syncMediasIfNeeded(animated: true)
        }
    }

    private func syncMediasIfNeeded(animated: Bool) {
        if medias.isEmpty,
           case .success(let success) = onEnum(of: presenter.state.status),
           let content = success.data.timelineContentPost {
            if animated {
                withAnimation {
                    medias = Array(content.images)
                }
            } else {
                medias = Array(content.images)
            }
        }
    }

    private var statusUserHandle: String {
        if case .success(let success) = onEnum(of: presenter.state.status),
           let content = success.data.timelineContentPost {
            return content.user?.handle.canonical ?? "unknown"
        }
        return "unknown"
    }
}

struct LazyPagerIndicator: View {
    let count: Int
    @Binding var page: Int
    
    var body: some View {
        HStack(spacing: 8) {
            ForEach(0..<count, id: \.self) { index in
                Circle()
                    .fill(index == page ? Color.accentColor : Color.secondary)
                    .frame(width: 8, height: 8)
            }
        }
    }
}

extension StatusMediaScreen {
    init(
        accountType: AccountType,
        statusKey: MicroBlogKey,
        initialIndex: Int,
        preview: String?,
        initialMediaAspectRatio: CGFloat?,
        initialMediaIsImage: Bool
    ) {
        self.accountType = accountType
        self.statusKey = statusKey
        self.initialIndex = initialIndex
        self.preview = preview
        self.initialMediaAspectRatio = initialMediaAspectRatio
        self.initialMediaIsImage = initialMediaIsImage
        self._presenter = .init(wrappedValue: .init(presenter: StatusPresenter(accountType: accountType, statusKey: statusKey)))
    }
}

@MainActor
enum MediaOrientationController {
    static func setLandscape(_ enabled: Bool) {
        guard let scene = UIApplication.shared.connectedScenes
            .compactMap({ $0 as? UIWindowScene })
            .first(where: { $0.activationState == .foregroundActive }) else {
            return
        }

        scene.mediaKeyWindow?.rootViewController?.setNeedsUpdateOfSupportedInterfaceOrientations()
        let orientations: UIInterfaceOrientationMask = enabled ? .landscapeRight : .portrait
        scene.requestGeometryUpdate(.iOS(interfaceOrientations: orientations)) { error in
            print("Media orientation request failed: \(error)")
        }
    }
}

private extension UIWindowScene {
    var mediaKeyWindow: UIWindow? {
        windows.first { $0.isKeyWindow }
    }
}

struct AdaptiveKFImage: View {
    let data: String
    let placeholder: String?
    let customHeader: [String: String]?
    let mediaAspectRatio: CGFloat?

    @State private var measuredImageSize: CGSize?

    init(
        data: String,
        placeholder: String?,
        customHeader: [String: String]? = nil,
        mediaAspectRatio: CGFloat? = nil
    ) {
        self.data = data
        self.placeholder = placeholder
        self.customHeader = customHeader
        self.mediaAspectRatio = mediaAspectRatio
    }

    private var shouldFill: Bool {
        MediaViewerImageLayoutPolicy.shouldFillWidth(
            mediaAspectRatio: mediaAspectRatio,
            measuredImageSize: measuredImageSize
        )
    }

    var body: some View {
        if shouldFill {
            ScrollView(.vertical, showsIndicators: false) {
                kfImageView
            }
        } else {
            kfImageView
        }
    }
    
    var kfImageView: some View {
        ZStack {
            if data.hasSuffix(".gif") {
                KFAnimatedImage(.init(string: data))
                    .requestModifier({ request in
                        if let customHeader {
                            for (key, value) in customHeader {
                                request.setValue(value, forHTTPHeaderField: key)
                            }
                        }
                    })
                    .onSuccess { result in
                        measuredImageSize = result.image.size
                    }
                    .placeholder {
                        if let placeholder {
                            NetworkImage(
                                data: placeholder,
                                customHeader: customHeader,
                                contentMode: shouldFill ? .fill : .fit
                            )
                        } else {
                            ProgressView()
                        }
                    }
                    .aspectRatio(contentMode: shouldFill ? .fill : .fit)
            } else {
                KFImage(.init(string: data))
                    .requestModifier({ request in
                        if let customHeader {
                            for (key, value) in customHeader {
                                request.setValue(value, forHTTPHeaderField: key)
                            }
                        }
                    })
                    .onSuccess { result in
                        measuredImageSize = result.image.size
                    }
                    .placeholder {
                        if let placeholder {
                            NetworkImage(
                                data: placeholder,
                                customHeader: customHeader,
                                contentMode: shouldFill ? .fill : .fit
                            )
                        } else {
                            ProgressView()
                        }
                    }
                    .resizable()
                    .aspectRatio(contentMode: shouldFill ? .fill : .fit)
            }
        }
    }
}
