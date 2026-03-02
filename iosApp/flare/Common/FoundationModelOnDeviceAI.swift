import Foundation
import KotlinSharedUI

#if canImport(FoundationModels)
import FoundationModels
#endif

final class FoundationModelOnDeviceAI: SwiftOnDeviceAI {
    private init() {}

    static let shared = FoundationModelOnDeviceAI()

    func isAvailable() -> Bool {
        #if canImport(FoundationModels)
        if #available(iOS 26.0, *) {
            return SystemLanguageModel.default.isAvailable
        }
        #endif
        return false
    }

    func translate(source: String, targetLanguage: String, prompt: String) -> String? {
        return generateText(prompt: prompt)
    }

    func tldr(source: String, targetLanguage: String, prompt: String) -> String? {
        return generateText(prompt: prompt)
    }

    private func generateText(prompt: String) -> String? {
        guard isAvailable() else {
            return nil
        }

        #if canImport(FoundationModels)
        if #available(iOS 26.0, *) {
            let session = LanguageModelSession()
            let response = try await session.respond(to: prompt)
            return response.content
        }
        #endif

        return nil
    }
}
