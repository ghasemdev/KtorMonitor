// Web Worker implementing the `androidx.sqlite.driver.web.WebWorkerSQLiteDriver` message protocol on top of
// SQLite WASM (https://sqlite.org/wasm). Databases are kept in memory (no OPFS), so no special HTTP headers
// (COOP / COEP) are required to host the app. The monitor data therefore lasts for the lifetime of the page.
import sqlite3InitModule from '@sqlite.org/sqlite-wasm';

let sqlite3 = null;

// An in-memory database is private to its connection, while Room opens its connections by file name.
// Connections opened with the same file name share one underlying database (ref-counted).
const sharedDatabases = new Map(); // fileName -> { db, refCount }
const databases = new Map(); // databaseId -> { fileName, db }
const statements = new Map(); // statementId -> statement

let nextDatabaseId = 1;
let nextStatementId = 1;

function open(id, request) {
    let shared = sharedDatabases.get(request.fileName);
    if (!shared) {
        shared = { db: new sqlite3.oo1.DB(':memory:', 'c'), refCount: 0 };
        sharedDatabases.set(request.fileName, shared);
    }
    shared.refCount++;
    const databaseId = nextDatabaseId++;
    databases.set(databaseId, { fileName: request.fileName, db: shared.db });
    return { databaseId };
}

function prepare(id, request) {
    const entry = databases.get(request.databaseId);
    if (!entry) throw new Error('Invalid database ID: ' + request.databaseId);
    const statement = entry.db.prepare(request.sql);
    const statementId = nextStatementId++;
    statements.set(statementId, statement);
    const columnNames = [];
    for (let i = 0; i < statement.columnCount; i++) {
        columnNames.push(sqlite3.capi.sqlite3_column_name(statement, i));
    }
    return {
        statementId,
        parameterCount: sqlite3.capi.sqlite3_bind_parameter_count(statement),
        columnNames,
    };
}

function step(id, request) {
    const statement = statements.get(request.statementId);
    if (!statement) throw new Error('Invalid statement ID: ' + request.statementId);
    const rows = [];
    const columnTypes = [];
    statement.reset(true);
    try {
        const bindings = request.bindings || [];
        for (let i = 0; i < bindings.length; i++) {
            statement.bind(i + 1, bindings[i]);
        }
        while (statement.step()) {
            if (columnTypes.length === 0) {
                for (let i = 0; i < statement.columnCount; i++) {
                    columnTypes.push(sqlite3.capi.sqlite3_column_type(statement, i));
                }
            }
            rows.push(statement.get([]));
        }
    } finally {
        statement.reset(true);
    }
    return { rows, columnTypes };
}

// One-way command, no response is sent.
function close(id, request) {
    if (request.statementId != null) {
        const statement = statements.get(request.statementId);
        if (statement) {
            statement.finalize();
            statements.delete(request.statementId);
        }
    }
    if (request.databaseId != null) {
        const entry = databases.get(request.databaseId);
        if (entry) {
            databases.delete(request.databaseId);
            const shared = sharedDatabases.get(entry.fileName);
            if (shared && --shared.refCount === 0) {
                sharedDatabases.delete(entry.fileName);
                shared.db.close();
            }
        }
    }
}

const commands = { open, prepare, step, close };

function handleMessage(message) {
    const { id, data } = message;
    const isClose = data && data.cmd === 'close';
    try {
        const handler = data && commands[data.cmd];
        if (!handler) {
            throw new Error("Invalid request, unknown command: '" + (data && data.cmd) + "'.");
        }
        const result = handler(id, data);
        if (!isClose) {
            postMessage({ id, data: result });
        }
    } catch (error) {
        if (!isClose) {
            postMessage({ id, error: error && error.message ? error.message : String(error) });
        }
    }
}

// Requests can arrive before the WASM module is ready.
const pending = [];
onmessage = (event) => {
    if (sqlite3) {
        handleMessage(event.data);
    } else {
        pending.push(event.data);
    }
};

sqlite3InitModule().then((instance) => {
    sqlite3 = instance;
    pending.splice(0).forEach(handleMessage);
});
