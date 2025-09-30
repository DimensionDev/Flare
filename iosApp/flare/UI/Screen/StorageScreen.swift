import SwiftUI
import KotlinSharedUI
import Kingfisher

struct StorageScreen: View {
    @StateObject private var presenter = KotlinPresenter(presenter: StoragePresenter())
    @State private var showDatabaseClearAlert = false
    @State private var showImageClearAlert = false
    var body: some View {
        List {
            Button(role: .destructive) {
                showImageClearAlert = true
            } label: {
                Label {
                    Text("storage_clear_image_cache")
                } icon: {
                    Image("fa-image")
                }
            }
            .alert("storage_clear_image_cache_confirm", isPresented: $showImageClearAlert) {
                Button("Cancel", role: .cancel) {
                    showImageClearAlert = false
                }
                Button("Ok", role: .destructive) {
                    KingfisherManager.shared.cache.clearMemoryCache()
                    KingfisherManager.shared.cache.clearDiskCache {
                    }
                }
            }
            
            
            Button(role: .destructive) {
//                presenter.state.clearCache()
                showDatabaseClearAlert = true
            } label: {
                Label {
                    Text("storage_clear_database_cache\(presenter.state.userCount) \(presenter.state.statusCount)")
                } icon: {
                    Image("fa-database")
                }
            }
            .alert("storage_clear_database_cache_confirm", isPresented: $showDatabaseClearAlert) {
                Button("Cancel", role: .cancel) {
                    showDatabaseClearAlert = false
                }
                Button("Ok", role: .destructive) {
                    presenter.state.clearCache()
                }
            }
        }
        .navigationTitle("storage_title")
    }
}
