import Kingfisher
import shared
import SwiftUI

struct StorageScreen: View {
    @State private var presenter = StoragePresenter()
    @State private var imageCacheSize: String = "Calculating..."
    @State private var isCleaningCache = false

    private func calculateImageCacheSize() {
        ImageCache.default.calculateDiskStorageSize { result in
            switch result {
            case let .success(size):
                let sizeInMB = Double(size) / 1024.0 / 1024.0
                imageCacheSize = String(format: "%.1f MB", sizeInMB)
            case .failure:
                imageCacheSize = "Failed to calculate"
            }
        }
    }

    private func clearImageCache() {
        isCleaningCache = true
        ImageCache.default.clearDiskCache {
            ImageCache.default.clearMemoryCache()
            calculateImageCacheSize()
            isCleaningCache = false
        }
    }

    var body: some View {
        ObservePresenter(presenter: presenter) { state in
            List {
                // Database Cache Section
                Section("settings_storage_clear_database") {
                    Button(role: .destructive) {
                        state.clearCache()
                    } label: {
                        HStack(alignment: .center) {
                            Image(systemName: "trash")
                                .font(.title)
                            Spacer()
                                .frame(width: 16)
                            VStack(alignment: .leading) {
                                Text(String(format: NSLocalizedString("settings_storage_clear_database_description", comment: ""), String(state.userCount), String(state.statusCount)))
                            }
                        }
                    }
                    .buttonStyle(.borderless)
                }

                // Image Cache Section
                Section("settings_storage_clear_image_cache") {
                    Button(role: .destructive) {
                        clearImageCache()
                    } label: {
                        HStack {
                            Image(systemName: "photo.circle")
                                .font(.title)
                            Spacer()
                                .frame(width: 16)
                            VStack(alignment: .leading) {
                                Text(String(format: NSLocalizedString("settings_storage_clear_image_cache_description", comment: ""), String(imageCacheSize)))
                                // Text("Current size: \(imageCacheSize)")
                                //    .font(.caption)
                            }
                            if isCleaningCache {
                                Spacer()
                                ProgressView()
                            }
                        }
                    }
                    .buttonStyle(.borderless)
                    .disabled(isCleaningCache)
                }
            }
            .navigationTitle("settings_storage_clear_database")
            .onAppear {
                calculateImageCacheSize()
            }
        }
    }
}
