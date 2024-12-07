import SwiftUI

struct SensitiveContentButton: View {
    let hideSensitive: Bool
    let action: () -> Void
    
    var body: some View {
        ZStack() {
            if hideSensitive {
                Button(action: {
                    withAnimation {
                        action()
                    }
                }, label: {
                    Text("status_sensitive_media_show", comment: "Status media sensitive button")
                        .foregroundColor(.white) 
                        .padding(.horizontal, 16)
                        .padding(.vertical, 8)
                        .background(.ultraThinMaterial)
                        .cornerRadius(8)
                }) 
                .buttonStyle(.plain)
                .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .center)
            } else {
                Button(action: {
                    withAnimation {
                        action()
                    } 
                }, label: {
                    Image(systemName: "eye.slash")
                        .foregroundColor(.white) 
                })
                .padding(.horizontal, 16)
                .padding(.vertical, 8)
                .background(.ultraThinMaterial)
                .cornerRadius(8)
                .buttonStyle(.plain)
                .padding(.top, 16)
                .padding(.leading, 16)
                .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
            }
        }
    }
}
