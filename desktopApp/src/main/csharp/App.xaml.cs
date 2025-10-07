using Microsoft.UI.Xaml;
using System;
using System.Collections.Generic;
using System.Diagnostics;
using System.IO;
using System.Net;
using System.Net.Sockets;
using System.Text;
using System.Text.Json.Serialization;
using System.Threading;
using System.Threading.Tasks;
using Windows.Media.Core;
using Microsoft.UI.Xaml.Controls;
using Microsoft.UI.Xaml.Media;

namespace Flare
{
    public partial class App : Application
    {
        //private Window? _window;
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

        protected override void OnLaunched(LaunchActivatedEventArgs args)
        {
            //_window = new MainWindow();
            //_window.Activate();
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
            Debug.WriteLine($"[C# IPC] Listening on {ip}:{port}");

            try
            {
                while (!ct.IsCancellationRequested)
                {
                    var client = await listener.AcceptTcpClientAsync(ct);
                    _ = HandleClientAsync(client, ct);
                }
            }
            catch (OperationCanceledException) { }
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
                    Debug.WriteLine($"[C# IPC] <- {line}");
                    var jsonObject = System.Text.Json.JsonDocument.Parse(line);
                    if (jsonObject.RootElement.TryGetProperty("Type", out var typeElement))
                    {
                        var type = typeElement.GetString();
                        switch (type)
                        {
                            case "shutdown":
                            {
                                Debug.WriteLine("[C# IPC] Shutdown command received.");
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
                                            Debug.WriteLine($"[C# IPC] Open image viewer command received: {data}");
                                            new Window
                                            {
                                                Content = new ScrollViewer
                                                {
                                                    Content = new Image
                                                    {
                                                        Source = new Microsoft.UI.Xaml.Media.Imaging.BitmapImage(new Uri(data)),
                                                        Stretch = Stretch.Uniform,
                                                    },
                                                    HorizontalScrollBarVisibility = ScrollBarVisibility.Auto,
                                                    VerticalScrollBarVisibility = ScrollBarVisibility.Auto,
                                                    ZoomMode = ZoomMode.Enabled,
                                                    HorizontalScrollMode = ScrollMode.Auto,
                                                    VerticalScrollMode = ScrollMode.Auto,
                                                },
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
                                var data = System.Text.Json.JsonSerializer.Deserialize<OpenStatusImageData>(jsonObject.RootElement.GetProperty("Data").GetRawText());
                                if (data != null)
                                {
                                    Debug.WriteLine($"[C# IPC] Open status image viewer command received: {data.Index}");
                                    var flipView = new FlipView();
                                    foreach (var media in data.Medias)
                                    {
                                        switch (media.Type)
                                        {
                                            case "image":
                                                var image = new Image
                                                {
                                                    Source = new Microsoft.UI.Xaml.Media.Imaging.BitmapImage(new Uri(media.Url)),
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
                                    new Window
                                    {
                                        Content = flipView,
                                        ExtendsContentIntoTitleBar = true,
                                        SystemBackdrop = new MicaBackdrop(),
                                        Title = "Media Viewer",
                                        AppWindow =
                                        {
                                            IsShownInSwitchers = false,
                                        },
                                    }.Activate();
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
            Debug.WriteLine($"[C# IPC] -> {deeplink}");
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
        [JsonPropertyName("index")]
        public int Index { get; init; } = Index;
        [JsonPropertyName("medias")]
        public List<StatusMediaItem> Medias { get; init; } = Medias;
    }

    internal class StatusMediaItem(string Url, string Type, string? Placeholder)
    {
        [JsonPropertyName("url")]
        public string Url { get; init; } = Url;
        
        [JsonPropertyName("type")]
        public string Type { get; init; } = Type;
        
        [JsonPropertyName("placeholder")]
        public string? Placeholder { get; init; } = Placeholder;
    }
}
