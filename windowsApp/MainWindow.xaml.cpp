// Copyright (c) Microsoft Corporation and Contributors.
// Licensed under the MIT License.

#include "pch.h"
#include "MainWindow.xaml.h"
#if __has_include("MainWindow.g.cpp")
#include "MainWindow.g.cpp"
#endif

using namespace winrt;
using namespace Microsoft::UI::Xaml;

// To learn more about WinUI, the WinUI project structure,
// and more about our project templates, see: http://aka.ms/winui-project-info.

namespace winrt::Flare::implementation
{
    MainWindow::MainWindow()
    {
        auto lib = libshared_symbols();

        presenter = lib->kotlin.root.dev.dimension.flare.ui.presenter.login.BlueskyLoginPresenterMinGWPresenter.BlueskyLoginPresenterMinGWPresenter(toHomeCallback);
        lib->kotlin.root.dev.dimension.flare.ui.presenter.login.BlueskyLoginPresenterMinGWPresenter.connect(presenter, stateCallback);
        InitializeComponent();
    }

    void MainWindow::myButton_Click(IInspectable const&, RoutedEventArgs const&)
    {
        myButton().Content(box_value(L"Clicked"));
    }

    void MainWindow::toHomeCallback() {
    }

    void MainWindow::stateCallback(void* data) {
        auto state = libshared_symbols()->kotlin.root.dev.dimension.flare.ui.presenter.login.BlueskyLoginPresenterMinGWPresenter.unwrap(presenter, data);
        auto stateData = libshared_symbols()->kotlin.root.dev.dimension.flare.ui.presenter.login.BlueskyLoginState.get_loading(state);
    }

}
