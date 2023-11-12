using dev.dimension.flare.di;
using Microsoft.UI.Xaml;
using Microsoft.Windows.AppLifecycle;
using System;
using Windows.ApplicationModel.Activation;

// To learn more about WinUI, the WinUI project structure,
// and more about our project templates, see: http://aka.ms/winui-project-info.

namespace Flare
{
    /// <summary>
    /// Provides application-specific behavior to supplement the default Application class.
    /// </summary>
    public partial class App : Application
    {
        /// <summary>
        /// Initializes the singleton application object.  This is the first line of authored code
        /// executed, and as such is the logical equivalent of main() or WinMain().
        /// </summary>
        public App()
        {
            this.InitializeComponent();
            KoinHelper.INSTANCE.start();
        }

        /// <summary>
        /// Invoked when the application is launched.
        /// </summary>
        /// <param name="args">Details about the launch request and process.</param>
        protected override async void OnLaunched(Microsoft.UI.Xaml.LaunchActivatedEventArgs args)
        {

            // Get the activation args
            var appArgs = Microsoft.Windows.AppLifecycle.AppInstance.GetCurrent().GetActivatedEventArgs();

            // Get or register the main instance
            var mainInstance = Microsoft.Windows.AppLifecycle.AppInstance.FindOrRegisterForKey("main");

            // If the main instance isn't this current instance
            if (!mainInstance.IsCurrent)
            {
                // Redirect activation to that instance
                await mainInstance.RedirectActivationToAsync(appArgs);

                // And exit our instance and stop
                System.Diagnostics.Process.GetCurrentProcess().Kill();
                return;
            }

            // Otherwise, register for activation redirection
            Microsoft.Windows.AppLifecycle.AppInstance.GetCurrent().Activated += App_Activated;

            m_window = new MainWindow();
            m_window.Activate();


            var activatedEventArgs = Microsoft.Windows.AppLifecycle.AppInstance.GetCurrent().GetActivatedEventArgs();
            switch (activatedEventArgs.Kind)
            {
                case ExtendedActivationKind.Protocol:
                    var protocolArgs = activatedEventArgs.Data as Windows.ApplicationModel.Activation.LaunchActivatedEventArgs;
                    break;
            }
        }

        private void App_Activated(object sender, Microsoft.Windows.AppLifecycle.AppActivationArguments e)
        {
            // Bring the window to the foreground... first get the window handle...
            var hwnd = (Windows.Win32.Foundation.HWND)WinRT.Interop.WindowNative.GetWindowHandle(m_window);

            // Restore window if minimized... requires Microsoft.Windows.CsWin32 NuGet package and a NativeMethods.txt file with ShowWindow method
            Windows.Win32.PInvoke.ShowWindow(hwnd, Windows.Win32.UI.WindowsAndMessaging.SHOW_WINDOW_CMD.SW_RESTORE);

            // And call SetForegroundWindow... requires Microsoft.Windows.CsWin32 NuGet package and a NativeMethods.txt file with SetForegroundWindow method
            Windows.Win32.PInvoke.SetForegroundWindow(hwnd);
        }

        private Window m_window;
    }
}
