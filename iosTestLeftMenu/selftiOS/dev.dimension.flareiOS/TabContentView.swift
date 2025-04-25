// dev.dimension.flareiOS/TabContentView.swift
import SwiftUI
import SwiftUIIntrospect   // swiftui-introspect 的模块名

struct TabContentView: View {
    
    @Binding var selectedTopTab: Int
     
    let topTabs: [String]

    var body: some View {
        
        TabView(selection: $selectedTopTab) {
            ForEach(topTabs.indices, id: \.self) { index in
                List {
                    ForEach(0..<50) { itemIndex in
                        NavigationLink("Item \(itemIndex) for \(topTabs[index])") {
                            TweetDetailView()
                        }
                    }
                }
                .listStyle(.plain)
                .frame(maxWidth: .infinity, maxHeight: .infinity)
                .tag(index)
            }
        }
        .tabViewStyle(.page(indexDisplayMode: .never))
        .introspect(
            .tabView(style: .page),
            on: .iOS(.v17, .v18)
        ) { collectionView in
            print("[Introspect] Page TabView introspect closure entered. Selected Tab: \(selectedTopTab)")
            print("[Introspect] Received UICollectionView: \(collectionView)")

            if selectedTopTab == 0 {
                collectionView.bounces = false
                print("[Introspect] Setting collectionView.bounces to false for first tab.")
            } else {
                collectionView.bounces = true
                print("[Introspect] Setting collectionView.bounces to true for other tabs.")
            }
        }
    }
} 
