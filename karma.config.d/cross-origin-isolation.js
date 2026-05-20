config.set({
    customHeaders: [
        {
            match: '.*',
            name: 'Cross-Origin-Opener-Policy',
            value: 'same-origin',
        },
        {
            match: '.*',
            name: 'Cross-Origin-Embedder-Policy',
            value: 'require-corp',
        },
    ],
});
