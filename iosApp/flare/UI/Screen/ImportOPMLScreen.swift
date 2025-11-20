import SwiftUI
import KotlinSharedUI

struct ImportOPMLScreen: View {
    @Environment(\.dismiss) private var dismiss
    let url: URL
    @StateObject private var presenter: KotlinPresenter<ImportOPMLPresenterState>
    
    init(url: URL) {
        self.url = url
        do {
            url.startAccessingSecurityScopedResource()
            let content = try String(contentsOf: url, encoding: .utf8)
            self._presenter = .init(wrappedValue: .init(presenter: ImportOPMLPresenter(opmlContent: content ?? "")))
            url.stopAccessingSecurityScopedResource()
        } catch {
            self._presenter = .init(wrappedValue: .init(presenter: ImportOPMLPresenter(opmlContent: "")))
        }
    }
    
    var body: some View {
        VStack {
            if let error = presenter.state.error {
                Text(error)
                    .foregroundColor(.red)
            } else {
                if presenter.state.importing {
                    ProgressView(value: presenter.state.progress)
                        .padding()
                }
                List {
                    ForEach(presenter.state.importedSources, id: \.url) { item in
                        UiRssView(data: item)
                    }
                }
            }
        }
        .navigationTitle("opml_import_title")
        .toolbar {
            ToolbarItem(placement: .confirmationAction) {
                Button("Done") {
                    dismiss()
                }
                .disabled(presenter.state.importing)
            }
        }
    }
}
