using Flare.Cache;
using Microsoft.UI.Xaml;
using Microsoft.UI.Xaml.Controls;
using Microsoft.UI.Xaml.Media;
using Microsoft.UI.Xaml.Media.Imaging;
using System;
using System.Threading;
using System.Threading.Tasks;
using Windows.Foundation;

namespace Flare
{
    internal class ImageEx2 : ImageEx.ImageEx
    {
        private readonly MenuFlyout _contextMenu;
        public event EventHandler<Uri>? SaveRequested = null;

        public ImageEx2()
        {
            DefaultStyleKey = typeof(ImageEx.ImageEx);
            IsCacheEnabled = true;
            ContextRequested += OnContextRequested;

            // Build context menu once
            _contextMenu = new MenuFlyout();
            var saveItem = new MenuFlyoutItem { Text = "Save", Icon = new SymbolIcon(Symbol.Save) };
            saveItem.Click += OnSaveClick;
            _contextMenu.Items.Add(saveItem);
        }

        private void OnSaveClick(object sender, RoutedEventArgs e)
        {
            if (Source is Uri source)
            {
                SaveRequested?.Invoke(this, source);
            } else if (Source is string sourceString)
            {
                if (Uri.TryCreate(sourceString, UriKind.Absolute, out Uri? uri))
                {
                    SaveRequested?.Invoke(this, uri);
                }
            }
        }

        private void OnContextRequested(object sender, Microsoft.UI.Xaml.Input.ContextRequestedEventArgs args)
        {
            args.Handled = true;

            if (args.TryGetPosition(this, out Point position))
            {
                _contextMenu.ShowAt(this, position);
            }
            else
            {
                _contextMenu.ShowAt(this);
            }
        }

        protected override async Task<ImageSource> ProvideCachedResourceAsync(Uri imageUri, CancellationToken token)
        {
            return await ImageCache.Instance.GetFromCacheAsync(imageUri, true, token);
        }
    }
}
