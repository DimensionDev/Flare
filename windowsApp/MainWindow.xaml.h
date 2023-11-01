// Copyright (c) Microsoft Corporation and Contributors.
// Licensed under the MIT License.

#pragma once

#include "MainWindow.g.h"

namespace winrt::Flare::implementation
{
    struct MainWindow : MainWindowT<MainWindow>
    {
        MainWindow();
        libshared_kref_dev_dimension_flare_ui_presenter_login_BlueskyLoginPresenterMinGWPresenter presenter;
        void toHomeCallback();
        void stateCallback(void* data);
        void myButton_Click(Windows::Foundation::IInspectable const& sender, Microsoft::UI::Xaml::RoutedEventArgs const& args);
    };
}

namespace winrt::Flare::factory_implementation
{
    struct MainWindow : MainWindowT<MainWindow, implementation::MainWindow>
    {
    };
}
