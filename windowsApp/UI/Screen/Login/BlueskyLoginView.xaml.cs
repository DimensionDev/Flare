using CommunityToolkit.Mvvm.ComponentModel;
using CommunityToolkit.Mvvm.Input;
using dev.dimension.flare.ui.presenter.login;
using Microsoft.UI.Xaml;
using Microsoft.UI.Xaml.Controls;
using Microsoft.UI.Xaml.Controls.Primitives;
using Microsoft.UI.Xaml.Data;
using Microsoft.UI.Xaml.Input;
using Microsoft.UI.Xaml.Media;
using Microsoft.UI.Xaml.Navigation;
using PropertyChanged;
using System;
using System.Collections.Generic;
using System.IO;
using System.Linq;
using System.Runtime.InteropServices.WindowsRuntime;
using Windows.Foundation;
using Windows.Foundation.Collections;
using kotlin.jvm.functions;

// To learn more about WinUI, the WinUI project structure,
// and more about our project templates, see: http://aka.ms/winui-project-info.

namespace Flare.UI.Screen.Login;

/// <summary>
/// An empty page that can be used on its own or navigated to within a Frame.
/// </summary>
sealed partial class BlueskyLoginView : Page
{
    public BlueskyLoginViewModel ViewModel { get; }
    public BlueskyLoginView()
    {
        ViewModel = new BlueskyLoginViewModel(ToHome);
        this.InitializeComponent();
    }

    void ToHome()
    {
        
    }

    private void Page_Loaded(object sender, RoutedEventArgs e)
    {
        ViewModel.Subscribe();
    }

    private void Page_Unloaded(object sender, RoutedEventArgs e)
    {
        ViewModel.Unsubscribe();
    }
}

class KotlinFunction : Function0
{
    private readonly Action _action;

    public KotlinFunction(Action action)
    {
        _action = action;
    }

    public object invoke()
    {
        _action();
        return kotlin.Unit.INSTANCE;
    }
}

partial class BlueskyLoginViewModel : MoleculeViewModel<BlueskyLoginPresenter, BlueskyLoginState>
{
    [ObservableProperty]
    private string _username = "";
    [ObservableProperty]
    private string _password = "";
    [ObservableProperty]
    private string _baseUrl = "https://bsky.social";

    [DependsOn(nameof(State))]
    public bool IsNotLoading => !State.getLoading();

    [DependsOn(nameof(State))]
    public string? Error => State.getError()?.Message;


    public BlueskyLoginViewModel(Action toHome) : base(new BlueskyLoginPresenter(new KotlinFunction(toHome)))
    {
    }

    [RelayCommand(CanExecute = nameof(IsNotLoading))]
    public void Login()
    {
        State.login(BaseUrl, Username, Password);
    }
}