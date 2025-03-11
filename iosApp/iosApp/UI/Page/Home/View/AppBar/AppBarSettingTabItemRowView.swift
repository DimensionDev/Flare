
import Kingfisher
import os
import shared
import SwiftUI

// Secondary
struct TabItemRow: View {
    let tab: FLTabItem
    let store: AppBarTabSettingStore
    let isPrimary: Bool

    // 本地状态用于防止频繁操作
    @State private var isProcessing = false

    var body: some View {
        HStack {
            if !isPrimary {
                Image(systemName: "line.3.horizontal")
                    .foregroundColor(Color.textTertiary)
            }

            switch tab.metaData.icon {
            case let .material(iconName):
                if let materialIcon = FLMaterialIcon(rawValue: iconName) {
                    materialIcon.icon
                        .foregroundColor(Color.interactiveActive)
                }
            case let .mixed(icons):
                if let firstIcon = icons.first,
                   let materialIcon = FLMaterialIcon(rawValue: firstIcon)
                {
                    materialIcon.icon
                        .foregroundColor(Color.interactiveActive)
                }
            case .avatar:
                Image(systemName: "person.circle")
                    .foregroundColor(Color.interactiveActive)
            }

            switch tab.metaData.title {
            case let .text(title):
                Text(title)
                    .foregroundColor(Color.textPrimary)
            case let .localized(key):
                Text(NSLocalizedString(key, comment: ""))
                    .foregroundColor(Color.textPrimary)
            }

            Spacer()

            if !isPrimary {
                Toggle("", isOn: Binding(
                    get: {
                        store.availableAppBarTabsItems.contains(where: { $0.key == tab.key })
                    },
                    set: { _ in
                        if !isProcessing {
                            isProcessing = true
                            store.toggleTab(tab.key)
                            // 设置短暂延迟避免频繁操作
                            DispatchQueue.main.asyncAfter(deadline: .now() + 0.5) {
                                isProcessing = false
                            }
                        }
                    }
                ))
                .tint(Color.interactiveActive)
                .disabled(isProcessing)
            }
        }
    }
}

// list
struct ListTabItemRowRow: View {
    let listId: String
    let title: String
    let store: AppBarTabSettingStore
    let onRequestEdit: (String, String) -> Void

    @State private var isProcessing = false
    @State private var currentTitle: String

    init(listId: String, title: String, store: AppBarTabSettingStore, onRequestEdit: @escaping (String, String) -> Void) {
        self.listId = listId
        self.title = title
        self.store = store
        self.onRequestEdit = onRequestEdit
        _currentTitle = State(initialValue: title)
    }

    var body: some View {
        HStack {
            Image(systemName: "line.3.horizontal")
                .foregroundColor(Color.textTertiary)

            if let listIconUrl = store.listIconUrls[listId], let url = URL(string: listIconUrl) {
                KFImage(url)
                    .placeholder {
                        RoundedRectangle(cornerRadius: 8)
                            .fill(Color.gray.opacity(0.2))
                    }
                    .resizable()
                    .aspectRatio(contentMode: .fill)
                    .frame(width: 32, height: 32)
                    .clipShape(RoundedRectangle(cornerRadius: 8))
            } else {
                ZStack {
                    RoundedRectangle(cornerRadius: 8)
                        .fill(Color.blue.opacity(0.7))
                    Image(systemName: "list.bullet")
                        .foregroundColor(.white)
                        .font(.system(size: 18))
                }
                .frame(width: 32, height: 32)
            }

            Text(store.listTitles[listId] ?? currentTitle)
                .foregroundColor(Color.textPrimary)
                .lineLimit(1)

            Spacer()

            HStack(spacing: 8) {
                Button(action: {
                    let titleToEdit = store.listTitles[listId] ?? currentTitle
                    onRequestEdit(listId, titleToEdit)
                }) {
                    Image(systemName: "pencil")
                        .foregroundColor(.blue)
                        .font(.system(size: 14))
                }
                .disabled(isProcessing)

                Toggle("", isOn: Binding(
                    get: {
                        let tabKey = "list_\(store.accountType)_\(listId)"
                        return store.availableAppBarTabsItems.contains(where: { $0.key == tabKey })
                    },
                    set: { _ in
                        if !isProcessing {
                            isProcessing = true
                            let tabKey = "list_\(store.accountType)_\(listId)"
                            store.toggleTab(tabKey)
                            // 设置短暂延迟避免频繁操作
                            DispatchQueue.main.asyncAfter(deadline: .now() + 0.5) {
                                isProcessing = false
                            }
                        }
                    }
                ))
                .tint(Color.interactiveActive)
                .disabled(isProcessing)
                .frame(width: 44)
            }
            .padding(.leading, 8)
        }
        .onReceive(store.$listTitles) { _ in

            if let updatedTitle = store.listTitles[listId] {
                currentTitle = updatedTitle
            }
        }
    }
}
