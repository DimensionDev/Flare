import SwiftUI

struct TweetDetailView: View {
    @EnvironmentObject private var appState: AppState

    var body: some View {
        Text("Tweet Detail View")
            .onAppear {
                // 进入详情页，导航深度增加
                appState.navigationDepth = 1 // 或者根据实际层级设置
                print("TweetDetailView appeared, depth: \(appState.navigationDepth)")
            }
            .onDisappear {
                 // 离开详情页（返回），导航深度减少（假设返回到根）
                 // 注意：更健壮的实现可能需要更复杂的导航状态管理
                 // 这里简化处理，假设总是返回到根(0)
                 if appState.navigationDepth > 0 { // 防止意外情况减成负数
                    // appState.navigationDepth -= 1
                    appState.navigationDepth = 0 // 直接设为0更简单
                 }
                 print("TweetDetailView disappeared, depth reset to: \(appState.navigationDepth)")
            }
            // 可以设置导航栏标题等
            .navigationTitle("Tweet")
            .navigationBarTitleDisplayMode(.inline)
    }
}

 
