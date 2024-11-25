import SwiftUI
import shared

//struct TimelineView: View {
//    let items: UiState<PagingState<UiTimeline>>
//    
//    var body: some View {
//        switch onEnum(of: items) {
//        case .loading:
//            ProgressView()
//                .frame(maxWidth: .infinity, maxHeight: .infinity)
//        case .success(let pagingState):
//            switch onEnum(of: pagingState) {
//            case .loading:
//                ProgressView()
//                    .frame(maxWidth: .infinity, maxHeight: .infinity)
////            case .empty:
////                Text("No posts")
////                    .foregroundColor(.secondary)
////                    .frame(maxWidth: .infinity, maxHeight: .infinity)
//            case .success(let success):
//                LazyVStack(spacing: 0) {
//                    ForEach(0..<success.size, id: \.self) { index in
//                        if let status = success.get(index: index) {
//                            StatusView(status: status)
//                                .onAppear {
//                                    if index == success.size - 1 {
//                                        success.retry()
//                                    }
//                                }
//                            
//                            Divider()
//                        }
//                    }
//                    
//                    if success.appendState == .loading {
//                        ProgressView()
//                            .frame(maxWidth: .infinity)
//                            .padding()
//                    }
//                }
//            case .error(let error):
//                VStack {
//                    Text(error.error.localizedDescription)
//                        .foregroundColor(.secondary)
//                    
//                    Button("Retry") {
//                        error.retry()
//                    }
//                }
//                .frame(maxWidth: .infinity, maxHeight: .infinity)
//            }
//        case .error(let error):
//            VStack {
//                Text(error.error.localizedDescription)
//                    .foregroundColor(.secondary)
//                
//                Button("Retry") {
//                    error.retry()
//                }
//            }
//            .frame(maxWidth: .infinity, maxHeight: .infinity)
//        }
//    }
//}
//
//struct StatusView: View {
//    let status: UiTimeline
//    
//    var body: some View {
//        VStack(alignment: .leading, spacing: 8) {
//            if let topMessage = status.topMessage {
//                Text(topMessage.type as! DateInterval)
//                    .font(.footnote)
//                    .foregroundColor(.secondary)
//            }
//            
//            if let content = status.content {
//                switch onEnum(of: content) {
//                case .status(let data):
//                    Text( as NSObjectverbatim: <#String#>verbatim: <#String#>)
//                default:
//                    EmptyView()
//                }
//            }
//        }
//        .padding()
//    }
//}
