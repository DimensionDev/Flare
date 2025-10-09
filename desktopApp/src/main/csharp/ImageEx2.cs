using Flare.Cache;
using Microsoft.UI.Xaml.Media;
using Microsoft.UI.Xaml.Media.Imaging;
using System;
using System.Collections.Generic;
using System.Linq;
using System.Text;
using System.Threading;
using System.Threading.Tasks;

namespace Flare
{
    internal class ImageEx2 : ImageEx.ImageEx
    {
        public ImageEx2()
        {
            DefaultStyleKey = typeof(ImageEx.ImageEx);
            IsCacheEnabled = true;
        }

        protected override async Task<ImageSource> ProvideCachedResourceAsync(Uri imageUri, CancellationToken token)
        {
            return await ImageCache.Instance.GetFromCacheAsync(imageUri, true, token);
        }
    }
}
