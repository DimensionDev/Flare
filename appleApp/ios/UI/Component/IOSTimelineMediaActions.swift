import FlareAppleCore
import FlareAppleUI
import KotlinSharedUI
import SwiftUI
import UIKit

enum IOSTimelineMediaActions {
    static let handler: TimelineMediaActionHandler = { post, media, action in
        switch action {
        case .download:
            save(media: media, post: post)
        case .downloadAll:
            saveAll(post: post)
        case .shareImage:
            shareImage(media: media, post: post)
        case .copyLink:
            UIPasteboard.general.string = media.url
        }
    }

    private static func save(
        media: any UiMedia,
        post: UiTimelineV2.Post,
        showsDownloadStarted: Bool = true,
        showsSaveResult: Bool = true,
        completion: (@Sendable (Bool) -> Void)? = nil
    ) {
        switch onEnum(of: media) {
        case .image(let image):
            MediaSaver.shared.saveImage(
                url: image.url,
                customHeaders: image.customHeaders,
                showsDownloadStarted: showsDownloadStarted,
                showsSaveResult: showsSaveResult,
                completion: completion
            )
        case .gif(let gif):
            MediaSaver.shared.saveImage(
                url: gif.url,
                customHeaders: gif.customHeaders,
                showsDownloadStarted: showsDownloadStarted,
                showsSaveResult: showsSaveResult,
                completion: completion
            )
        case .video(let video):
            MediaSaver.shared.saveVideo(
                url: video.url,
                customHeaders: video.customHeaders,
                showsDownloadStarted: showsDownloadStarted,
                showsSaveResult: showsSaveResult,
                completion: completion
            )
        case .audio(let audio):
            MediaSaver.shared.saveFile(
                url: audio.url,
                fileName: fileName(post: post, media: media),
                customHeaders: audio.customHeaders,
                showsDownloadStarted: showsDownloadStarted,
                showsSaveResult: showsSaveResult,
                completion: completion
            )
        }
    }

    private static func saveAll(post: UiTimelineV2.Post) {
        let medias = Array(post.images)
        guard !medias.isEmpty else {
            return
        }

        MediaSaver.shared.showDownloadStarted()
        let tracker = TimelineMediaBatchSaveTracker(count: medias.count)

        let onComplete: @Sendable (Bool) -> Void = { success in
            Task {
                if let batchSuccess = await tracker.complete(success: success) {
                    await MainActor.run {
                        MediaSaver.shared.showBatchSaveResult(success: batchSuccess)
                    }
                }
            }
        }

        for media in medias {
            save(
                media: media,
                post: post,
                showsDownloadStarted: false,
                showsSaveResult: false,
                completion: onComplete
            )
        }
    }

    private static func shareImage(media: any UiMedia, post: UiTimelineV2.Post) {
        guard case .image(let image) = onEnum(of: media) else {
            return
        }
        Task {
            guard let fileURL = try? await OriginalImageShareFile.make(
                url: image.url,
                customHeaders: image.customHeaders,
                statusKey: post.statusKey.description(),
                userHandle: post.user?.handle.canonical,
                onPreparingNeeded: {
                    MediaSaver.showPreparingMedia()
                }
            ) else {
                return
            }
            await MainActor.run {
                guard let presenter = topViewController() else {
                    return
                }
                let controller = UIActivityViewController(activityItems: [fileURL], applicationActivities: nil)
                controller.popoverPresentationController?.sourceView = presenter.view
                controller.popoverPresentationController?.sourceRect = presenter.view.bounds
                presenter.present(
                    controller,
                    animated: true
                )
            }
        }
    }

    private static func fileName(
        post: UiTimelineV2.Post,
        media: any UiMedia
    ) -> String {
        MediaFileNamePolicy.shared.statusMediaFileName(
            statusKey: post.statusKey.description(),
            userHandle: post.user?.handle.canonical ?? "unknown",
            media: media
        )
    }

    private static func topViewController() -> UIViewController? {
        let rootViewController = UIApplication.shared.connectedScenes
            .compactMap { $0 as? UIWindowScene }
            .first { $0.activationState == .foregroundActive }?
            .windows
            .first { $0.isKeyWindow }?
            .rootViewController
        return topViewController(from: rootViewController)
    }

    private static func topViewController(from viewController: UIViewController?) -> UIViewController? {
        if let navigationController = viewController as? UINavigationController {
            return topViewController(from: navigationController.visibleViewController)
        }
        if let tabBarController = viewController as? UITabBarController {
            return topViewController(from: tabBarController.selectedViewController)
        }
        if let presentedViewController = viewController?.presentedViewController {
            return topViewController(from: presentedViewController)
        }
        return viewController
    }
}

private actor TimelineMediaBatchSaveTracker {
    private var remainingCount: Int
    private var hasFailure = false

    init(count: Int) {
        remainingCount = count
    }

    func complete(success: Bool) -> Bool? {
        hasFailure = hasFailure || !success
        remainingCount -= 1
        guard remainingCount == 0 else {
            return nil
        }
        return !hasFailure
    }
}
