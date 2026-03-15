import Dispatch
import Foundation

#if canImport(FoundationModels)
import FoundationModels
#endif

private enum FlareFoundationModelsError: Int32 {
    case ok = 0
    case invalidArgument = 1
    case invalidPromptEncoding = 2
    case unsupportedPlatform = 3
    case modelUnavailable = 4
    case generationFailed = 5
}

private struct MissingResultError: LocalizedError {
    var errorDescription: String? {
        "Foundation Models task finished without producing a result."
    }
}

private func duplicatedCString(_ value: String) -> UnsafeMutablePointer<CChar>? {
    strdup(value)
}

private func setError(
    code: FlareFoundationModelsError,
    message: String,
    errorCode: UnsafeMutablePointer<Int32>?,
    errorMessage: UnsafeMutablePointer<UnsafeMutablePointer<CChar>?>?
) {
    errorCode?.pointee = code.rawValue
    errorMessage?.pointee = duplicatedCString(message)
}

private func clearError(
    errorCode: UnsafeMutablePointer<Int32>?,
    errorMessage: UnsafeMutablePointer<UnsafeMutablePointer<CChar>?>?
) {
    errorCode?.pointee = FlareFoundationModelsError.ok.rawValue
    errorMessage?.pointee = nil
}

private func waitForTask<T>(_ operation: @escaping @Sendable () async throws -> T) throws -> T {
    let semaphore = DispatchSemaphore(value: 0)
    var outcome: Result<T, Error>?

    Task {
        do {
            let value = try await operation()
            outcome = .success(value)
        } catch {
            outcome = .failure(error)
        }
        semaphore.signal()
    }

    semaphore.wait()
    return try outcome?.get() ?? { throw MissingResultError() }()
}

#if canImport(FoundationModels)
@available(macOS 26.0, *)
private func generateText(prompt: String) throws -> String {
    try waitForTask {
        let session = LanguageModelSession()
        let response = try await session.respond(to: prompt)
        return response.content
    }
}
#endif

@_cdecl("flare_foundation_models_is_supported")
public func flare_foundation_models_is_supported() -> Int32 {
    #if os(macOS) && canImport(FoundationModels)
    return 1
    #else
    return 0
    #endif
}

@_cdecl("flare_foundation_models_is_available")
public func flare_foundation_models_is_available(
    _ errorCode: UnsafeMutablePointer<Int32>?,
    _ errorMessage: UnsafeMutablePointer<UnsafeMutablePointer<CChar>?>?
) -> Int32 {
    #if os(macOS) && canImport(FoundationModels)
    if #available(macOS 26.0, *) {
        clearError(errorCode: errorCode, errorMessage: errorMessage)
        return SystemLanguageModel.default.isAvailable ? 1 : 0
    } else {
        setError(
            code: .unsupportedPlatform,
            message: "Foundation Models requires macOS 26.0 or newer.",
            errorCode: errorCode,
            errorMessage: errorMessage,
        )
        return 0
    }
    #else
    setError(
        code: .unsupportedPlatform,
        message: "Foundation Models is not available in this build environment.",
        errorCode: errorCode,
        errorMessage: errorMessage,
    )
    return 0
    #endif
}

@_cdecl("flare_foundation_models_generate")
public func flare_foundation_models_generate(
    _ prompt: UnsafePointer<CChar>?,
    _ errorCode: UnsafeMutablePointer<Int32>?,
    _ errorMessage: UnsafeMutablePointer<UnsafeMutablePointer<CChar>?>?
) -> UnsafeMutablePointer<CChar>? {
    guard let prompt else {
        setError(
            code: .invalidArgument,
            message: "Prompt pointer must not be null.",
            errorCode: errorCode,
            errorMessage: errorMessage,
        )
        return nil
    }

    guard let promptString = String(validatingUTF8: prompt) else {
        setError(
            code: .invalidPromptEncoding,
            message: "Prompt must be valid UTF-8.",
            errorCode: errorCode,
            errorMessage: errorMessage,
        )
        return nil
    }

    #if os(macOS) && canImport(FoundationModels)
    guard !promptString.isEmpty else {
        setError(
            code: .invalidArgument,
            message: "Prompt must not be empty.",
            errorCode: errorCode,
            errorMessage: errorMessage,
        )
        return nil
    }

    guard #available(macOS 26.0, *) else {
        setError(
            code: .unsupportedPlatform,
            message: "Foundation Models requires macOS 26.0 or newer.",
            errorCode: errorCode,
            errorMessage: errorMessage,
        )
        return nil
    }

    guard SystemLanguageModel.default.isAvailable else {
        setError(
            code: .modelUnavailable,
            message: "The on-device Foundation model is not available on this Mac.",
            errorCode: errorCode,
            errorMessage: errorMessage,
        )
        return nil
    }

    do {
        let content = try generateText(prompt: promptString)
        clearError(errorCode: errorCode, errorMessage: errorMessage)
        return duplicatedCString(content)
    } catch {
        setError(
            code: .generationFailed,
            message: "Foundation Models generation failed: \(error.localizedDescription)",
            errorCode: errorCode,
            errorMessage: errorMessage,
        )
        return nil
    }
    #else
    setError(
        code: .unsupportedPlatform,
        message: "Foundation Models is not available in this build environment.",
        errorCode: errorCode,
        errorMessage: errorMessage,
    )
    return nil
    #endif
}

@_cdecl("flare_foundation_models_free_string")
public func flare_foundation_models_free_string(_ value: UnsafeMutablePointer<CChar>?) {
    guard let value else {
        return
    }
    free(value)
}
