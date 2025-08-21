import Foundation
import SwiftUI

struct ReleaseLogEntry {
    let version: String
    let content: String
}

@MainActor
class ReleaseLogManager: ObservableObject {
    static let shared = ReleaseLogManager()
    
    @Published var releaseLogEntries: [ReleaseLogEntry] = []
    @Published var isLoading = false
    
    private init() {
        loadReleaseLog()
    }
    
    func loadReleaseLog() { 
        if let path = Bundle.main.path(forResource: "ReleaseLog", ofType: "md"),
           let content = try? String(contentsOfFile: path) {
          
            parseReleaseLog(content: content)
        } else { 
            parseReleaseLog(content: "None")
        }
    }
    
    private func parseReleaseLog(content: String) {
 
        let sections = content.components(separatedBy: "----------")
 
        releaseLogEntries = sections.compactMap { section in
            let trimmedSection = section.trimmingCharacters(in: .whitespacesAndNewlines)
            guard !trimmedSection.isEmpty else { 
                return nil
            }

            // 提取版本号（从第一行的 # 标题中）
            let lines = trimmedSection.components(separatedBy: .newlines)
            guard let firstLine = lines.first,
                  firstLine.hasPrefix("#") else { 
                return nil
            }

            let version = extractVersion(from: firstLine)
            print("✅ 解析到版本: \(version)")
            return ReleaseLogEntry(version: version, content: trimmedSection)
        }

        print("🎉 解析完成，共 \(releaseLogEntries.count) 个版本条目")
        for entry in releaseLogEntries {
            print("📋 版本: \(entry.version)")
        }
    }
    
    private func extractVersion(from line: String) -> String {
        // 从 "# Flare 0.4.7 Release Notes" 中提取 "0.4.7"
        let pattern = #"(\d+\.\d+\.\d+)"#
        if let regex = try? NSRegularExpression(pattern: pattern),
           let match = regex.firstMatch(in: line, range: NSRange(line.startIndex..., in: line)) {
            return String(line[Range(match.range, in: line)!])
        }
        return "Unknown"
    }
    
    func getCurrentVersionLog() -> ReleaseLogEntry? {
        guard let currentVersion = getCurrentAppVersion() else { return nil }
        return releaseLogEntries.first { $0.version == currentVersion }
    }
    
    func getCurrentAppVersion() -> String? {
        return Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String
    }
    
    func hasShownVersionLog(version: String) -> Bool {
        let settings = UserDefaults.standard.appearanceSettings
        return settings.shownReleaseLogVersions[version] != nil
    }
    
    func markVersionLogAsShown(version: String) {
        var settings = UserDefaults.standard.appearanceSettings
        settings.shownReleaseLogVersions[version] = 1
        UserDefaults.standard.appearanceSettings = settings
    }
    
    func shouldShowReleaseLogPopup() -> Bool {
        guard let currentVersion = getCurrentAppVersion(),
              let currentLog = getCurrentVersionLog() else { return false }

        return !hasShownVersionLog(version: currentVersion)
    }

  
    func shouldShowBanner() -> Bool {
        guard let currentVersion = getCurrentAppVersion() else { return false }
        return !hasShownVersionLog(version: currentVersion)
    }

    
    func isFirstInstall() -> Bool {
        let settings = UserDefaults.standard.appearanceSettings
        return settings.shownReleaseLogVersions.isEmpty
    }

     
    func getBannerText() -> String {
        if isFirstInstall() {
            return "View recent version release notes"
        } else if let currentVersion = getCurrentAppVersion() {
            return "Updated to v\(currentVersion), view latest release notes"
        }
        return ""
    }
}
