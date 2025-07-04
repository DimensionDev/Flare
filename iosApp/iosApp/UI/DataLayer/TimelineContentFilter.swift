import Foundation
import SwiftUI

 class TimelineContentFilter {
    
 
     static func filterSensitiveContent(_ items: [TimelineItem]) -> [TimelineItem] {
        let startTime = CFAbsoluteTimeGetCurrent()
        
         guard shouldFilterSensitiveContent() else {
            FlareLog.debug("TimelineContentFilter 敏感内容过滤未开启，返回原始数据")
            return items
        }
        
        let originalCount = items.count
        let filteredItems = items.filter { item in
            !shouldHideItem(item)
        }
        
        let duration = CFAbsoluteTimeGetCurrent() - startTime
        let filteredCount = originalCount - filteredItems.count
        
        FlareLog.debug("TimelineContentFilter 过滤完成: 原始\(originalCount)项 -> 过滤\(filteredCount)项 -> 剩余\(filteredItems.count)项, 耗时\(String(format: "%.3f", duration))秒")
        
        return filteredItems
    }
    
   
    
     private static func shouldFilterSensitiveContent() -> Bool {

        guard let appSettings = getAppSettings() else {
            FlareLog.warning("TimelineContentFilter 无法获取AppSettings，跳过过滤")
            return false
        }
        
        let hideInTimeline = appSettings.appearanceSettings.sensitiveContentSettings.hideInTimeline
        FlareLog.debug("TimelineContentFilter 敏感内容Timeline隐藏设置: \(hideInTimeline)")
        
        return hideInTimeline
    }
    

    private static func shouldHideItem(_ item: TimelineItem) -> Bool {

        guard item.sensitive else {
            return false
        }
        
        guard let appSettings = getAppSettings() else {
            FlareLog.warning("TimelineContentFilter 无法获取AppSettings，默认不隐藏敏感内容")
            return false
        }
        
        let settings = appSettings.appearanceSettings.sensitiveContentSettings
        

        if let timeRange = settings.timeRange, timeRange.isEnabled {
            let shouldHide = timeRange.isCurrentTimeInRange()
            FlareLog.debug("TimelineContentFilter 敏感内容时间范围检查 - item.id: \(item.id), 当前时间在范围内: \(shouldHide)")
            return shouldHide
        } else {

            FlareLog.debug("TimelineContentFilter 敏感内容无时间限制隐藏 - item.id: \(item.id)")
            return true
        }
    }
    
     private static func getAppSettings() -> AppSettings? {
         return AppSettings()
    }
}

 