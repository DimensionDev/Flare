using CommunityToolkit.Mvvm.ComponentModel;
using dev.dimension.flare.ui.presenter;
using jdk.nashorn.@internal.objects.annotations;
using kotlinx.serialization.encoding;
using System;
using System.Collections.Generic;
using System.Linq;
using System.Text;
using System.Threading.Tasks;

namespace Flare.UI;

internal abstract partial class MoleculeViewModel<TPresenter, TState> : ObservableObject, ModelListener where TPresenter : PresenterBase  where TState : class
{
    [ObservableProperty] private TState _state;
    private readonly TPresenter _presenter;

    protected MoleculeViewModel(TPresenter presenter)
    {
        _presenter = presenter;
        if (_presenter.getModels().getValue() is TState initialValue)
        {
            _state = initialValue;
        }
    }
    
    public void Subscribe()
    {
        _presenter.subscribe(this);
    }

    public void Unsubscribe()
    {
        _presenter.unsubscribe();
    }

    public void onModelChanged(object obj)
    {
        if (obj is TState state)
        {
            State = state;
        }
    }
}