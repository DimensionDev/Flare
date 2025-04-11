import SwiftUI
import Tiercel
import Kingfisher

struct DownloadTaskCell: View {
    let task: Tiercel.DownloadTask
    let onTapAction: () -> Void
    let onShareAction: () -> Void
    
    @State private var progress: Double = 0
    @State private var statusText: String = ""
    @State private var previewImageUrl: String? = nil
    
    var body: some View {
        HStack(spacing: 12) {
        
            if let imageUrl = previewImageUrl, let url = URL(string: imageUrl) {
                KFImage(url)
                    .placeholder {
                        Rectangle()
                            .foregroundColor(Colors.Background.swiftUISecondary)
                    }
                    .resizable()
                    .aspectRatio(contentMode: .fill)
                    .frame(width: 60, height: 60)
                    .clipShape(RoundedRectangle(cornerRadius: 8))
                    .overlay(
                        task.status != .succeeded ? 
                            Rectangle()
                                .foregroundColor(Color.black.opacity(0.3))
                                .clipShape(RoundedRectangle(cornerRadius: 8)) : nil
                    )
            } else {
                 
                Rectangle()
                    .foregroundColor(Colors.Background.swiftUISecondary)
                    .frame(width: 60, height: 60)
                    .clipShape(RoundedRectangle(cornerRadius: 8))
            }
            
          
            VStack(alignment: .leading, spacing: 4) {
               
                Text(fileName)
                    .font(.system(size: 16, weight: .semibold))
                    .foregroundColor(Colors.Text.swiftUIPrimary)
                    .lineLimit(1)
                
              
                ProgressView(value: progress)
                    .progressViewStyle(LinearProgressViewStyle(tint: Colors.State.swiftUIActive))
                    .frame(height: 2)
               
                Text(statusText)
                    .font(.system(size: 14))
                    .foregroundColor(Colors.Text.swiftUISecondary)
                    .lineLimit(1)
                
               
                if task.status == .running || task.status == .suspended || task.status == .succeeded {
                    Text("\(formattedDownloadSize) / \(formattedTotalSize)")
                        .font(.system(size: 12))
                        .foregroundColor(Colors.Text.swiftUITertiary)
                        .lineLimit(1)
                }
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            
           
            Group {
                switch task.status {
                case .waiting, .failed:
                    Button(action: onTapAction) {
                        Image(systemName: "arrow.down.circle")
                            .resizable()
                            .frame(width: 24, height: 24)
                            .foregroundColor(Colors.State.swiftUIActive)
                    }
                case .running:
                    Button(action: onTapAction) {
                        Image(systemName: "pause.circle")
                            .resizable()
                            .frame(width: 24, height: 24)
                            .foregroundColor(Colors.State.swiftUIActive)
                    }
                case .suspended:
                    Button(action: onTapAction) {
                        Image(systemName: "play.circle")
                            .resizable()
                            .frame(width: 24, height: 24)
                            .foregroundColor(Colors.State.swiftUIActive)
                    }
                case .succeeded:
                    Button(action: onShareAction) {
                        Image(systemName: "square.and.arrow.up")
                            .font(.system(size: 20))
                            .foregroundColor(Colors.State.swiftUIActive)
                    }
                 
                case .removed:
                    EmptyView()
                @unknown default:
                    EmptyView()
                }
            }
            .padding(.trailing, 8)
        }
        .padding(.vertical, 8)
        .contentShape(Rectangle())
        .onAppear {
            updateUI()
            previewImageUrl = DownloadHelper.shared.getPreviewImageUrl(for: task.url.absoluteString)
            
            task.progress { _ in
                updateUI()
            }
            task.completion { _ in
                updateUI()
            }
        }
    }
    
    private var fileName: String {
        let defaultName = URL(string: task.url.absoluteString)?.lastPathComponent ?? "File"
        return task.fileName.isEmpty ? defaultName : task.fileName
    }
    
    private func updateUI() {
        progress = task.progress.fractionCompleted
        
        switch task.status {
        case .waiting:
            statusText = "Waiting..."
        case .running:
            statusText = "Downloading \(Int(progress * 100))%"
        case .suspended:
            statusText = "Paused"
        case .succeeded:
            statusText = "Completed"
        case .failed:
            statusText = "Failed - \(task.error?.localizedDescription ?? "Unknown error")"
        case .removed:
            statusText = "Removed"
        @unknown default:
            statusText = "Unknown status"
        }
    }
    
    private var formattedTotalSize: String {
        let formatter = ByteCountFormatter()
        formatter.allowedUnits = [.useKB, .useMB, .useGB]
        formatter.countStyle = .file
        return formatter.string(fromByteCount: Int64(task.progress.totalUnitCount))
    }
    
    private var formattedDownloadSize: String {
        let formatter = ByteCountFormatter()
        formatter.allowedUnits = [.useKB, .useMB, .useGB]
        formatter.countStyle = .file
        return formatter.string(fromByteCount: Int64(task.progress.completedUnitCount))
    }
} 
