import SwiftUI
import shared
import Kingfisher

struct StorageScreen: View {
    @State private var presenter = StoragePresenter()
    @State private var imageCacheSize: String = "Calculating..."
    @State private var isCleaningCache = false
    
    private func calculateImageCacheSize() {
        ImageCache.default.calculateDiskStorageSize { result in
            switch result {
            case .success(let size):
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
                Section("Database Cache") {
                    Button(role: .destructive) {
                        state.clearCache()
                    } label: {
                        HStack(alignment: .center) {
                            Image(systemName: "trash")
                                .font(.title)
                            Spacer()
                                .frame(width: 16)
                            VStack(alignment: .leading) {
                                Text("Clear Database Cache")
                                Text(
                                    "\(state.userCount) users, \(state.statusCount) statuses"
                                )
                                    .font(.caption)
                            }
                        }
                    }
                    .buttonStyle(.borderless)
                }
                
                // Image Cache Section
                Section("Image Cache") {
                    Button(role: .destructive) {
                        clearImageCache()
                    } label: {
                        HStack {
                            Image(systemName: "photo.circle")
                                .font(.title)
                            Spacer()
                                .frame(width: 16)
                            VStack(alignment: .leading) {
                                Text("Clear Image Cache")
                                Text("Current size: \(imageCacheSize)")
                                    .font(.caption)
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
            .navigationTitle("Storage")
            .onAppear {
                calculateImageCacheSize()
            }
        }
    }
}

#Preview {
    StorageScreen()
}
