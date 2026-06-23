import FlareAppleCore
import FlareAppleUI
@preconcurrency import KotlinSharedUI
import SwiftUI
@preconcurrency import WebKit

struct RssScreen: View {
    @StateObject private var presenter = KotlinPresenter(presenter: RssListWithTabsPresenter())
    @State private var selectedSourceId: Int?
    @State private var selectedTimelineItemID: String?
    @State private var selectedTimelineItem: RssTimelineItemSelection?
    @State private var showAddSheet = false
    @State private var selectedEditItem: EditRssSelection?
    @State private var importOPMLSelection: OPMLImportSelection?
    @State private var exportedOPMLContent: String?

    var body: some View {
        NavigationSplitView {
            List(selection: $selectedSourceId) {
                ForEach(presenter.state.sources, id: \.id) { item in
                    RssSourceSidebarRow(
                        item: item,
                        onEdit: {
                            selectedEditItem = EditRssSelection(item)
                        },
                        onDelete: {
                            presenter.state.delete(id: Int32(item.id))
                        }
                    )
                    .tag(Int(item.id))
                    .contextMenu {
                        Button {
                            selectedEditItem = EditRssSelection(item)
                        } label: {
                            Label {
                                Text("macos_action_edit")
                            } icon: {
                                Image(fontAwesome: .pen)
                            }
                        }

                        Button(role: .destructive) {
                            presenter.state.delete(id: Int32(item.id))
                        } label: {
                            Label {
                                Text("delete_button")
                            } icon: {
                                Image(fontAwesome: .trash)
                            }
                        }
                    }
                }
            }
            .navigationTitle("rss_title")
            .listStyle(.sidebar)
            .navigationSplitViewColumnWidth(min: 220, ideal: 280, max: 360)
            .toolbar {
                ToolbarItemGroup(placement: .navigation) {
                    Button {
                        showAddSheet = true
                    } label: {
                        Label {
                            Text("add_rss_title")
                        } icon: {
                            Image(fontAwesome: .plus)
                        }
                    }
                    .help(String(localized: "add_rss_title", bundle: .main))

                    if !presenter.state.sources.isEmpty {
                        Button {
                            Task {
                                exportedOPMLContent = try? await ExportOPMLPresenter().export()
                            }
                        } label: {
                            Label {
                                Text("macos_action_export")
                            } icon: {
                                Image(fontAwesome: .fileExport)
                            }
                        }
                        .help(String(localized: "macos_action_export", bundle: .main))
                        .fileExporter(
                            isPresented: Binding(
                                get: { exportedOPMLContent != nil },
                                set: { newValue in
                                    if !newValue {
                                        exportedOPMLContent = nil
                                    }
                                }
                            ),
                            document: OPMLFile(initialText: exportedOPMLContent ?? ""),
                            defaultFilename: "flare_export.opml"
                        ) { _ in
                            exportedOPMLContent = nil
                        }
                    }
                }
            }
        } content: {
            if let selectedSource {
                RssTimelineColumn(
                    source: selectedSource,
                    tabItem: presenter.state.timelineTabItem(item: selectedSource),
                    selectedItemID: $selectedTimelineItemID,
                    selectedItem: $selectedTimelineItem
                )
                    .id(Int(selectedSource.id))
            } else if presenter.state.sources.isEmpty {
                RssEmptyState {
                    showAddSheet = true
                }
            } else {
                RssDetailPlaceholder()
            }
        } detail: {
            RssTimelineItemDetailScaffold(selection: selectedTimelineItem)
        }
        .onAppear(perform: reconcileSelection)
        .onChange(of: sourceIds) { _, _ in
            reconcileSelection()
        }
        .onChange(of: selectedSourceId) { _, _ in
            selectedTimelineItemID = nil
            selectedTimelineItem = nil
        }
        .sheet(isPresented: $showAddSheet) {
            NavigationStack {
                EditRssSheet(id: nil, onImportOPML: beginImport)
            }
            .frame(minWidth: 520, minHeight: 560)
        }
        .sheet(item: $selectedEditItem) { item in
            NavigationStack {
                EditRssSheet(
                    id: item.sourceId,
                    initialUrl: item.url,
                    initialDisplayMode: item.displayMode,
                    onImportOPML: beginImport
                )
            }
            .frame(minWidth: 520, minHeight: 560)
        }
        .sheet(item: $importOPMLSelection) { selection in
            NavigationStack {
                ImportOPMLScreen(url: selection.url)
            }
            .frame(minWidth: 520, minHeight: 480)
        }
    }

    private var sourceIds: [Int] {
        presenter.state.sources.map { Int($0.id) }
    }

    private var selectedSource: UiRssSource? {
        guard let selectedSourceId else {
            return nil
        }

        return presenter.state.sources.first { Int($0.id) == selectedSourceId }
    }

    private func reconcileSelection() {
        if let selectedSourceId, sourceIds.contains(selectedSourceId) {
            return
        }

        selectedSourceId = sourceIds.first
    }

    private func beginImport(_ url: URL) {
        showAddSheet = false
        selectedEditItem = nil
        importOPMLSelection = OPMLImportSelection(url: url)
    }
}

private struct RssTimelineColumn: View {
    let source: UiRssSource
    let tabItem: UiTimelineTabItem
    @Binding var selectedItemID: String?
    @Binding var selectedItem: RssTimelineItemSelection?

    @Environment(\.timelineAppearance) private var timelineAppearance
    @StateObject private var presenter: KotlinPresenter<TimelineItemPresenterState>

    init(
        source: UiRssSource,
        tabItem: UiTimelineTabItem,
        selectedItemID: Binding<String?>,
        selectedItem: Binding<RssTimelineItemSelection?>
    ) {
        self.source = source
        self.tabItem = tabItem
        _selectedItemID = selectedItemID
        _selectedItem = selectedItem
        _presenter = StateObject(wrappedValue: KotlinPresenter(presenter: TimelineItemPresenter(timelineTabItem: tabItem)))
    }

    var body: some View {
        RssTimelineItemList(
            data: presenter.state.listState,
            selectedItemID: $selectedItemID,
            selectedItem: $selectedItem,
            key: presenter.key
        )
        .environment(\.timelineAppearance, tabItem.resolveTimelineAppearance(base: timelineAppearance))
        .overlay(alignment: .top) {
            if presenter.state.isRefreshing {
                ProgressView()
                    .progressViewStyle(.linear)
                    .ignoresSafeArea()
                    .padding(.horizontal)
            }
        }
        .refreshable {
            try? await presenter.state.refreshSuspend()
        }
        .navigationTitle(sourceTitle)
        .toolbar {
            ToolbarItem(placement: .primaryAction) {
                Button {
                    presenter.state.refreshSync()
                } label: {
                    Label {
                        Text("Refresh")
                    } icon: {
                        Image(fontAwesome: .arrowsRotate)
                    }
                }
            }
        }
        .navigationSplitViewColumnWidth(min: 360, ideal: 440, max: 620)
    }

    private var sourceTitle: String {
        if let title = source.title, !title.isEmpty {
            return title
        }
        return source.host
    }
}

private struct RssTimelineItemList: View {
    let data: PagingState<UiTimelineV2>
    @Binding var selectedItemID: String?
    @Binding var selectedItem: RssTimelineItemSelection?
    let key: String

    private let loadingCount = 6

    var body: some View {
        List(selection: $selectedItemID) {
            content
        }
        .listStyle(.plain)
        .id(key)
        .onChange(of: selectedItemID) { _, newValue in
            syncSelection(id: newValue)
        }
    }

    @ViewBuilder
    private var content: some View {
        switch onEnum(of: data) {
        case .empty:
            ListEmptyView()
                .padding()
                .frame(maxWidth: .infinity)
        case .error(let error):
            ListErrorView(error: error.error) {
                _ = error.onRetry()
            }
            .padding()
            .frame(maxWidth: .infinity)
        case .loading:
            ForEach(0..<loadingCount, id: \.self) { index in
                RssTimelineItemRow(
                    row: RssTimelineRow(index: index, item: nil),
                    onDisplay: { _ in }
                )
            }
        case .success(let success):
            successContent(success)
        }
    }

    @ViewBuilder
    private func successContent(_ success: PagingStateSuccess<UiTimelineV2>) -> some View {
        let count = Int(success.itemCount)
        if count == 0 {
            ListEmptyView()
                .padding()
                .frame(maxWidth: .infinity)
        } else {
            let rows = RssTimelineRows(success: success, count: count)
            ForEach(rows) { row in
                let rowView = RssTimelineItemRow(
                    row: row,
                    onDisplay: { index in
                        _ = success.get(index: Int32(index))
                    }
                )

                if row.item != nil {
                    rowView.tag(row.id)
                } else {
                    rowView
                }
            }

            switch onEnum(of: success.appendState) {
            case .error(let error):
                ListErrorView(error: error.error) {
                    success.retry()
                }
                .padding()
                .frame(maxWidth: .infinity)
            case .loading:
                ProgressView()
                    .padding()
                    .frame(maxWidth: .infinity)
            case .notLoading:
                EmptyView()
            }
        }
    }

    @MainActor
    private func syncSelection(id: String?) {
        guard let id else {
            selectedItem = nil
            return
        }

        if selectedItem?.id == id {
            return
        }

        selectedItem = selection(for: id)
    }

    @MainActor
    private func selection(for id: String) -> RssTimelineItemSelection? {
        switch onEnum(of: data) {
        case .success(let success):
            let count = Int(success.itemCount)
            for index in 0..<count {
                guard let item = success.peek(index: Int32(index)),
                      rssTimelineItemID(for: item) == id else {
                    continue
                }
                return RssTimelineItemSelection(item)
            }
            return nil
        case .empty, .error, .loading:
            return nil
        }
    }
}

private struct RssTimelineItemRow: View {
    let row: RssTimelineRow
    let onDisplay: (Int) -> Void

    var body: some View {
        Group {
            if let item = row.item {
                TimelineView(data: item, detailStatusKey: nil)
                    .environment(\.openURL, OpenURLAction { _ in .handled })
                    .allowsHitTesting(false)
                    .padding(.vertical, 8)
                    .frame(maxWidth: .infinity, alignment: .leading)
            } else {
                TimelinePlaceholderView()
                    .padding(.vertical, 8)
                    .frame(maxWidth: .infinity, alignment: .leading)
            }
        }
        .listRowInsets(EdgeInsets(top: 4, leading: 12, bottom: 4, trailing: 12))
        .onAppear {
            onDisplay(row.index)
        }
    }
}

private struct RssTimelineRow: Identifiable {
    let index: Int
    let item: UiTimelineV2?

    var id: String {
        item.map(rssTimelineItemID(for:)) ?? "placeholder-\(index)"
    }
}

private struct RssTimelineRows: @MainActor RandomAccessCollection {
    let success: PagingStateSuccess<UiTimelineV2>
    let count: Int

    var startIndex: Int { 0 }
    var endIndex: Int { count }

    func index(after index: Int) -> Int { index + 1 }
    func index(before index: Int) -> Int { index - 1 }
    func index(_ index: Int, offsetBy distance: Int) -> Int { index + distance }
    func distance(from start: Int, to end: Int) -> Int { end - start }

    subscript(position: Int) -> RssTimelineRow {
        RssTimelineRow(
            index: position,
            item: success.peek(index: Int32(position))
        )
    }
}

private struct RssTimelineItemDetailScaffold: View {
    let selection: RssTimelineItemSelection?

    var body: some View {
        Group {
            if let selection {
                RssDetailScreen(
                    url: selection.url,
                    descriptionHtml: selection.descriptionHtml,
                    descriptionTitle: selection.title
                )
                .id(selection.id)
            } else {
                ContentUnavailableView {
                    Label("macos_rss_item_detail_placeholder_title", systemImage: "doc.text")
                } description: {
                    Text("macos_rss_item_detail_placeholder_description")
                }
                .navigationTitle(String(localized: "rss_detail_title", bundle: .main))
            }
        }
        .navigationSplitViewColumnWidth(min: 420, ideal: 560)
    }
}

private struct RssTimelineItemSelection: Identifiable {
    let id: String
    let url: String
    let title: String?
    let descriptionHtml: String?

    init?(_ item: UiTimelineV2) {
        switch onEnum(of: item) {
        case .feed(let feed):
            id = rssTimelineItemID(for: item)
            url = feed.url
            title = feed.title
            descriptionHtml = feed.descriptionHtml
        default:
            return nil
        }
    }
}

struct RssDetailScreen: View {
    let url: String
    private let fallbackTitle: String?

    @StateObject private var presenter: KotlinPresenter<RssDetailPresenterState>
    @Environment(\.translateConfig) private var translateConfig
    @Environment(\.openURL) private var openURL
    @State private var showTLDR = false
    @State private var showTranslate = false

    init(url: String, descriptionHtml: String? = nil, descriptionTitle: String? = nil) {
        self.url = url
        self.fallbackTitle = descriptionTitle
        _presenter = StateObject(
            wrappedValue: KotlinPresenter(
                presenter: RssDetailPresenter(
                    url: url,
                    descriptionHtml: descriptionHtml,
                    descriptionTitle: descriptionTitle
                )
            )
        )
    }

    private var shouldTranslate: Bool {
        translateConfig.preTranslate || showTranslate
    }

    private var navigationTitle: String {
        switch onEnum(of: presenter.state.data) {
        case .success(let data):
            if let title = data.data.title.nonBlank {
                return title
            }
        case .error, .loading:
            break
        }

        return fallbackTitle.nonBlank ?? String(localized: "rss_detail_title", bundle: .main)
    }

    var body: some View {
        StateView(state: presenter.state.data) { document in
            if shouldTranslate {
                RssTranslateProvider(document: document) { translatedTitle, translatedHtml, isTranslating in
                    RssArticleContentView(
                        document: document,
                        url: url,
                        showTLDR: $showTLDR,
                        showTranslate: $showTranslate,
                        translatedTitle: translatedTitle,
                        translatedHtml: translatedHtml,
                        isTranslating: isTranslating,
                        showTranslateButton: false
                    )
                }
            } else {
                RssArticleContentView(
                    document: document,
                    url: url,
                    showTLDR: $showTLDR,
                    showTranslate: $showTranslate,
                    translatedTitle: nil,
                    translatedHtml: nil,
                    isTranslating: false,
                    showTranslateButton: true
                )
            }
        } errorContent: { error in
            ContentUnavailableView {
                Label("rss_detail_title", systemImage: "exclamationmark.triangle")
            } description: {
                Text(error.message ?? "Unknown error")
            }
        } loadingContent: {
            RssArticleLoadingView()
        }
        .navigationTitle(navigationTitle)
        .toolbar {
            ToolbarItem(placement: .primaryAction) {
                if let url = URL(string: url) {
                    Button {
                        openURL(url)
                    } label: {
                        Label {
                            Text("Open in Browser")
                        } icon: {
                            Image(systemName: "safari")
                        }
                    }
                    .help("Open in Browser")
                }
            }
        }
    }
}

private struct RssTranslateProvider<Content: View>: View {
    @StateObject private var translatePresenter: KotlinPresenter<RssDetailTranslatePresenterState>
    let document: DocumentData
    let content: (_ translatedTitle: String?, _ translatedHtml: String?, _ isTranslating: Bool) -> Content

    init(
        document: DocumentData,
        @ViewBuilder content: @escaping (_ translatedTitle: String?, _ translatedHtml: String?, _ isTranslating: Bool) -> Content
    ) {
        self.document = document
        self.content = content
        _translatePresenter = StateObject(
            wrappedValue: KotlinPresenter(
                presenter: RssDetailTranslatePresenter(
                    htmlContent: document.content,
                    title: document.title,
                    targetLanguage: Locale.current.language.languageCode?.identifier ?? "en"
                )
            )
        )
    }

    private var translatedTitle: String? {
        switch onEnum(of: translatePresenter.state.translatedTitle) {
        case .success(let data):
            return String(data.data)
        case .loading, .error:
            return nil
        }
    }

    private var translatedHtml: String? {
        switch onEnum(of: translatePresenter.state.translatedHtml) {
        case .success(let data):
            return String(data.data)
        case .loading, .error:
            return nil
        }
    }

    private var isTranslating: Bool {
        switch onEnum(of: translatePresenter.state.translatedHtml) {
        case .loading:
            return true
        case .success, .error:
            return false
        }
    }

    var body: some View {
        content(translatedTitle, translatedHtml, isTranslating)
    }
}

private struct RssArticleContentView: View {
    let document: DocumentData
    let url: String
    @Binding var showTLDR: Bool
    @Binding var showTranslate: Bool
    let translatedTitle: String?
    let translatedHtml: String?
    let isTranslating: Bool
    let showTranslateButton: Bool

    @Environment(\.openURL) private var openURL

    var body: some View {
        MacRssHtmlWebView(
            htmlString: translatedHtml ?? document.content,
            baseURL: URL(string: url),
            onOpenURL: { url in openURL(url) }
        )
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .ignoresSafeArea(.container, edges: .top)
        .background(Color(nsColor: .textBackgroundColor))
        .overlay(alignment: .top) {
            if isTranslating {
                ProgressView()
                    .progressViewStyle(.linear)
            }
        }
        .toolbar {
            ToolbarItem(placement: .primaryAction) {
                if let url = URL(string: url) {
                    ShareLink(
                        item: url,
                        subject: Text(document.title),
                        message: Text(document.title),
                        preview: SharePreview(document.title)
                    ) {
                        Image(fontAwesome: .shareNodes)
                    }
                    .help("Share")
                }
            }

            ToolbarItem(placement: .primaryAction) {
                if showTranslateButton {
                    Button {
                        showTranslate = true
                    } label: {
                        Image(fontAwesome: .language)
                    }
                    .help("Translate")
                }
            }

            ToolbarItem(placement: .primaryAction) {
                Button {
                    showTLDR = true
                } label: {
                    Text("Summarize")
                }
                .help("Summarize")
                .popover(isPresented: $showTLDR, arrowEdge: .bottom) {
                    ScrollView {
                        TLDRTextView(text: document.textContent)
                            .padding()
                            .frame(maxWidth: .infinity, alignment: .leading)
                    }
                    .frame(width: 420, height: 360)
                }
            }
        }
    }
}

private struct RssArticleLoadingView: View {
    var body: some View {
        VStack {
            VStack(alignment: .leading, spacing: 16) {
                Text("Loading...")
                    .font(.title2)
                    .bold()
                    .redacted(reason: .placeholder)
                Divider()
                TimelinePlaceholderView()
            }
            .frame(maxWidth: 680)
            .padding(.horizontal, 28)
            .padding(.vertical, 24)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .top)
        .background(Color(nsColor: .textBackgroundColor))
    }
}

private struct MacRssHtmlWebView: NSViewRepresentable {
    @Environment(\.colorScheme) private var colorScheme
    let htmlString: String?
    let baseURL: URL?
    let onOpenURL: ((URL) -> Void)?

    final class Coordinator: NSObject, WKNavigationDelegate {
        var parent: MacRssHtmlWebView
        var renderedHTML: String?
        var renderedBaseURL: URL?

        init(_ parent: MacRssHtmlWebView) {
            self.parent = parent
        }

        @MainActor
        func webView(
            _ webView: WKWebView,
            decidePolicyFor navigationAction: WKNavigationAction,
            decisionHandler: @escaping @MainActor (WKNavigationActionPolicy) -> Void
        ) {
            guard let targetURL = navigationAction.request.url else {
                decisionHandler(.allow)
                return
            }

            if targetURL.scheme == "flare-media-image",
               let components = URLComponents(url: targetURL, resolvingAgainstBaseURL: false),
               let imageUrl = components.queryItems?.first(where: { $0.name == "url" })?.value,
               let url = URL(string: imageUrl) {
                parent.onOpenURL?(url)
                decisionHandler(.cancel)
                return
            }

            if let baseURL = parent.baseURL,
               targetURL.host() == baseURL.host(),
               targetURL.scheme == baseURL.scheme {
                decisionHandler(.allow)
                return
            }

            parent.onOpenURL?(targetURL)
            decisionHandler(.cancel)
        }
    }

    func makeCoordinator() -> Coordinator {
        Coordinator(self)
    }

    func makeNSView(context: Context) -> WKWebView {
        let config = WKWebViewConfiguration()

        let webView = WKWebView(frame: .zero, configuration: config)
        webView.navigationDelegate = context.coordinator

        loadHTMLIfNeeded(in: webView, context: context)
        return webView
    }

    func updateNSView(_ nsView: WKWebView, context: Context) {
        context.coordinator.parent = self
        loadHTMLIfNeeded(in: nsView, context: context)
    }

    static func dismantleNSView(_ nsView: WKWebView, coordinator: Coordinator) {
        nsView.navigationDelegate = nil
    }

    private func loadHTMLIfNeeded(in webView: WKWebView, context: Context) {
        guard let html = htmlString else {
            return
        }

        let renderedHTML = htmlData(html: html, scheme: colorScheme)
        guard context.coordinator.renderedHTML != renderedHTML ||
                context.coordinator.renderedBaseURL != baseURL else {
            return
        }

        context.coordinator.renderedHTML = renderedHTML
        context.coordinator.renderedBaseURL = baseURL
        webView.loadHTMLString(renderedHTML, baseURL: baseURL)
    }

    private func htmlData(html: String, scheme: ColorScheme) -> String {
        let foreground = scheme == .dark ? "#f0f0f0" : "#24292f"
        let secondary = scheme == .dark ? "#8b949e" : "#57606a"
        let link = scheme == .dark ? "#58a6ff" : "#0969da"
        let background = scheme == .dark ? "#1e1e1e" : "#ffffff"

        return """
        <!doctype html>
        <html>
        <head>
          <meta charset="utf-8">
          <meta name="viewport" content="width=device-width, initial-scale=1.0">
          <meta name="color-scheme" content="light dark">
          <link rel="stylesheet" href="https://cdnjs.cloudflare.com/ajax/libs/github-markdown-css/5.8.1/github-markdown.min.css">
          <style>
            html, body {
              margin: 0;
              padding: 0;
              min-height: 100%;
              background: \(background);
            }
            .markdown-body {
              background-color: \(background);
              color: \(foreground);
              box-sizing: border-box;
              font-size: 15px;
              line-height: 1.58;
              max-width: 736px;
              min-height: 100vh;
              margin: 0 auto;
              padding: calc(env(safe-area-inset-top, 0px) + 24px) 28px calc(env(safe-area-inset-bottom, 0px) + 48px);
            }
            .markdown-body a {
              color: \(link);
            }
            .markdown-body blockquote {
              color: \(secondary);
            }
            .markdown-body img {
              display: block;
              width: 100%;
              height: auto;
            }
            .markdown-body video {
              display: block;
              width: 100%;
              height: auto;
            }
            .markdown-body img[data-flare-clickable="true"] {
              cursor: pointer;
            }
            @media (max-width: 736px) {
              .markdown-body {
                padding: calc(env(safe-area-inset-top, 0px) + 20px) 20px calc(env(safe-area-inset-bottom, 0px) + 40px);
              }
            }
          </style>
          <script>
            function bindFlareImageClicks() {
              const images = document.querySelectorAll('.markdown-body img');
              images.forEach((img) => {
                img.loading = 'lazy';
                if (img.dataset.flareClickable === 'true') {
                  return;
                }
                img.dataset.flareClickable = 'true';
                img.addEventListener('click', function(event) {
                  event.preventDefault();
                  event.stopPropagation();
                  const src = img.currentSrc || img.src;
                  if (!src) {
                    return;
                  }
                  window.location.href = 'flare-media-image://open?url=' + encodeURIComponent(src);
                });
              });
            }

            document.addEventListener('DOMContentLoaded', function() {
              bindFlareImageClicks();
            });
          </script>
        </head>
        <body>
        <article class="markdown-body">
          \(html)
        </article>
        <script>
          bindFlareImageClicks();
        </script>
        </body>
        </html>
        """
    }
}

private func rssTimelineItemID(for item: UiTimelineV2) -> String {
    item.itemKey ?? String(describing: item)
}

private extension Optional where Wrapped == String {
    var nonBlank: String? {
        guard let value = self,
              !value.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else {
            return nil
        }
        return value
    }
}

private extension String {
    var nonBlank: String? {
        guard !trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else {
            return nil
        }
        return self
    }
}

private struct RssSourceSidebarRow: View {
    let item: UiRssSource
    let onEdit: () -> Void
    let onDelete: () -> Void

    var body: some View {
        HStack(spacing: 10) {
            UiRssView(data: item)
                .frame(maxWidth: .infinity, alignment: .leading)
                .contentShape(Rectangle())

            Button(action: onEdit) {
                Image(fontAwesome: .pen)
            }
            .buttonStyle(.borderless)
            .help(String(localized: "macos_action_edit", bundle: .main))

            Button(role: .destructive, action: onDelete) {
                Image(fontAwesome: .trash)
            }
            .buttonStyle(.borderless)
            .help(String(localized: "delete_button", bundle: .main))
        }
        .padding(.vertical, 6)
    }
}

private struct RssEmptyState: View {
    let onAdd: () -> Void

    var body: some View {
        ContentUnavailableView {
            Label("empty_rss_sources", systemImage: "dot.radiowaves.left.and.right")
        } description: {
            Text("macos_placeholder_rss")
        } actions: {
            Button(action: onAdd) {
                Label {
                    Text("add_rss_title")
                } icon: {
                    Image(fontAwesome: .plus)
                }
            }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
    }
}

private struct RssDetailPlaceholder: View {
    var body: some View {
        ContentUnavailableView {
            Label("macos_rss_detail_placeholder_title", systemImage: "sidebar.left")
        } description: {
            Text("macos_rss_detail_placeholder_description")
        }
    }
}

private struct ImportOPMLScreen: View {
    @Environment(\.dismiss) private var dismiss

    let url: URL
    @StateObject private var presenter: KotlinPresenter<ImportOPMLPresenterState>

    init(url: URL) {
        self.url = url
        _presenter = StateObject(wrappedValue: KotlinPresenter(presenter: ImportOPMLPresenter(opmlContent: Self.readContent(from: url))))
    }

    var body: some View {
        VStack(spacing: 12) {
            if let error = presenter.state.error {
                ContentUnavailableView {
                    Label("import_error", systemImage: "exclamationmark.triangle")
                } description: {
                    Text(error)
                }
            } else {
                if presenter.state.importing {
                    VStack(alignment: .leading, spacing: 8) {
                        ProgressView(value: presenter.state.progress)
                        Text("\(presenter.state.importedCount) / \(presenter.state.totalCount)")
                            .font(.caption)
                            .foregroundStyle(.secondary)
                    }
                    .padding(.horizontal)
                }

                List {
                    ForEach(presenter.state.importedSources, id: \.url) { item in
                        UiRssView(data: item)
                            .padding(.vertical, 6)
                    }
                }
                .listStyle(.inset)
            }
        }
        .navigationTitle("opml_import_title")
        .toolbar {
            ToolbarItem(placement: .confirmationAction) {
                Button {
                    dismiss()
                } label: {
                    Label {
                        Text("ok_button")
                    } icon: {
                        Image(fontAwesome: .check)
                    }
                }
                .disabled(presenter.state.importing)
            }
        }
    }

    private static func readContent(from url: URL) -> String {
        let isSecurityScoped = url.startAccessingSecurityScopedResource()
        defer {
            if isSecurityScoped {
                url.stopAccessingSecurityScopedResource()
            }
        }

        return (try? String(contentsOf: url, encoding: .utf8)) ?? ""
    }
}

private struct EditRssSelection: Identifiable {
    let id: Int
    let sourceId: Int
    let url: String
    let displayMode: RssDisplayMode

    init(_ item: UiRssSource) {
        id = Int(item.id)
        sourceId = Int(item.id)
        url = item.url
        displayMode = item.displayMode
    }
}

private struct OPMLImportSelection: Identifiable {
    let id = UUID()
    let url: URL
}
