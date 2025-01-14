import Foundation
import Translation
import SwiftUI

// @available(iOS 16.0, *)
// final class AppleTranslationServiceImpl: TranslationService {
//     private let targetLanguage: String
//     private var translatedText: String = ""
//     private let translationController: TranslationViewController
    
//     public init(targetLanguage: String = "en") {
//         // 处理中文特殊情况
//         if targetLanguage.starts(with: "zh") {
//             self.targetLanguage = "zh-Hans" // 使用简体中文
//         } else {
//             self.targetLanguage = targetLanguage
//         }
        
//         // 初始化翻译控制器
//         self.translationController = TranslationViewController()
        
//         print("Apple翻译初始化: 目标语言 = \(self.targetLanguage)")
//     }
    
//     public func translate(text: String) async throws -> TranslationResult {
//         print("Apple翻译开始: 原文 = \(text)")
//         print("Apple翻译目标语言: \(targetLanguage)")
        
//         // 使用 LanguageDetector 检测源语言
//         let detector = LanguageDetector()
//         let sourceLanguage = detector.detectLanguage(for: text)
        
//         print("Apple翻译检测到源语言: \(sourceLanguage)")
        
//         // 如果源语言和目标语言相同，直接返回原文
//         if sourceLanguage == targetLanguage || 
//            (sourceLanguage.starts(with: "zh") && targetLanguage.starts(with: "zh")) {
//             print("源语言和目标语言相同，无需翻译")
//             return TranslationResult(
//                 translatedText: text,
//                 sourceLanguage: sourceLanguage,
//                 targetLanguage: targetLanguage,
//                 provider: .apple
//             )
//         }
        
//         do {
//             print("开始执行翻译...")
//             // 执行翻译
//             let translatedText = try await translationController.translate(text)
//             print("翻译完成: \(translatedText)")
            
//             return TranslationResult(
//                 translatedText: translatedText,
//                 sourceLanguage: sourceLanguage,
//                 targetLanguage: targetLanguage,
//                 provider: .apple
//             )
//         } catch {
//             print("Apple翻译错误: \(error.localizedDescription)")
//             throw TranslationError.translationFailed
//         }
//     }
// }

// // 用于管理翻译的 UIViewController
// @available(iOS 16.0, *)
// private class TranslationViewController: UIViewController {
//     private var configuration: TranslationSession.Configuration?
//     private var translationTask: Task<String, Error>?
//     private var hostingController: UIHostingController<TranslationView>?
//     private var currentContinuation: CheckedContinuation<String, Error>?
//     private var currentText: String = ""
    
//     override init(nibName nibNameOrNil: String?, bundle nibBundleOrNil: Bundle?) {
//         super.init(nibName: nibNameOrNil, bundle: nibBundleOrNil)
//         self.view = UIView(frame: .zero)
//     }
    
//     required init?(coder: NSCoder) {
//         fatalError("init(coder:) has not been implemented")
//     }
    
//     func translate(_ text: String) async throws -> String {
//         print("TranslationViewController开始翻译...")
        
//         // 保存当前文本
//         currentText = text
        
//         // 取消之前的任务和清理
//         cleanup()
        
//         return try await withCheckedThrowingContinuation { continuation in
//             self.currentContinuation = continuation
            
//             DispatchQueue.main.async { [weak self] in
//                 guard let self = self else {
//                     continuation.resume(throwing: TranslationError.translationFailed)
//                     return
//                 }
                
//                 print("准备翻译视图...")
                
//                 // 创建新的配置
//                 self.configuration = .init()
                
//                 // 创建视图
//                 let translationView = TranslationView(text: text) { [weak self] result in
//                     print("翻译结果回调: \(result)")
//                     self?.handleTranslationResult(result)
//                 }
                
//                 // 创建 hosting controller
//                 let hostingController = UIHostingController(rootView: translationView)
//                 self.hostingController = hostingController
                
//                 // 添加到视图层级
//                 print("添加翻译视图到层级...")
//                 self.addChild(hostingController)
//                 self.view.addSubview(hostingController.view)
//                 hostingController.didMove(toParent: self)
                
//                 // 设置超时
//                 self.setupTimeout()
//             }
//         }
//     }
    
//     private func handleTranslationResult(_ result: String) {
//         cleanup()
//         currentContinuation?.resume(returning: result)
//         currentContinuation = nil
//     }
    
//     private func cleanup() {
//         // 取消之前的任务
//         translationTask?.cancel()
//         translationTask = nil
        
//         // 清理视图
//         DispatchQueue.main.async { [weak self] in
//             self?.hostingController?.willMove(toParent: nil)
//             self?.hostingController?.view.removeFromSuperview()
//             self?.hostingController?.removeFromParent()
//             self?.hostingController = nil
//         }
//     }
    
//     private func setupTimeout() {
//         // 设置 5 秒超时
//         DispatchQueue.main.asyncAfter(deadline: .now() + 5) { [weak self] in
//             guard let self = self,
//                   self.currentContinuation != nil else { return }
            
//             print("翻译超时")
//             self.handleTranslationResult(self.currentText) // 超时返回原文
//         }
//     }
// }

// // 用于执行翻译的 SwiftUI 视图
// @available(iOS 16.0, *)
// private struct TranslationView: View {
//     let text: String
//     let completion: (String) -> Void
//     @State private var configuration: TranslationSession.Configuration?
    
//     var body: some View {
//         Color.clear
//             .frame(width: 0, height: 0)
//             .onAppear {
//                 print("TranslationView出现，创建配置...")
//                 // 创建新的配置
//                 configuration = .init()
//             }
//             .translationTask(configuration) { session in
//                 do {
//                     print("开始执行翻译任务...")
//                     // 直接使用 session.translate
//                     let response = try await session.translate(text)
//                     print("翻译任务完成: \(response.targetText)")
//                     completion(response.targetText)
//                 } catch {
//                     print("翻译任务错误: \(error)")
//                     completion(text) // 翻译失败时返回原文
//                 }
//             }
//     }
// }

 