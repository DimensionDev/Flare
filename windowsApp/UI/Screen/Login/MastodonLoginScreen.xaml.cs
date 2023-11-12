using CommunityToolkit.Mvvm.ComponentModel;
using CommunityToolkit.Mvvm.Input;
using dev.dimension.flare.common;
using dev.dimension.flare.di;
using dev.dimension.flare.ui.presenter.login;
using kotlin;
using kotlin.coroutines;
using Microsoft.UI.Xaml;
using Microsoft.UI.Xaml.Controls;
using Microsoft.UI.Xaml.Controls.Primitives;
using Microsoft.UI.Xaml.Data;
using Microsoft.UI.Xaml.Input;
using Microsoft.UI.Xaml.Media;
using Microsoft.UI.Xaml.Navigation;
using System;
using System.Collections.Generic;
using System.IO;
using System.Linq;
using System.Runtime.InteropServices.WindowsRuntime;
using System.Threading.Tasks;
using Windows.Foundation;
using Windows.Foundation.Collections;

// To learn more about WinUI, the WinUI project structure,
// and more about our project templates, see: http://aka.ms/winui-project-info.

namespace Flare.UI.Screen.Login
{
    /// <summary>
    /// An empty page that can be used on its own or navigated to within a Frame.
    /// </summary>
    sealed partial class MastodonLoginScreen : Page
    {
        public MastodonViewModel ViewModel { get; } = new MastodonViewModel();
        public MastodonLoginScreen()
        {
            this.InitializeComponent();
        }
    }

    partial class MastodonViewModel : ObservableObject
    {
        [ObservableProperty] private string _instance = "mstdn.jp";

        [RelayCommand]
        public async Task Login()
        {
            await KotlinAsync.ToTask<kotlin.Unit>((continuation) =>
            {
                MastodonCallbackPresenterKt.mastodonLoginUseCase(
                    Instance,
                    KoinHelper.INSTANCE.getApplicationRepository(),
                    new KotlinFunction1<string>((url) =>
                    {
                        _ = Windows.System.Launcher.LaunchUriAsync(new Uri(url));
                    }),
                    continuation);
            });
        }
    }

    static class KotlinAsync
    {
        public static Task<T?> ToTask<T>(Action<CustomContinuation<T?>> action)
        {
            var taskCompletionSource = new TaskCompletionSource<T?>();
            var continuation = new CustomContinuation<T?>(taskCompletionSource);
            action(continuation);
            return taskCompletionSource.Task;
        }
    }

    class CustomContinuation<T> : Continuation
    {
        private readonly TaskCompletionSource<T?> _taskCompletionSource;

        public CustomContinuation(TaskCompletionSource<T?> taskCompletionSource)
        {
            _taskCompletionSource = taskCompletionSource;
        }

        public CoroutineContext getContext()
        {
            return EmptyCoroutineContext.INSTANCE;
        }

        public void resumeWith(object obj)
        {
            if (obj is Result result)
            {
                if (ResultHelper.INSTANCE.isSuccess(result))
                {
                    if (ResultHelper.INSTANCE.unwrapResult(result) is T value)
                    {
                        _taskCompletionSource.SetResult(value);
                    }
                    else
                    {
                        _taskCompletionSource.SetResult(default);
                    }
                }
                else
                {
                    _taskCompletionSource.SetException(ResultHelper.INSTANCE.unwrapException(result));
                }
            }
            else
            {

                _taskCompletionSource.SetResult(default);
            }
        }
    }
}
