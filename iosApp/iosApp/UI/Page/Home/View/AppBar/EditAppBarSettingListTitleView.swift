import Kingfisher
import os
import shared
import SwiftUI

struct EditAppBarSettingListTitleView: View {
    @Binding var title: String
    let listId: String
    let iconUrl: String?
    var onSave: (String) -> Void
    var onCancel: () -> Void

    var body: some View {
        NavigationView {
            Form {
                Section(header: Text("List Info")) {
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
                                Image(systemName: "list.bullet")
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
                }

                Section(header: Text("List Name")) {
                    TextField("List Name", text: $title)
                        .autocapitalization(.none)
                }

                Section {
                    Text("Note: This only modifies the local display title, not the server list name.")
                        .font(.caption)
                        .foregroundColor(.secondary)
                }
            }
            .navigationTitle("Edit List Title")
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
