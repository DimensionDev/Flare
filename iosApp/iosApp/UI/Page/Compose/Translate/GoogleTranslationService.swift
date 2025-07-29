import Foundation

public enum GoogleTranslationError: LocalizedError {
    case invalidURL
    case invalidResponse
    case languageDetectionFailed
    case serviceUnavailable
    case networkError(Error)

    public var errorDescription: String? {
        switch self {
        case .invalidURL:
            "Invalid translation service URL"
        case .invalidResponse:
            "Invalid response from translation service"
        case .languageDetectionFailed:
            "Failed to detect language"
        case .serviceUnavailable:
            "Translation service is not available"
        case let .networkError(error):
            "Network error: \(error.localizedDescription)"
        }
    }
}

public class GoogleTranslationService: TranslationService {
    private let baseUrl = "https://translate.google.com/translate_a/single"
    private let cache = NSCache<NSString, TranslationResult>()
    private let targetLanguage: String

    public init(targetLanguage: String = "en") {
        self.targetLanguage = targetLanguage
    }

    public func translate(text: String) async throws -> TranslationResult {
        let cacheKey = "\(text)_\(targetLanguage)" as NSString
        if let cachedResult = cache.object(forKey: cacheKey) {
            return cachedResult
        }

        var components = URLComponents(string: baseUrl)!
        components.queryItems = [
            URLQueryItem(name: "client", value: "gtx"),
            URLQueryItem(name: "sl", value: "auto"),
            URLQueryItem(name: "tl", value: targetLanguage),
            URLQueryItem(name: "dt", value: "t"),
            URLQueryItem(name: "q", value: text),
            URLQueryItem(name: "ie", value: "UTF-8"),
            URLQueryItem(name: "oe", value: "UTF-8")
        ]

        guard let url = components.url else {
            throw GoogleTranslationError.invalidURL
        }

        // print("翻译URL: \(url.absoluteString)")

        do {
            let (data, response) = try await URLSession.shared.data(from: url)

            if let httpResponse = response as? HTTPURLResponse {
                // print("HTTP状态码: \(httpResponse.statusCode)")
            }

            // print("原始响应数据: \(String(data: data, encoding: .utf8) ?? "无法解码")")

            let json = try JSONSerialization.jsonObject(with: data) as? [Any]

            guard let translationArray = json?[0] as? [[Any]] else {
                throw GoogleTranslationError.invalidResponse
            }

            var translatedText = ""
            var originalText = ""

            for translation in translationArray {
                if let trans = translation[0] as? String {
                    if !translatedText.isEmpty {
                        translatedText += "\n"
                    }
                    translatedText += trans
                }
                if let orig = translation[1] as? String {
                    if !originalText.isEmpty {
                        originalText += "\n"
                    }
                    originalText += orig
                }
            }

            let detectedLanguage = (json?.last as? [Any])?[0] as? [String]
            let languageCode = detectedLanguage?.first

            // print("翻译结果: \(translatedText)")
            // print("原文: \(originalText)")
            // print("检测到的语言: \(languageCode ?? "unknown")")

            guard !translatedText.isEmpty else {
                throw GoogleTranslationError.invalidResponse
            }

            let result = TranslationResult(
                translatedText: translatedText,
                sourceLanguage: languageCode ?? "auto",
                targetLanguage: targetLanguage,
                provider: .google
            )

            cache.setObject(result, forKey: cacheKey)
            return result
        } catch {
            // print("翻译错误: \(error)")
            throw GoogleTranslationError.networkError(error)
        }
    }
}
