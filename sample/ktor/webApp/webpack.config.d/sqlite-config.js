// {project}/webpack.config.d/sqlite.js
const path = require('path');

config.resolve = {
    modules: [
        'node_modules',
        // Needed so that the aliased sqlite.worker.js (which lives outside the generated npm package
        // directory) can still resolve its own `import ... from "@sqlite.org/sqlite-wasm"` dependency.
        path.resolve(__dirname, '../../node_modules'),
    ],
    fallback: {
        fs: false,
        path: false,
        crypto: false,
        os: false,
    },
    alias: {
        ...(config.resolve && config.resolve.alias),
        // WebWorkerSQLiteDriver needs a Web Worker implementing its protocol (none is shipped with it).
        // Redirect webpack's bundling of
        // `new Worker(new URL("ktor-monitor-sqlite-worker/sqlite.worker.js", import.meta.url))`
        // (in DatabaseFactory.kt) to the SQLite WASM based worker.
        'ktor-monitor-sqlite-worker/sqlite.worker.js': path.resolve(__dirname, '../../../../sample/ktor/webApp/webpack-resources/sqlite.worker.js'),
    },
};
