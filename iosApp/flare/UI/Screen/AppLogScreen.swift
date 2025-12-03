import SwiftUI
import KotlinSharedUI

struct AppLogScreen: View {
    @StateObject private var presenter = KotlinPresenter(presenter: DevModePresenter())
    @State private var selectedMessage: String? = nil
    @State private var exportedLogContent: String? = nil
    var body: some View {
        List {
            Section {
                Toggle(isOn: Binding(get: {
                    presenter.state.enabled
                }, set: { enabled in
                    presenter.state.setEnabled(value: enabled)
                }), label: {
                    Text("app_log_network_toggle")
                })
            }
            ForEach(presenter.state.messages) { message in
                Text(message)
                    .lineLimit(3)
                    .onTapGesture {
                        selectedMessage = message
                    }
            }
        }
        .toolbar {
            ToolbarItem {
                Button {
                    presenter.state.clear()
                } label: {
                    Image(.faTrash)
                }
            }
            ToolbarItem {
                Button {
                    exportedLogContent = presenter.state.printMessageToString()
                } label: {
                    Image(.faFloppyDisk)
                }
            }
        }
        .fileExporter(
            isPresented: Binding(
                get: { exportedLogContent != nil },
                set: { newValue in
                    if !newValue {
                        exportedLogContent = nil
                    }
                }
            ),
            document: TextDocument(text: exportedLogContent ?? ""),
            defaultFilename: "flare_log.txt"
        ) { result in
            exportedLogContent = nil
        }
        .navigationTitle("app_log")
        .sheet(item: $selectedMessage) { message in
            NavigationView {
                ScrollView {
                    Text(message)
                        .padding()
                        .frame(maxWidth: .infinity, alignment: .leading)
                }
                .toolbar {
                    ToolbarItem(placement: .cancellationAction) {
                        Button {
                            selectedMessage = nil
                        } label: {
                            Image(.faXmark)
                        }
                    }
                }
            }
        }
    }
}
