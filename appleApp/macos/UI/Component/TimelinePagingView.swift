import FlareAppleUI
import KotlinSharedUI
import SwiftUI

struct TimelinePagingContent: View {
    @Environment(\.refresh) private var refreshAction: RefreshAction?

    let data: PagingState<UiTimelineV2>
    let detailStatusKey: MicroBlogKey?
    let key: String
    let topContentInset: CGFloat
    let allowGalleryMode: Bool
    let suppressInitialRefreshIndicator: Bool

    init(
        data: PagingState<UiTimelineV2>,
        detailStatusKey: MicroBlogKey?,
        key: String,
        topContentInset: CGFloat = 0,
        allowGalleryMode: Bool = false,
        suppressInitialRefreshIndicator: Bool = false
    ) {
        self.data = data
        self.detailStatusKey = detailStatusKey
        self.key = key
        self.topContentInset = topContentInset
        self.allowGalleryMode = allowGalleryMode
        self.suppressInitialRefreshIndicator = suppressInitialRefreshIndicator
    }

    var body: some View {
        ScrollView {
            TimelinePagingView(data: data, detailStatusKey: detailStatusKey)
                .padding(.top, topContentInset)
                .padding(.bottom, 12)
        }
        .id(key)
        .refreshable {
            if let refreshAction {
                await refreshAction()
            }
        }
    }
}
