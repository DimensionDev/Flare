if (process.env.FLARE_RUN_DATABASE_BENCHMARK === "true") {
    config.set({
        client: {
            mocha: {
                timeout: 300000,
            },
        },
    });
}
