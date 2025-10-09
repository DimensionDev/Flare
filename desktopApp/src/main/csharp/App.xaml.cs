using Microsoft.UI.Xaml;
using System;
using System.Collections.Generic;
using System.Diagnostics;
using System.IO;
using System.Linq;
using System.Net;
using System.Net.Sockets;
using System.Text;
using System.Text.Json.Serialization;
using System.Threading;
using System.Threading.Tasks;
using Windows.Media.Core;
using Microsoft.UI.Xaml.Controls;
using Microsoft.UI.Xaml.Media;
using Microsoft.Web.WebView2.Core;

namespace Flare
{
    public partial class App : Application
    {
        private Dictionary<string, Window> _openWindows = new();
        private int _kotlinPort;
        private int _csharpPort;
        private Process? _composeProcess;

        public static App Instance { get; private set; }

        public App()
        {
            InitializeComponent();
            DispatcherShutdownMode = DispatcherShutdownMode.OnExplicitShutdown;
            _kotlinPort = GetFreeTcpPort();
            _csharpPort = GetFreeTcpPort();
            var cts = new CancellationTokenSource();
            _ = RunCSharpIpcServerAsync(IPAddress.Loopback, _csharpPort, cts.Token);
            StartComposeWithArgs(_kotlinPort, _csharpPort);
            Instance = this;
        }

        private int GetFreeTcpPort()
        {
            var l = new TcpListener(IPAddress.Loopback, 0);
            l.Start();
            int port = ((IPEndPoint)l.LocalEndpoint).Port;
            l.Stop();
            return port;
        }

        private void StartComposeWithArgs(int kotlinRecvPort, int csharpRecvPort)
        {
            var installPath = Windows.ApplicationModel.Package.Current.InstalledLocation.Path;
            var exePath = Path.Combine(installPath, "Assets", "compose", "Flare", "Flare.exe");
            var psi = new ProcessStartInfo
            {
                FileName = exePath,
                Arguments = $"--kotlin-recv {kotlinRecvPort} --csharp-recv {csharpRecvPort}",
                UseShellExecute = false,
                WorkingDirectory = Path.GetDirectoryName(exePath)!,
            };
            _composeProcess = Process.Start(psi);
        }

        private async Task RunCSharpIpcServerAsync(IPAddress ip, int port, CancellationToken ct)
        {
            var listener = new TcpListener(ip, port);
            listener.Start();
            try
            {
                while (!ct.IsCancellationRequested)
                {
                    var client = await listener.AcceptTcpClientAsync(ct);
                    _ = HandleClientAsync(client, ct);
                }
            }
            catch (OperationCanceledException)
            {
            }
            finally
            {
                listener.Stop();
            }
        }

        private async Task HandleClientAsync(TcpClient client, CancellationToken ct)
        {
            using (client)
            await using (var stream = client.GetStream())
            using (var reader = new StreamReader(stream, Encoding.UTF8, false, 8192, leaveOpen: true))
            {
                while (!ct.IsCancellationRequested && await reader.ReadLineAsync(ct) is { } line)
                {
                    var jsonObject = System.Text.Json.JsonDocument.Parse(line);
                    if (jsonObject.RootElement.TryGetProperty("Type", out var typeElement))
                    {
                        var type = typeElement.GetString();
                        switch (type)
                        {
                            case "shutdown":
                            {
                                Exit();
                                break;
                            }
                            case "open-image-viewer":
                            {
                                if (jsonObject.RootElement.TryGetProperty("Data", out var dataElement))
                                {
                                    var data = dataElement.GetString();
                                    if (data != null)
                                    {
                                        
                                        var image = new Image
                                        {
                                            Source = new Microsoft.UI.Xaml.Media.Imaging.BitmapImage(
                                                new Uri(data)),
                                            Stretch = Stretch.Uniform,
                                        };
                                        var scrollViewer = new ScrollViewer
                                        {
                                            Content = image,
                                            HorizontalScrollBarVisibility = ScrollBarVisibility.Auto,
                                            VerticalScrollBarVisibility = ScrollBarVisibility.Auto,
                                            ZoomMode = ZoomMode.Enabled,
                                            HorizontalScrollMode = ScrollMode.Auto,
                                            VerticalScrollMode = ScrollMode.Auto,
                                        };
                                        scrollViewer.Loaded += (sender, __) =>
                                        {
                                            if (sender is ScrollViewer sv)
                                            {
                                                var img = sv.Content as Image;
                                                if (img != null)
                                                {
                                                    img.Height = sv.ViewportHeight;
                                                }
                                            }
                                        };
                                        new Window
                                        {
                                            Content = scrollViewer,
                                            ExtendsContentIntoTitleBar = true,
                                            SystemBackdrop = new MicaBackdrop(),
                                            Title = "Image Viewer",
                                            AppWindow =
                                            {
                                                IsShownInSwitchers = false,
                                            },
                                        }.Activate();
                                    }
                                }

                                break;
                            }
                            case "open-status-image-viewer":
                            {
                                var data = System.Text.Json.JsonSerializer.Deserialize<OpenStatusImageData>(
                                    jsonObject.RootElement.GetProperty("Data").GetRawText());
                                if (data != null)
                                {
                                    var flipView = new FlipView();
                                    foreach (var media in data.Medias)
                                    {
                                        switch (media.Type)
                                        {
                                            case "image":
                                                var image = new Image
                                                {
                                                    Source = new Microsoft.UI.Xaml.Media.Imaging.BitmapImage(
                                                        new Uri(media.Url)),
                                                    Stretch = Stretch.Uniform,
                                                };
                                                var scrollViewer = new ScrollViewer
                                                {
                                                    Content = image,
                                                    HorizontalScrollBarVisibility = ScrollBarVisibility.Auto,
                                                    VerticalScrollBarVisibility = ScrollBarVisibility.Auto,
                                                    ZoomMode = ZoomMode.Enabled,
                                                    HorizontalScrollMode = ScrollMode.Auto,
                                                    VerticalScrollMode = ScrollMode.Auto,
                                                };

                                                scrollViewer.Loaded += (sender, __) =>
                                                {
                                                    if (sender is ScrollViewer sv)
                                                    {
                                                        var img = sv.Content as Image;
                                                        if (img != null)
                                                        {
                                                            img.Height = sv.ViewportHeight;
                                                        }
                                                    }
                                                };
                                                flipView.Items.Add(scrollViewer);
                                                break;
                                            case "video":
                                            case "gif":
                                            case "audio":
                                                var mediaPlayerElement = new MediaPlayerElement
                                                {
                                                    Source = MediaSource.CreateFromUri(new Uri(media.Url)),
                                                    AreTransportControlsEnabled = true,
                                                    Stretch = Stretch.Uniform,
                                                };
                                                flipView.Items.Add(mediaPlayerElement);
                                                break;
                                        }
                                    }

                                    flipView.Loaded += (sender, args) =>
                                    {
                                        if (sender is FlipView fv)
                                        {
                                            if (fv.Items.Count > data.Index && data.Index >= 0)
                                            {
                                                fv.SelectedIndex = data.Index;
                                            }

                                            if (fv.SelectedItem is MediaPlayerElement mpe)
                                            {
                                                mpe.MediaPlayer?.Play();
                                            }
                                        }
                                    };
                                    flipView.SelectionChanged += (sender, args) =>
                                    {
                                        if (sender is FlipView fv)
                                        {
                                            foreach (var item in fv.Items)
                                            {
                                                if (item is MediaPlayerElement oldmpe)
                                                {
                                                    oldmpe.MediaPlayer?.Pause();
                                                }
                                            }

                                            if (fv.SelectedItem is MediaPlayerElement mpe)
                                            {
                                                mpe.MediaPlayer?.Play();
                                            }
                                        }
                                    };
                                    var window = new Window
                                    {
                                        Content = flipView,
                                        ExtendsContentIntoTitleBar = true,
                                        SystemBackdrop = new MicaBackdrop(),
                                        Title = "Media Viewer",
                                        AppWindow =
                                        {
                                            IsShownInSwitchers = false,
                                        },
                                    };
                                    window.Closed += (sender, args) =>
                                    {
                                        foreach (var item in flipView.Items)
                                        {
                                            if (item is MediaPlayerElement mpe)
                                            {
                                                var player = mpe.MediaPlayer;
                                                mpe.SetMediaPlayer(null);
                                                player?.Dispose();
                                            }
                                        }
                                    };
                                    window.Activate();
                                }

                                break;
                            }
                            case "open-and-wait-cookies":
                            {
                                var data = System.Text.Json.JsonSerializer.Deserialize<OpenWebViewData>(jsonObject
                                    .RootElement.GetProperty("Data").GetRawText());
                                if (data != null)
                                {
                                    var webView = new WebView2
                                    {
                                        Source = new Uri(data.Url),
                                    };
                                    webView.CoreWebView2Initialized += (sender, args) =>
                                    {
                                        sender.CoreWebView2.CookieManager.DeleteAllCookies();
                                    };

                                    async void NavigationCompletedHandler(WebView2 sender,
                                        CoreWebView2NavigationCompletedEventArgs args)
                                    {
                                        if (args.IsSuccess)
                                        {
                                            var cookies =
                                                await sender.CoreWebView2.CookieManager.GetCookiesAsync(data.Url);
                                            var cookieString = string.Join("; ",
                                                cookies.Select(c => $"{c.Name}={c.Value}"));
                                            var message = new IPCEvent<OnCookieReceivedData>(data.Id,
                                                new OnCookieReceivedData(data.Id, cookieString));
                                            var json = System.Text.Json.JsonSerializer.Serialize(message);
                                            await SendMessage(json);
                                        }
                                    }

                                    webView.NavigationCompleted += NavigationCompletedHandler;
                                    var window = new Window
                                    {
                                        Content = webView,
                                        ExtendsContentIntoTitleBar = true,
                                        SystemBackdrop = new MicaBackdrop(),
                                        Title = "Login",
                                        AppWindow =
                                        {
                                            IsShownInSwitchers = false,
                                        },
                                    };
                                    window.Activate();
                                    _openWindows[data.Id] = window;
                                }

                                break;
                            }
                            case "close-webview":
                            {
                                if (jsonObject.RootElement.TryGetProperty("Data", out var dataElement))
                                {
                                    var data = dataElement.GetString();
                                    if (data != null)
                                    {
                                        if (_openWindows.TryGetValue(data, out var window))
                                        {
                                            window.Close();
                                            _openWindows.Remove(data);
                                        }
                                    }
                                }

                                break;
                            }
                        }
                    }
                }
            }
        }

        private async Task SendMessage(string message)
        {
            var sender = new TcpClient();
            await sender.ConnectAsync(IPAddress.Loopback, _kotlinPort);
            await using var stream = sender.GetStream();
            await using var writer = new StreamWriter(stream, new UTF8Encoding(false));
            writer.AutoFlush = true;
            await writer.WriteAsync(message);
        }

        public void OnDeeplink(string deeplink)
        {
            var message = new IPCEvent<DeeplinkData>("deeplink", new DeeplinkData(deeplink));
            var json = System.Text.Json.JsonSerializer.Serialize(message);
            _ = SendMessage(json);
        }
    }

    internal class IPCEvent<T>(string Type, T? Data)
    {
        public string Type { get; init; } = Type;
        public T? Data { get; init; } = Data;
    }

    internal class DeeplinkData(string Deeplink)
    {
        public string Deeplink { get; init; } = Deeplink;
    }

    internal class OpenStatusImageData(int Index, List<StatusMediaItem> Medias)
    {
        [JsonPropertyName("index")] public int Index { get; init; } = Index;
        [JsonPropertyName("medias")] public List<StatusMediaItem> Medias { get; init; } = Medias;
    }

    internal class StatusMediaItem(string Url, string Type, string? Placeholder)
    {
        [JsonPropertyName("url")] public string Url { get; init; } = Url;

        [JsonPropertyName("type")] public string Type { get; init; } = Type;

        [JsonPropertyName("placeholder")] public string? Placeholder { get; init; } = Placeholder;
    }

    internal class OpenWebViewData(string Url, string Id)
    {
        [JsonPropertyName("url")] public string Url { get; init; } = Url;
        [JsonPropertyName("id")] public string Id { get; init; } = Id;
    }

    internal class OnCookieReceivedData(string Id, string Cookie)
    {
        [JsonPropertyName("id")] public string Id { get; init; } = Id;
        [JsonPropertyName("cookie")] public string Cookie { get; init; } = Cookie;
    }
}