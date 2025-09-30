import SwiftUI

struct ListEmptyView: View {
    var body: some View {
        VStack {
            Image(systemName: "questionmark.text.page")
                .resizable()
                .scaledToFit()
                .frame(width: 64, height: 64)
            Text("list_empty_title")
                .multilineTextAlignment(.center)
                .font(.headline)
        }
        .padding()
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .center)
    }
}
