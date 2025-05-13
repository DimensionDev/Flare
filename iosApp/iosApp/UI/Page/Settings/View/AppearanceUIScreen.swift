import shared
import SwiftUI

// 字体设置状态
struct FontSettings {
    var fontSizeScale: Double = 1.0
    var lineSpacing: Double = 1.2
}

struct AppearanceUIScreen: View {
    @Environment(\.appSettings) private var appSettings
    @StateObject private var settingsRepository = SettingsRepository.shared
    @State private var settings: AppearanceSettings
    @State private var showFontPicker = false
    @State private var showThemeSettings = false
    @State private var presenter = AppearancePresenter()
    @State private var fontSettings = FontSettings(
        fontSizeScale: Theme.shared!.fontSizeScale,
        lineSpacing: Theme.shared!.lineSpacing
    )

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

                // 主题设置部分
                Section("settings_appearance_theme") {
                    // 主题选择器
                    HStack {
                        Text("settings_appearance_theme_color")
                        Spacer()
                        Text(Theme.shared?.selectedSet.localizedName ?? "默认")
                            .foregroundColor(.secondary)
                    }
                    .contentShape(Rectangle())
                    .onTapGesture {
                        showThemeSettings = true
                    }

                    // 暗色/亮色模式选择
                    Picker(selection: Binding(get: {
                        settings.theme
                    }, set: { value in
                        settings.theme = value
                        appSettings.update(newValue: settings)

                        // 更新统一主题系统的显示模式
                        if let mode = convertToAppDisplayMode(theme: value) {
                            Theme.shared?.appDisplayMode = mode
                        }
                    }), content: {
                        Text("settings_appearance_theme_auto")
                            .tag("system")
                        Text("settings_appearance_theme_dark")
                            .tag("dark")
                        Text("settings_appearance_theme_light")
                            .tag("light")
                    }, label: {
                        Text("settings_appearance_theme_mode")
                    })
                }
                
                // 字体设置部分
                Section("settings_appearance_font_title") {
                    // 字体类型
                    NavigationLink {
                        FlareThemeSettingsView()
                    } label: {
                        HStack {
                            Text("settings_appearance_font")
                            Spacer()
                            if let fontName = Theme.shared?.chosenFont?.fontName {
                                Text(fontName)
                                    .foregroundColor(.secondary)
                                    .lineLimit(1)
                                    .truncationMode(.middle)
                            } else {
                                Text("settings_appearance_font_system")
                                    .foregroundColor(.secondary)
                            }
                        }
                    }
                    
                    // 字体大小
                    VStack(alignment: .leading, spacing: 8) {
                        HStack {
                            Text("settings_appearance_font_size")
                            Spacer()
                            Text(String(format: "%.1fx", fontSettings.fontSizeScale))
                                .foregroundColor(.secondary)
                        }
                        
                        Slider(value: $fontSettings.fontSizeScale, in: 0.8...1.5, step: 0.1) { 
                            changed in
                            if changed {
                                var newSettings = appSettings.appearanceSettings
                                newSettings.fontSize = Double(fontSettings.fontSizeScale * 14)
                                appSettings.update(newValue: newSettings)
                                
                                Theme.shared!.fontSizeScale = fontSettings.fontSizeScale
                            }
                        }
                    }
                    
                    // 行间距
                    VStack(alignment: .leading, spacing: 8) {
                        HStack {
                            Text("settings_appearance_line_spacing")
                            Spacer()
                            Text(String(format: "%.1fx", fontSettings.lineSpacing))
                                .foregroundColor(.secondary)
                        }
                        
                        Slider(value: $fontSettings.lineSpacing, in: 1.0...2.0, step: 0.1) { 
                            changed in
                            if changed {
                                var newSettings = appSettings.appearanceSettings
                                newSettings.lineSpacing = Double(fontSettings.lineSpacing)
                                appSettings.update(newValue: newSettings)
                                
                                Theme.shared!.lineSpacing = fontSettings.lineSpacing
                            }
                        }
                    }
                }
                
                // 头像相关设置
                Section("settings_appearance_avatar") {
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
                }
                
                // 内容显示相关设置
                Section("settings_appearance_content") {
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
                }
                
                // 其他设置
                Section("settings_appearance_other") {
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
                FlareThemeSettingsView()
                    .onDisappear {
                        // 无需手动更新，FlareThemeSettingsView会直接更新Theme.shared
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

    // 从字符串主题设置转换为AppDisplayMode
    private func convertToAppDisplayMode(theme: String) -> AppDisplayMode? {
        switch theme {
        case "system":
            .auto
        case "light":
            .light
        case "dark":
            .dark
        default:
            nil
        }
    }

    private func updateFontProvider() {
        // 应用字体设置
        AppFontProvider.shared.updateFont(name: settings.fontName, size: CGFloat(settings.fontSize))

        // 同时更新Theme.shared的字体设置
        if let font = UIFont(name: settings.fontName, size: CGFloat(settings.fontSize)) {
            Theme.shared?.chosenFont = font
        }
    }
}
