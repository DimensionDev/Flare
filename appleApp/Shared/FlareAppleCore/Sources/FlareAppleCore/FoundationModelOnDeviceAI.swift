import Foundation
import KotlinSharedUI

#if canImport(FoundationModels)
import FoundationModels
#endif

public final class FoundationModelOnDeviceAI: SwiftOnDeviceAI, @unchecked Sendable {
    private init() {}

    public static let shared = FoundationModelOnDeviceAI()

    public func __isAvailable() async throws -> KotlinBoolean {
        #if canImport(FoundationModels)
        if #available(iOS 26.0, macOS 26.0, *) {
            return KotlinBoolean(bool: SystemLanguageModel.default.isAvailable)
        }
        #endif

        return KotlinBoolean(bool: false)
    }

    public func __translate(source: String, targetLanguage: String, prompt: String) async throws -> String? {
        await generateText(prompt: prompt)
    }

    public func __tldr(source: String, targetLanguage: String, prompt: String) async throws -> String? {
        await generateText(prompt: prompt)
    }

    private func generateText(prompt: String) async -> String? {
        guard ((try? await __isAvailable())?.boolValue ?? false) else {
            return nil
        }

        #if canImport(FoundationModels)
        if #available(iOS 26.0, macOS 26.0, *) {
            do {
                let session = LanguageModelSession()
                let response = try await session.respond(to: prompt)
                return response.content
            } catch {
                return nil
            }
        }
        #endif

        return nil
    }
}
