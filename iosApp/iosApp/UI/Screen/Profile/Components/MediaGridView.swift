import SwiftUI
import shared
//
//struct MediaGridView: View {
//    let items: PagingState<UiMedia>
//    @State private var isRefreshing = false
//    
//    var body: some View {
//        ScrollView {
//            RefreshControl(isRefreshing: $isRefreshing) {
//                if case .success(let success) = onEnum(of: items) {
//                    success.retry()
//                }
//            }
//            
//            LazyVStack(spacing: 0) {
//                contentView
//            }
//        }
//    }
//    
//    @ViewBuilder
//    private var contentView: some View {
//        switch onEnum(of: items) {
//        case .error(let error):
//            ErrorView(error: error.error.asError())
//        case .loading:
//            LoadingView()
//        case .empty:
//            EmptyStateView()
//        case .success(let success):
//            MediaGridContentView(success: success)
//        }
//    }
//}
//
//// MARK: - Loading View
//private struct LoadingView: View {
//    var body: some View {
//        ProgressView()
//            .frame(maxWidth: .infinity, maxHeight: .infinity)
//            .padding(.vertical, 40)
//    }
//}
//
//// MARK: - Empty State View
//private struct EmptyStateView: View {
//    var body: some View {
//        VStack(spacing: 16) {
//            Image(systemName: "photo.on.rectangle")
//                .font(.system(size: 48))
//                .foregroundColor(.secondary)
//            Text("No media yet")
//                .font(.headline)
//                .foregroundColor(.secondary)
//        }
//        .frame(maxWidth: .infinity, maxHeight: .infinity)
//        .padding(.vertical, 40)
//    }
//}
//
//// MARK: - Media Grid Content View
//private struct MediaGridContentView: View {
//    let success: PagingStateSuccess<UiMedia>
//    let columns = [
//        GridItem(.flexible(), spacing: 1),
//        GridItem(.flexible(), spacing: 1),
//        GridItem(.flexible(), spacing: 1)
//    ]
//    
//    var body: some View {
//        LazyVGrid(columns: columns, spacing: 1) {
//            ForEach(0..<success.itemCount, id: \.self) { index in
//                if let item = success.peek(index: index) {
//                    MediaGridItem(media: item)
//                        .onAppear {
//                            success.get(index: index)
//                        }
//                }
//            }
//        }
//        
////        if success.hasMore {
////            LoadMoreView {
////                success.loadMore()
////            }
////        }
//    }
//}
//
//// MARK: - Media Grid Item
//private struct MediaGridItem: View {
//    let media: UiMedia
//    
//    var body: some View {
//        GeometryReader { geometry in
//            AsyncImage(url: URL(string: media.url ?? "")) { phase in
//                switch phase {
//                case .empty:
//                    defaultMediaView
//                case .success(let image):
//                    image
//                        .resizable()
//                        .aspectRatio(contentMode: .fill)
//                case .failure:
//                    defaultMediaView
//                @unknown default:
//                    defaultMediaView
//                }
//            }
//            .frame(width: geometry.size.width, height: geometry.size.width)
//            .clipped()
//        }
//        .aspectRatio(1, contentMode: .fit)
//    }
//    
//    private var defaultMediaView: some View {
//        Color.gray.opacity(0.3)
//    }
//}
//
//// MARK: - Load More View
//private struct LoadMoreView: View {
//    let action: () -> Void
//    
//    var body: some View {
//        ProgressView()
//            .frame(maxWidth: .infinity)
//            .padding()
//            .onAppear(perform: action)
//    }
//}
//
//// MARK: - Refresh Control
//private struct RefreshControl: View {
//    @Binding var isRefreshing: Bool
//    let onRefresh: () -> Void
//    
//    var body: some View {
//        GeometryReader { geometry in
//            if geometry.frame(in: .global).minY > 50 {
//                Spacer()
//                    .onAppear {
//                        isRefreshing = true
//                        onRefresh()
//                    }
//                    .onDisappear {
//                        isRefreshing = false
//                    }
//            }
//            
//            HStack {
//                Spacer()
//                if isRefreshing {
//                    ProgressView()
//                }
//                Spacer()
//            }
//        }
//        .frame(height: 50)
//    }
//}
//
//// MARK: - Error View
//private struct ErrorView: View {
//    let error: Error
//    
//    var body: some View {
//        VStack(spacing: 16) {
//            Image(systemName: "exclamationmark.triangle")
//                .font(.system(size: 48))
//                .foregroundColor(.secondary)
//            
//            Text("Something went wrong")
//                .font(.headline)
//                .foregroundColor(.secondary)
//            
//            Text(error.localizedDescription)
//                .font(.subheadline)
//                .foregroundColor(.secondary)
//                .multilineTextAlignment(.center)
//                .padding(.horizontal)
//        }
//        .frame(maxWidth: .infinity, maxHeight: .infinity)
//        .padding(.vertical, 40)
//    }
//}
