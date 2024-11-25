import SwiftUI

struct TabBarView: View {
    @Binding var selection: ProfileTabItem
    
    var body: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 24) {
                ForEach(ProfileTabItem.allCases, id: \.self) { tab in
                    VStack(spacing: 8) {
                        Image(systemName: tab.imageName)
                            .foregroundColor(selection == tab ? .primary : .secondary)
                        
                        Text(tab.title)
                            .font(.subheadline)
                            .foregroundColor(selection == tab ? .primary : .secondary)
                        
                        if selection == tab {
                            Rectangle()
                                .frame(height: 2)
                                .foregroundColor(.blue)
                        } else {
                            Rectangle()
                                .frame(height: 2)
                                .foregroundColor(.clear)
                        }
                    }
                    .onTapGesture {
                        withAnimation {
                            selection = tab
                        }
                    }
                }
            }
            .padding(.horizontal)
        }
        .background(
            Color(.systemBackground)
                .shadow(color: .black.opacity(0.1), radius: 8, y: 4)
        )
    }
}
