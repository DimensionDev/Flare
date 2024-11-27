//
//  MediaPreviewViewModel.swift
//  TwidereX
//
//  Created by Cirno MainasuK on 2020-11-5.
//  Copyright © 2020 Twidere. All rights reserved.
//

import UIKit
import Combine
import CoreData
//import CoreDataStack
//import Pageboy
//import MaskCore
//import TwitterSDK
import Kingfisher
//
//final class MediaPreviewViewModel: NSObject {
//    
//    var observations = Set<NSKeyValueObservation>()
//    
//    weak var mediaPreviewImageViewControllerDelegate: MediaPreviewImageViewControllerDelegate?
//
//    // input
//    let context: AppContext
//    let item: Item
//    let transitionItem: MediaPreviewTransitionItem
//    
//    @Published var currentPage: Int
//    
//    // output
//    let viewControllers: [UIViewController]
//    
//    init(
//        context: AppContext,
//        item: Item,
//        transitionItem: MediaPreviewTransitionItem
//    ) {
//        self.context = context
//        self.item = item
//        self.currentPage = {
//            switch item {
//            case .media(let previewContext):
//                return previewContext.initialIndex
//            case .statusMedia(let previewContext):
//                return previewContext.initialIndex
//            }
//        }()
//        self.transitionItem = transitionItem
//        // setup output
//        self.viewControllers = {
//            var viewControllers: [UIViewController] = []
//            switch item {
//            case .media(let previewContext):
//                for (_, source) in previewContext.sources.enumerated() {
//                    if let url = source?.url, url.pathExtension == "mp4" {
//                        // FIXME: video or GIFV ???
//                        let viewController = MediaPreviewVideoViewController()
//                        viewController.viewModel = MediaPreviewVideoViewModel(
//                            context: context,
//                            item: .gif(.init(
//                                assetURL: url,
//                                previewURL: nil
//                            ))
//                        )
//                        viewControllers.append(viewController)
//                    } else {
//                        let viewController = MediaPreviewImageViewController()
//                        viewController.viewModel = MediaPreviewImageViewModel(
//                            context: context,
//                            item: .init(source: source, thumbnail: nil)
//                        )
//                        viewControllers.append(viewController)
//                    }
//                }
//            case .statusMedia(let previewContext):
//                for (i, attachment) in previewContext.attachments.enumerated() {
//                    switch attachment.kind {
//                    case .photo:
//                        let viewController = MediaPreviewImageViewController()
//                        viewController.viewModel = MediaPreviewImageViewModel(
//                            context: context,
//                            item: .init(
//                                source: attachment.originalURL.flatMap { URL(string: $0) }.flatMap { Source.network($0) },
//                                thumbnail: previewContext.thumbnail(at: i)
//                            )
//                        )
//                        viewControllers.append(viewController)
//                    case .video:
//                        // FIXME:
//                        // assertionFailure("Use system AVPlayerViewController directly")
//                        // viewControllers.append(UIViewController())
//                        
//                        let viewController = MediaPreviewImageViewController()
//                        viewController.viewModel = MediaPreviewImageViewModel(
//                            context: context,
//                            item: .init(
//                                source: attachment.previewImageURL.flatMap { URL(string: $0) }.flatMap { Source.network($0) },
//                                thumbnail: previewContext.thumbnail(at: i)
//                            )
//                        )
//                        viewControllers.append(viewController)
//                    case .animatedGIF:
//                        let viewController = MediaPreviewVideoViewController()
//                        viewController.viewModel = MediaPreviewVideoViewModel(
//                            context: context,
//                            item: .gif(.init(
//                                assetURL: attachment.url.flatMap { URL(string: $0) },
//                                previewURL: attachment.previewImageURL.flatMap { URL(string: $0) }
//                            ))
//                        )
//                        viewControllers.append(viewController)
////                    case .audio:
////                        assertionFailure("Use system AVPlayerViewController directly")
////                        viewControllers.append(UIViewController())
//                    }
//                }
//            }   // end switch
//            return viewControllers
//        }()
//        super.init()
//    }
//    
//}
//
//extension MediaPreviewViewModel {
//    
//    enum Item {
//        case media(MediaPreviewContext)
//        case statusMedia(StatusMediaPreviewContext)
//    }
//    
//    struct MediaPreviewContext {
//        let sources: [Kingfisher.Source?]
//        let initialIndex: Int
//        let preloadThumbnails: [UIImage?]
//        
//        func thumbnail(at index: Int) -> UIImage? {
//            guard index < preloadThumbnails.count else { return nil }
//            return preloadThumbnails[index]
//        }
//    }
//
//    struct StatusMediaPreviewContext {
//        let status: Twitter.Entity.V2.Tweet
//        let attachments: [Twitter.Entity.V2.Media]
//        let initialIndex: Int
//        let preloadThumbnails: [UIImage?]
//        
//        func thumbnail(at index: Int) -> UIImage? {
//            guard index < preloadThumbnails.count else { return nil }
//            return preloadThumbnails[index]
//        }
//    }
//       
//}
//
//// MARK: - PageboyViewControllerDataSource
//extension MediaPreviewViewModel: PageboyViewControllerDataSource {
//    
//    func numberOfViewControllers(in pageboyViewController: PageboyViewController) -> Int {
//        return viewControllers.count
//    }
//
//    func viewController(for pageboyViewController: PageboyViewController, at index: PageboyViewController.PageIndex) -> UIViewController? {
//        let viewController = viewControllers[index]
//        if let mediaPreviewImageViewController = viewController as? MediaPreviewImageViewController {
//            mediaPreviewImageViewController.delegate = mediaPreviewImageViewControllerDelegate
//        }
//        return viewController
//    }
//
//    func defaultPage(for pageboyViewController: PageboyViewController) -> PageboyViewController.Page? {
//        switch item {
//        case .media(let previewContext):
//            return .at(index: previewContext.initialIndex)
//        case .statusMedia(let previewContext):
//            return .at(index: previewContext.initialIndex)
//        }
//    }
//    
//}
