
import Combine
import Kingfisher
import shared
import SwiftUI

 struct TimelineItemRowViewV2: View {
    let item: TimelineItem
    let index: Int
    let presenter: TimelinePresenter?
    @Binding var scrollPositionID: String?
    let onError: (FlareError) -> Void
    
    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            // 作者信息
            HStack {
                KFImage(URL(string: item.authorAvatar))
                    .flareAvatar(size: CGSize(width: 80, height: 80))
                    .placeholder {
                        Circle()
                            .fill(Color.gray.opacity(0.3))
                    }
                    .resizable()
                    .aspectRatio(contentMode: .fill)
                    .frame(width: 40, height: 40)
                    .clipShape(Circle())
                
                VStack(alignment: .leading, spacing: 2) {
                    Text(item.author)
                        .font(.headline)
                        .foregroundColor(.primary)
                    
                    Text(item.formattedTimestamp)
                        .font(.caption)
                        .foregroundColor(.secondary)
                }
                
                Spacer()
            }
            
            // 内容
            Text(item.content)
                .font(.body)
                .foregroundColor(.primary)
            
            // 媒体内容
            if item.hasMedia {
                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: 8) {
                        ForEach(item.mediaUrls, id: \.self) { url in
                            KFImage(URL(string: url))
                                .flareMediaPreview(size: CGSize(width: 240, height: 240))
                                .placeholder {
                                    Rectangle()
                                        .fill(Color.gray.opacity(0.3))
                                }
                                .resizable()
                                .aspectRatio(contentMode: .fill)
                                .frame(width: 120, height: 120)
                                .clipShape(RoundedRectangle(cornerRadius: 8))
                        }
                    }
                    .padding(.horizontal, 4)
                }
            }
            
            TimelineActionsViewV2(
                item: item,
                onAction: { actionType, updatedItem in
                    handleTimelineAction(actionType, item: updatedItem, at: index)
                }
            )
        }
        .padding(.vertical, 8)
        .padding(.horizontal, 16)
        .id("TimelineItem_\(item.id)")
        .onAppear {
            // 设置滚动位置ID
            if index == 0 {
                scrollPositionID = item.id
            }
            
            // 🔥 修复：恢复边界检查的预加载逻辑
            Task {
                if let presenter,
                   let timelineState = presenter.models.value as? TimelineState,
                   let pagingState = timelineState.listState as? PagingStateSuccess<UiTimeline>
                {
                    // 当接近末尾时触发预加载
                    let currentItemCount = Int(pagingState.itemCount)
                    if index >= currentItemCount - 5 {
                        // ✅ 关键修复：添加边界检查，避免越界访问
                        let preloadDistance = 10
                        let safePreloadIndex = min(index + preloadDistance, currentItemCount - 1)
                        
                        // 确保预加载索引有效且大于当前索引
                        if safePreloadIndex < currentItemCount && safePreloadIndex > index {
                            print("🔄 [TimelineItemRowView] Safe preload: index=\(index), preload=\(safePreloadIndex), total=\(currentItemCount)")
                            _ = pagingState.get(index: Int32(safePreloadIndex))
                        } else {
                            print("🚫 [TimelineItemRowView] Skipped preload: index=\(index), would preload=\(index + preloadDistance), total=\(currentItemCount)")
                        }
                    }
                }
            }
        }
    }
    
     private func handleTimelineAction(_ actionType: TimelineActionType, item: TimelineItem, at index: Int) {
        print("🔄 [TimelineView_v2] Handling action \(actionType) for item: \(item.id) at index: \(index)")
        print("🔍 [TimelineView_v2] Received updated item state:")
        print("   - ID: \(item.id)")
        print("   - Like count: \(item.likeCount)")
        print("   - Is liked: \(item.isLiked)")
        print("   - Retweet count: \(item.retweetCount)")
        print("   - Is retweeted: \(item.isRetweeted)")
        print("   - Bookmark count: \(item.bookmarkCount)")
        print("   - Is bookmarked: \(item.isBookmarked)")
        
        Task {
            print("🚀 [TimelineView_v2] Updating local state for index: \(index)")
            print("✅ [TimelineView_v2] Local state update logged for index: \(index)")
        }
    }
}

