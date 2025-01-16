import Foundation
import os.log

public class AppContext {
    public let logger = Logger(subsystem: "AppContext", category: "App")

    // Services
    public let translationService: TranslationService

    public init(translationService: TranslationService = AppleTranslationService()) {
        self.translationService = translationService
    }
}
