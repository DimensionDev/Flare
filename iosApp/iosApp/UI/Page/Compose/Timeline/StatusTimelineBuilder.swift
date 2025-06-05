import shared
import SwiftUI

struct StatusTimelineComponent: View {
    @Environment(\.horizontalSizeClass) private var horizontalSizeClass
    let data: PagingState<UiTimeline>
    let detailKey: MicroBlogKey?

    var body: some View {
        switch onEnum(of: data) {
        case .empty: Text("timeline_load_empty", comment: "Timeline is empty")
        case let .error(error):
            Text("timeline_load_error", comment: "Timeline loading error")
            Text(error.error.message ?? "")
        case .loading:
            ForEach((-10) ... -1, id: \.self) { _ in
                StatusPlaceHolder()
                    .if(horizontalSizeClass != .compact) { view in
                        view.padding([.horizontal])
                    }
            }
        case let .success(success):
            ForEach(0 ..< success.itemCount, id: \.self) { index in
                let data: UiTimeline? = {
                    do {
                        return try success.peek(index: index)
                    } catch {
                        print("Error peeking timeline data: \(error)")
                        return nil
                    }
                }()
                VStack(spacing: 0) {
                    if let status = data {
                        StatusItemView(
                            data: status,
                            detailKey: detailKey
                        )
                        .padding(.vertical, 8)
                    } else {
                        StatusPlaceHolder()
                    }
                }
                .onAppear {
                    // success.get(index: index)
                }
                .if(horizontalSizeClass != .compact) { view in
                    view.padding([.horizontal])
                }
            }
        }
    }
}
