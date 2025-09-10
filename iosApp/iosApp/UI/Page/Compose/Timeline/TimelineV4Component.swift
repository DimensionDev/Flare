import shared
import SwiftUI

// 图片过滤+ 图片预加载等都没
struct TimelineV4Component: View {
    let data: PagingState<UiTimeline>

    @State private var timelineState: FlareTimelineState = .loading
    @State private var converter = PagingStateConverter()
    @Environment(FlareTheme.self) private var theme

    var body: some View {
        Group {
            switch timelineState {
            case .loading:
                ForEach(0 ..< 5, id: \.self) { _ in
                    TimelineStatusViewV2(
                        item: createSampleTimelineItem(),
                        timelineViewModel: nil
                    )
                    .redacted(reason: .placeholder)
                    .listRowBackground(theme.primaryBackgroundColor)
                    .listRowInsets(EdgeInsets())
                    .listRowSeparator(.hidden)
                }

            case let .loaded(items, hasMore):
                ForEach(items) { item in
                    TimelineStatusViewV2(
                        item: item,
                        timelineViewModel: nil
                    )
                    .listRowBackground(theme.primaryBackgroundColor)
                }

                if hasMore {
                    HStack {
                        Spacer()
                        ProgressView()
                            .progressViewStyle(CircularProgressViewStyle())
                        Spacer()
                    }
                    .listRowBackground(theme.primaryBackgroundColor)
                    .listRowInsets(EdgeInsets())
                }

            case let .error(error):
                VStack(spacing: 16) {
                    Text("timeline_load_error")
                        .font(.headline)

                    Text(error.localizedDescription)
                        .font(.caption)
                        .multilineTextAlignment(.center)

                    Button("retry") {
                        timelineState = .loading
                        DispatchQueue.main.asyncAfter(deadline: .now() + 0.1) {
                            timelineState = converter.convert(data)
                        }
                    }
                }
                .padding()
                .listRowBackground(theme.primaryBackgroundColor)
                .listRowInsets(EdgeInsets())

            case .empty:
                VStack(spacing: 16) {}
                    .padding()
                    .listRowBackground(theme.primaryBackgroundColor)
                    .listRowInsets(EdgeInsets())
            }
        }
        .onAppear {
            updateTimelineState(from: data)
        }
        .onChange(of: data) { newData in
            updateTimelineState(from: newData)
        }
    }

    private func updateTimelineState(from pagingState: PagingState<UiTimeline>) {
        timelineState = converter.convert(pagingState)
    }
}
