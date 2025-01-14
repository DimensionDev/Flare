import SwiftUI
import shared

struct ProfileTabBarView: View {
   let tabs: [FLTabItem]
   @Binding var selectedTab: Int
   let onTabSelected: (Int) -> Void
   
   var body: some View {
       ScrollView(.horizontal, showsIndicators: false) {
           HStack(spacing: 24) {
               ForEach(Array(tabs.enumerated()), id: \.element.key) { index, tab in
                   Button(action: {
                       onTabSelected(index)
                   }) {
                       VStack(spacing: 4) {
                           switch tab.metaData.title {
                           case .text(let title):
                               Text(title)
                                   .font(.system(size: 16))
                                   .foregroundColor(selectedTab == index ? .primary : .gray)
                                   .fontWeight(selectedTab == index ? .semibold : .regular)
                           case .localized(let key):
                               Text(NSLocalizedString(key, comment: ""))
                                   .font(.system(size: 16))
                                   .foregroundColor(selectedTab == index ? .primary : .gray)
                                   .fontWeight(selectedTab == index ? .semibold : .regular)
                           }
                           
                           Rectangle()
                               .fill(selectedTab == index ? Color.accentColor : Color.clear)
                               .frame(height: 2)
                               .frame(width: 24)
                       }
                   }
               }
           }
           .padding(.horizontal)
       }
       .frame(height: 44)
       .padding(.top, 18)
       .background(Colors.Background.swiftUIPrimary)
   }
} 
