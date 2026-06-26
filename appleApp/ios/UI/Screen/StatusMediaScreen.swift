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
    @StateObject private var presenter: KotlinPresenter<StatusState>
    @State private var medias: [any UiMedia] = []

    var body: some View {
        MediaViewerScreen(
            medias: medias,
            initialIndex: initialIndex,
            preview: preview,
            shareContext: MediaViewerShareContext(
                statusKey: statusKey.description(),
                userHandle: statusUserHandle
            ),
            showsSupplementaryOverlay: true
        ) { _ in
            StateView(state: presenter.state.status) { timeline in
                if let content = timeline as? UiTimelineV2.Post {
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
           let content = success.data as? UiTimelineV2.Post {
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
           let content = success.data as? UiTimelineV2.Post {
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
        preview: String?
    ) {
        self.accountType = accountType
        self.statusKey = statusKey
        self.initialIndex = initialIndex
        self.preview = preview
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

    @State private var shouldFill = false
    private let wideThreshold: CGFloat = 19.5 / 9.0

    init(data: String, placeholder: String?, customHeader: [String: String]? = nil) {
        self.data = data
        self.placeholder = placeholder
        self.customHeader = customHeader
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
                        let size = result.image.size
                        let ratio = size.height / size.width
                        if ratio > wideThreshold {
                            shouldFill = true
                        } else {
                            shouldFill = false
                        }
                    }
                    .placeholder {
                        if let placeholder {
                            AdaptiveKFImage(data: placeholder, placeholder: nil, customHeader: customHeader)
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
                        let size = result.image.size
                        let ratio = size.height / size.width
                        if ratio > wideThreshold {
                            shouldFill = true
                        } else {
                            shouldFill = false
                        }
                    }
                    .placeholder {
                        if let placeholder {
                            AdaptiveKFImage(data: placeholder, placeholder: nil, customHeader: customHeader)
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
