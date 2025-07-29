import Foundation
import os
import shared
import SwiftUI

// 通知管理服务
class AppBarNotificationService {
    // 发送标签更新通知
    func postTabsDidUpdateNotification(updatedTabKey: String? = nil, newTitle: String? = nil) {
        var userInfo: [String: Any]?
        if let key = updatedTabKey, let title = newTitle {
            userInfo = ["updatedTabKey": key, "newTitle": title]
        }

        DispatchQueue.main.async {
            NotificationCenter.default.post(
                name: NSNotification.Name("TabsDidUpdate"),
                object: nil,
                userInfo: userInfo
            )
        }
    }

    // 发送列表Pin状态变化通知
    func postListPinStatusChangedNotification(
        listId: String,
        listTitle: String,
        isPinned: Bool,
        isBlueskyFeed: Bool,
        iconUrl: String? = nil
    ) {
        var userInfo: [String: Any] = [
            "listId": listId,
            "listTitle": listTitle,
            "isPinned": isPinned,
            "itemType": isBlueskyFeed ? "feed" : "list"
        ]

        if let iconUrl, !iconUrl.isEmpty {
            userInfo["listIconUrl"] = iconUrl
        }

        DispatchQueue.main.async {
            NotificationCenter.default.post(
                name: .listPinStatusChanged,
                object: nil,
                userInfo: userInfo
            )
        }
    }

    // 添加列表Pin状态变化观察者
    func addListPinStatusChangedObserver(
        target: Any,
        selector: Selector
    ) {
        NotificationCenter.default.addObserver(
            target,
            selector: selector,
            name: .listPinStatusChanged,
            object: nil
        )
    }

    // 添加列表标题更新观察者
    func addListTitleDidUpdateObserver(
        target: Any,
        selector: Selector
    ) {
        NotificationCenter.default.addObserver(
            target,
            selector: selector,
            name: .listTitleDidUpdate,
            object: nil
        )
    }

    // 移除所有观察者
    func removeAllObservers(target: Any) {
        NotificationCenter.default.removeObserver(target)
    }
}
