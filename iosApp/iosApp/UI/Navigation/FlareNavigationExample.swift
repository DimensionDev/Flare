import SwiftUI
import SwiftfulRouting
import shared

/// 示例视图：展示如何使用新的SwiftfulRouting路由系统
struct FlareNavigationExample: View {
    // 从环境中获取路由器
    @Environment(\.router) private var router
    
    // 从环境中获取应用状态
    @EnvironmentObject private var appState: FlareAppState
    
    var body: some View {
        List {
            // 基本导航示例 - 使用路由器直接导航
            Section("基本导航") {
                Button("导航到个人资料") {
                    router.showFlareDestination(.profile(accountId: nil), with: .push)
                }
                
                Button("显示设置") {
                    router.showFlareDestination(.settings, with: .sheet)
                }
                
                Button("全屏显示登录") {
                    router.showFlareDestination(.login, with: .fullScreenCover)
                }
            }
            
            // 使用修饰符导航
            Section("修饰符导航") {
                Text("点击导航到搜索页面")
                    .navigateTo(.search)
                
                Text("点击导航到通知页面")
                    .navigateTo(.notification)
            }
            
            // 使用AppState导航
            Section("应用状态导航") {
                Button("显示撰写帖子") {
                    appState.showCompose()
                }
                
                Button("显示个人资料") {
                    appState.showProfile(accountId: "example_user")
                }
                
                Button("显示帖子详情") {
                    appState.showPostDetail(statusKey: "example_post")
                }
            }
            
            // 进阶导航：流程导航
            Section("流程导航") {
                Button("启动注册流程") {
                    let destinations: [FlareRouteDestination] = [
                        .login,
                        .profile(accountId: nil),
                        .settings
                    ]
                    
                    router.enterFlareFlow(destinations)
                }
                
                Button("显示图片查看器") {
                    appState.showImageViewer(
                        urls: ["https://example.com/image1.jpg", "https://example.com/image2.jpg"],
                        initialIndex: 0,
                        using: router
                    )
                }
                
                Button("显示视频播放器") {
                    appState.showVideoPlayer(
                        url: "https://example.com/video.mp4",
                        using: router
                    )
                }
            }
            
            // 弹窗示例
            Section("弹窗") {
                Button("显示提示") {
                    router.showBasicAlert(title: "提示", text: "这是一个基本的提示")
                }
                
                Button("显示确认弹窗") {
                    router.showConfirmAlert(title: "确认", text: "确定执行此操作吗？") {
                        print("用户确认了操作")
                    }
                }
            }
            
            // 导航控制
            Section("导航控制") {
                Button("返回") {
                    router.dismissScreen()
                }
                
                Button("返回到根视图") {
                    if #available(iOS 16, *) {
                        router.dismissScreenStack()
                    } else {
                        // iOS 15及以下版本的回退逻辑
                        // 暂未实现
                    }
                }
                
                Button("关闭环境") {
                    router.dismissEnvironment()
                }
            }
        }
        .navigationModifers(title: "导航示例")
    }
} 