import Kingfisher
import os
import shared
import SwiftUI

struct AppBarSettingEditListTitleView: View {
    @Binding var title: String
    let listId: String
    let iconUrl: String?
    let isBlueskyFeed: Bool
    var onSave: (String) -> Void
    var onCancel: () -> Void
    @Environment(FlareTheme.self) private var theme

    init(title: Binding<String>, listId: String, iconUrl: String?, onSave: @escaping (String) -> Void, onCancel: @escaping () -> Void, isBlueskyFeed: Bool = false) {
        _title = title
        self.listId = listId
        self.iconUrl = iconUrl
        self.onSave = onSave
        self.onCancel = onCancel
        self.isBlueskyFeed = isBlueskyFeed
    }

    var body: some View {
        NavigationView {
            Form {
                Section(header: Text(isBlueskyFeed ? "Feed Info" : "List Info")) {
                    HStack {
                        if let iconUrl, let url = URL(string: iconUrl) {
                            KFImage(url)
                                .placeholder {
                                    RoundedRectangle(cornerRadius: 8)
                                        .fill(Color.gray.opacity(0.2))
                                }
                                .resizable()
                                .aspectRatio(contentMode: .fill)
                                .frame(width: 50, height: 50)
                                .clipShape(RoundedRectangle(cornerRadius: 8))
                        } else {
                            ZStack {
                                RoundedRectangle(cornerRadius: 8)
                                    .fill(Color.blue.opacity(0.7))
                                Image(systemName: isBlueskyFeed ? "square.grid.2x2" : "list.bullet")
                                    .foregroundColor(.white)
                                    .font(.system(size: 24))
                            }
                            .frame(width: 50, height: 50)
                        }

                        VStack(alignment: .leading, spacing: 4) {
                            Text("ID: \(listId)")
                                .font(.caption)
                                .foregroundColor(.secondary)
                        }
                    }
                    .padding(.vertical, 8)
                }.listRowBackground(theme.primaryBackgroundColor)

                Section(header: Text(isBlueskyFeed ? "Feed Name" : "List Name")) {
                    TextField(isBlueskyFeed ? "Feed Name" : "List Name", text: $title)
                        .autocapitalization(.none)
                }.listRowBackground(theme.primaryBackgroundColor)

                Section {
                    Text("Note: This only modifies the local display title, not the server \(isBlueskyFeed ? "feed" : "list") name.")
                        .font(.caption)
                        .foregroundColor(.secondary)
                }.listRowBackground(theme.primaryBackgroundColor)
            }
            .scrollContentBackground(.hidden)
            .background(theme.secondaryBackgroundColor)
            .navigationTitle(isBlueskyFeed ? "Edit Feed Title" : "Edit List Title")
            .navigationBarItems(
                leading: Button("Cancel") {
                    onCancel()
                },
                trailing: Button("Save") {
                    onSave(title)
                }
                .disabled(title.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
            )
        }
    }
}
