import SwiftUI
import shared

struct ComposeScreen: View {
    @State var viewModel: ComposeViewModel
    init(status: ComposeStatus? = nil) {
        viewModel = ComposeViewModel(status: status)
    }
    var body: some View {
        HStack(alignment: .top) {
            VStack(alignment: .leading) {
                if viewModel.enableCW {
                    TextField(text: $viewModel.cw) {
                        Text("Content Warning")
                    }
                }
                TextField(text: $viewModel.text) {
                    Text("What's happening?")
                }
                Spacer()
            }
        }
        .padding()
        .toolbarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .confirmationAction) {
                Button(action: {}, label: {
                    Image(systemName: "paperplane")
                })
            }
            ToolbarItem(placement: .cancellationAction) {
                Button(action: {}, label: {
                    Image(systemName: "xmark")
                })
            }
            ToolbarItem(placement: .principal) {
                Text("Compose")
            }
            ToolbarItem(placement: .bottomBar) {
                ScrollView(.horizontal) {
                    HStack {
                        Button(action: {}, label: {
                            Image(systemName: "photo")
                        })
                        Button(action: {}, label: {
                            Image(systemName: "list.bullet")
                        })
                        Button(action: {}, label: {
                            Image(systemName: "globe")
                        })
                        Button(action: {
                            withAnimation {
                                viewModel.toggleCW()
                            }
                        }, label: {
                            Image(systemName: "exclamationmark.triangle")
                        })
                        Button(action: {}, label: {
                            Image(systemName: "face.smiling")
                        })
                        Spacer()
                    }
                }
            }
        }
    }
}

#Preview {
    NavigationStack {
        ComposeScreen()
    }
}
