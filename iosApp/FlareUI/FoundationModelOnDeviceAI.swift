import Foundation
import KotlinSharedUI
import FoundationModels

public final class FoundationModelOnDeviceAI: SwiftOnDeviceAI {
    private init() {}

    public static let shared = FoundationModelOnDeviceAI()

    public func __isAvailable() async throws -> KotlinBoolean {
        if #available(iOS 26.0, *) {
            return KotlinBoolean(bool: SystemLanguageModel.default.isAvailable)
        }
        return KotlinBoolean(bool: false)
    }

    public func __translate(source: String, targetLanguage: String, prompt: String) async throws -> String? {
        return await generateText(prompt: prompt)
    }

    public func __tldr(source: String, targetLanguage: String, prompt: String) async throws -> String? {
        return await generateText(prompt: prompt)
    }

    private func generateText(prompt: String) async -> String? {
        guard ((try? await __isAvailable())?.boolValue ?? false) else {
            return nil
        }

        if #available(iOS 26.0, *) {
            do {
                let session = LanguageModelSession()
                let response = try await session.respond(to: prompt)
                return response.content
            } catch {
                return nil
            }
        }

        return nil
    }
}
