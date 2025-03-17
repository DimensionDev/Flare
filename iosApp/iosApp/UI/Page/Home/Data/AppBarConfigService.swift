import Foundation
import os
import shared
import SwiftUI

// 配置管理服务
class AppBarConfigService {
    private let logger = Logger(subsystem: "com.flare.app", category: "AppBarConfigService")
    private let settingsManager = FLTabSettingsManager()
    private let storageLock = NSLock()

    // 加载AppBar配置
    func loadAppBarConfig(for user: UiUserV2) -> [AppBarItemConfig] {
        storageLock.lock()
        defer { storageLock.unlock() }

        // 通过settingsManager加载配置
        let config = settingsManager.getAppBarConfig(for: user)
        let items = config.enabledItems

        logger.debug("加载到\(items.count)个配置项")
        return items
    }

    // 保存AppBar配置
    func saveAppBarConfig(_ items: [AppBarItemConfig], for user: UiUserV2) {
        storageLock.lock()
        defer { storageLock.unlock() }

        // 通过settingsManager保存配置
        let config = PlatformAppBarConfig(enabledItems: items)
        settingsManager.saveAppBarConfig(config, for: user)

        logger.debug("保存\(items.count)个配置项")
    }

    // 创建默认配置
    func createDefaultAppBarConfig(for user: UiUserV2) -> [AppBarItemConfig] {
        let defaultConfig = settingsManager.createDefaultAppBarConfig(for: user)
        let items = defaultConfig.enabledItems
        logger.debug("创建默认AppBar配置")
        return items
    }

    // 将配置转换为标签
    func convertConfigToTabs(_ items: [AppBarItemConfig], for user: UiUserV2, accountType: AccountType) -> [FLTabItem] {
        settingsManager.convertConfigToTabs(
            PlatformAppBarConfig(enabledItems: items),
            for: user,
            accountType: accountType
        )
    }

    // 将标签转换为配置
    func convertTabToConfig(_ tab: FLTabItem, type: AppBarItemType, addedTime: Date) -> AppBarItemConfig {
        settingsManager.convertTabToConfig(
            tab,
            type: type,
            addedTime: addedTime
        )
    }
}
