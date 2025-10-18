using System;
using System.Threading;
using System.Threading.Tasks;
using Windows.ApplicationModel.Activation;
using Flare;
using Microsoft.UI.Dispatching;
using Microsoft.UI.Xaml;
using Microsoft.Windows.AppLifecycle;

public class Program
{
    private static DispatcherQueue dispatcherQueue;

    [STAThread]
    static void Main(string[] args)
    {
        WinRT.ComWrappersSupport.InitializeComWrappers();
        AppInstance.GetCurrent().Activated += OnActivated;

        var isRedirect = DecideRedirection().GetAwaiter().GetResult();

        if (!isRedirect)
        {
            Application.Start((p) =>
            {
                dispatcherQueue = DispatcherQueue.GetForCurrentThread();
                var context = new DispatcherQueueSynchronizationContext(dispatcherQueue);
                SynchronizationContext.SetSynchronizationContext(context);
                new App();
            });
        }
    }

    private static async Task<bool> DecideRedirection()
    {
        var mainInstance = AppInstance.FindOrRegisterForKey("dev.dimension.flare");
        var activatedEventArgs = AppInstance.GetCurrent().GetActivatedEventArgs();

        if (!mainInstance.IsCurrent)
        {
            await mainInstance.RedirectActivationToAsync(activatedEventArgs);
            return true;
        }

        return false;
    }

    private static void OnActivated(object? sender, AppActivationArguments args)
    {
        if (args.Kind == ExtendedActivationKind.Protocol)
        {
            var protocolArgs = (ProtocolActivatedEventArgs)args.Data;
            dispatcherQueue.TryEnqueue(() =>
            {
                if (protocolArgs.Uri.Scheme == "flare")
                {
                    App.Instance.OnDeeplink(protocolArgs.Uri.ToString());
                }
            });
        }
    }
}