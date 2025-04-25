//
//  PagerTabView.swift
//  TwitterSwiftUI
//
//  Created by パクギョンソク on 2023/10/12.
//

import SwiftUI

struct PagerTabView<Tab: View, Content: View>: View {
    
    struct TabItem: Identifiable {
        var id: Int
        var tabView: Tab
    }
    
    private var tabs: [TabItem]
    
    // Page Content
    private var content: Content
    
    @Binding var selectedTab: Int
    
    
    init(selected: Binding<Int>,
         tabs: [Tab],
         @ViewBuilder content: @escaping () -> Content) {
        _selectedTab = selected
        self.tabs = tabs.enumerated().map { index, tab in
            TabItem(id: index, tabView: tab)}
        self.content = content()
        
    }
    
    @State private var underlineViewOffset: CGFloat = 0
    @State private var isScrolling: Bool = false
    @State private var offset: CGFloat = 0
    
    var body: some View {
        
        VStack(spacing: 0) {
            
            tabLabelsView
            
            UnderlineView()
            
            Divider()
            
            OffsetPageTabView(offset: $offset, isScrolling: $isScrolling) {
                HStack(spacing: 0) {
                    content
                        .frame(maxWidth: .infinity)
                }
            }
        }
        .onAppear {
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.1) {
                let contentOffset = screenWidth * CGFloat(selectedTab)
                offset = contentOffset
                underlineViewOffset = contentOffset / CGFloat(tabs.count)
            }
        }
        .onChange(of: offset) { _ in
            
            // SwipeでPagingする時のみ、UnderlineViewを移動させる
            if isScrolling {
                underlineViewOffset = offset / CGFloat(tabs.count)
            }
        }
        .onChange(of: isScrolling) { _ in
            if isScrolling == false {
                selectedTab = Int((offset / screenWidth).rounded())
            }
        }
    }
    
    func getOpacity(currentTab: Int) -> CGFloat {
        
        let position = self.offset / screenWidth

        let progress = abs(CGFloat(currentTab) - position)
        
        // current=1 or other=0.6
        let opacity = 1 - (min(1, progress) * 0.4)
        return opacity
    }
}

extension PagerTabView {
    
    var tabLabelsView: some View {
        HStack(spacing: 0) {
            ForEach(tabs) { item in
                item.tabView
                    .frame(maxWidth: .infinity)
                    .onTapGesture {

                        self.selectedTab = item.id
                        
                        let contentOffset = screenWidth * CGFloat(item.id)
                        // ボタンをタップした位置に PageViewをoffset
                        
                        withAnimation {
                            offset = contentOffset
                            // ボタンをタップした位置に UnderlineViewを位置させる
                            underlineViewOffset = contentOffset / CGFloat(tabs.count)
                        }
                    }
                    .opacity(getOpacity(currentTab: item.id))
            }
        }
    }

    @ViewBuilder
    func UnderlineView() -> some View {
        // underLineViewを画面いっぱいにしたい場合には paddingを削除
        let padding: CGFloat = screenWidth / CGFloat(tabs.count) - 80
        
        HStack {
            Capsule()
                .fill(Color(.systemBlue))
                .frame(width: (screenWidth  / CGFloat(tabs.count)) - padding , height: 5)
                .padding(.top, 2)
                .padding(.horizontal, padding / 2)
                .offset(x: underlineViewOffset)
            Spacer()
        }
    }
    
    var screenWidth: CGFloat {
        UIScreen.main.bounds.width
    }
}

fileprivate struct OffsetPageTabView<Content: View>: UIViewRepresentable {
    
    var content: Content
    @Binding var offset: CGFloat
    @Binding var isScrolling: Bool
    
    init(offset: Binding<CGFloat>, isScrolling: Binding<Bool>, @ViewBuilder content: @escaping () -> Content) {
        self._offset = offset
        self._isScrolling = isScrolling
        self.content = content()
    }
    
    func makeCoordinator() -> Coordinator  {
        Coordinator(parent: self)
    }
    
    func makeUIView(context: Context) -> some UIScrollView {
        let scrollView = UIScrollView()
        
        // Content(SwiftUI) 呼び出し先の { } から受け取ったViewを scrollViewに貼り付ける
        let hostView = UIHostingController(rootView: content)
        hostView.view.translatesAutoresizingMaskIntoConstraints = false
        
        let constraints = [
            hostView.view.topAnchor.constraint(equalTo: scrollView.topAnchor),
            hostView.view.leadingAnchor.constraint(equalTo: scrollView.leadingAnchor),
            hostView.view.trailingAnchor.constraint(equalTo: scrollView.trailingAnchor),
            hostView.view.bottomAnchor.constraint(equalTo: scrollView.bottomAnchor),
            hostView.view.heightAnchor.constraint(equalTo: scrollView.heightAnchor)
        ]
        
        scrollView.addSubview(hostView.view)
        scrollView.addConstraints(constraints)
        
        scrollView.isPagingEnabled = true
        scrollView.showsVerticalScrollIndicator = false
        scrollView.showsHorizontalScrollIndicator = false
        scrollView.delegate = context.coordinator
        return scrollView
    }
    
    func updateUIView(_ uiView: some UIScrollView, context: Context) {
                
        let currentOffset = uiView.contentOffset.x
        
        // 外部から offset値が変更された場合
        if currentOffset != offset {
            uiView.setContentOffset(.init(x: offset, y: 0), animated: true)
        }
    }
}

extension OffsetPageTabView {
    class Coordinator: NSObject, UIScrollViewDelegate {
        
        var parent: OffsetPageTabView
        
        init(parent: OffsetPageTabView) {
            self.parent = parent
        }
        
        func scrollViewDidScroll(_ scrollView: UIScrollView) {
            let offsetX = scrollView.contentOffset.x
            parent.offset = offsetX
            parent.isScrolling = (scrollView.isDragging || scrollView.isTracking || scrollView.isDecelerating)
        }
        
        func scrollViewDidEndDecelerating(_ scrollView: UIScrollView) {
            parent.isScrolling = false
        }

    }
}

