import KotlinSharedUI
import Foundation

class AppleScraper: AppleWebScraper {
    private init() {}
    static let shared = AppleScraper()
    
    func parse(url: String, callback: @escaping (DocumentData) -> Void) {
    }
}
