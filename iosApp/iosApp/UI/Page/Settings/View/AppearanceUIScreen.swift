import shared
import SwiftUI

struct AppearanceUIScreen: View {
    @Environment(\.appSettings) private var appSettings
    @EnvironmentObject private var themeProvider: FlareThemeProvider
    @StateObject private var settingsRepository = SettingsRepository.shared
    @State private var settings: AppearanceSettings
    @State private var showFontPicker = false
    @State private var showThemeSettings = false
    @State private var presenter = AppearancePresenter()

    init() {
        let initialSettings = SettingsRepository.shared.getAppearanceSettings()
        _settings = State(initialValue: initialSettings)
    }

    var body: some View {
        ObservePresenter(presenter: presenter) { state in
            List {
                // 样例状态显示
                if case let .success(success) = onEnum(of: state.sampleStatus) {
                    StatusItemView(
                        data: success.data,
                        detailKey: nil
                    )
                }
                
                // 主题与显示设置
                Section("settings_appearance_generic") {
                    // 主题选择器
                    HStack {
                        Text("settings_appearance_theme_color")
                        Spacer()
                        Text(themeProvider.flareTheme.displayName)
                            .foregroundColor(.secondary)
                    }
                    .contentShape(Rectangle())
                    .onTapGesture {
                        showThemeSettings = true
                    }
                    
                    // 暗色/亮色模式选择
                    Picker(selection: Binding(get: {
                        appSettings.appearanceSettings.theme
                    }, set: { value in
                        appSettings.update(newValue: appSettings.appearanceSettings.changing(path: \.theme, to: value))
                    }), content: {
                        Text("settings_appearance_theme_auto")
                            .tag(Theme.auto)
                        Text("settings_appearance_theme_dark")
                            .tag(Theme.dark)
                        Text("settings_appearance_theme_light")
                            .tag(Theme.light)
                    }, label: {
                        Text("settings_appearance_theme_mode")
                    })
                    
                    // 字体设置入口
                    HStack {
                        Text("settings_appearance_font")
                        Spacer()
                        Text(settings.fontName)
                            .foregroundColor(.secondary)
                    }
                    .contentShape(Rectangle())
                    .onTapGesture {
                        showFontPicker = true
                    }
                    
                    // 头像形状
                    Picker(selection: Binding(get: {
                        appSettings.appearanceSettings.avatarShape
                    }, set: { value in
                        appSettings.update(newValue: appSettings.appearanceSettings.changing(path: \.avatarShape, to: value))
                    }), content: {
                        Text("settings_appearance_avatar_shape_round")
                            .tag(AvatarShape.circle)
                        Text("settings_appearance_avatar_shape_square")
                            .tag(AvatarShape.square)
                    }, label: {
                        Text("settings_appearance_avatar_shape")
                    })
                    
                    // 显示操作按钮
                    Toggle(isOn: Binding(get: {
                        appSettings.appearanceSettings.showActions
                    }, set: { value in
                        appSettings.update(newValue: appSettings.appearanceSettings.changing(path: \.showActions, to: value))
                    })) {
                        Text("settings_appearance_show_actions")
                    }
                    
                    // 显示数字
                    if appSettings.appearanceSettings.showActions {
                        Toggle(isOn: Binding(get: {
                            appSettings.appearanceSettings.showNumbers
                        }, set: { value in
                            appSettings.update(newValue: appSettings.appearanceSettings.changing(path: \.showNumbers, to: value))
                        })) {
                            Text("settings_appearance_show_numbers")
                        }
                    }
                    
                    // 显示链接预览
                    Toggle(isOn: Binding(get: {
                        appSettings.appearanceSettings.showLinkPreview
                    }, set: { value in
                        appSettings.update(newValue: appSettings.appearanceSettings.changing(path: \.showLinkPreview, to: value))
                    })) {
                        Text("settings_appearance_show_link_previews")
                    }
                    
                    // 显示媒体内容
                    Toggle(isOn: Binding(get: {
                        appSettings.appearanceSettings.showMedia
                    }, set: { value in
                        appSettings.update(newValue: appSettings.appearanceSettings.changing(path: \.showMedia, to: value))
                    })) {
                        Text("settings_appearance_show_media")
                    }
                    
                    // 显示敏感内容
                    if appSettings.appearanceSettings.showMedia {
                        Toggle(isOn: Binding(get: {
                            appSettings.appearanceSettings.showSensitiveContent
                        }, set: { value in
                            appSettings.update(newValue: appSettings.appearanceSettings.changing(path: \.showSensitiveContent, to: value))
                        })) {
                            Text("settings_appearance_show_cw_img")
                        }
                    }
                    
                    // 全屏左滑返回
                    Toggle(isOn: Binding(get: {
                        appSettings.appearanceSettings.enableFullSwipePop
                    }, set: { value in
                        appSettings.update(newValue: appSettings.appearanceSettings.changing(path: \.enableFullSwipePop, to: value))
                    })) {
                        Text("settings_appearance_full_swipe_back")
                    }
                    
                    // 强制暗色模式
                    Toggle(isOn: Binding(get: {
                        appSettings.appearanceSettings.forceUseSystemAsDarkMode
                    }, set: { value in
                        appSettings.update(newValue: appSettings.appearanceSettings.changing(path: \.forceUseSystemAsDarkMode, to: value))
                    })) {
                        Text("settings_appearance_force_dark_mode")
                    }
                    
                    // 自动翻译
                    Toggle(isOn: Binding(get: {
                        appSettings.appearanceSettings.autoTranslate
                    }, set: { value in
                        appSettings.update(newValue: appSettings.appearanceSettings.changing(path: \.autoTranslate, to: value))
                    })) {
                        Text("settings_appearance_auto_translate")
                    }
                }
                .buttonStyle(.plain)
                .navigationTitle("settings_appearance_title")
            }
            #if os(macOS)
            .toggleStyle(.switch)
            .pickerStyle(.segmented)
            #endif
            .sheet(isPresented: $showThemeSettings) {
                FlareThemeSettingsView(settings: $settings)
                    .onDisappear {
                        // 同步设置
                        appSettings.update(newValue: appSettings.appearanceSettings.changing(path: \.theme, to: settings.theme))
                        updateThemeProvider()
                    }
                    .presentationDetents([.medium, .large])
                    .presentationDragIndicator(.visible)
            }
            .sheet(isPresented: $showFontPicker) {
                FontPickerViewRepresentable(selectedFontName: $settings.fontName)
                    .onDisappear {
                        // 同步设置
                        appSettings.update(newValue: appSettings.appearanceSettings.changing(path: \.fontName, to: settings.fontName))
                        appSettings.update(newValue: appSettings.appearanceSettings.changing(path: \.fontSize, to: settings.fontSize))
                        updateFontProvider()
                    }
            }
        }
    }
    
    private func updateThemeProvider() {
        // 应用主题（如果已更改）
        if themeProvider.flareTheme.rawValue != settings.theme,
           let newTheme = FlareTheme(rawValue: settings.theme) {
            themeProvider.flareTheme = newTheme
        }
    }
    
    private func updateFontProvider() {
        // 应用字体设置
        AppFontProvider.shared.updateFont(name: settings.fontName, size: CGFloat(settings.fontSize))
    }
}
