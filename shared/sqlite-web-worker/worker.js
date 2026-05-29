/**
 * Copyright 2026 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import sqlite3InitModule from "@sqlite.org/sqlite-wasm";

let sqlite3 = null;

const databases = new Map();
const statements = new Map();
const statementDatabases = new Map();

let nextDatabaseId = 0;
let nextStatementId = 0;

function createDatabase(fileName) {
  if (fileName && typeof sqlite3.oo1.OpfsDb === "function") {
    return new sqlite3.oo1.OpfsDb(fileName);
  }
  return new sqlite3.oo1.DB(fileName || ":memory:", "c");
}

function openRequest(id, requestData) {
  try {
    const newDatabaseId = nextDatabaseId++;
    const newDatabase = createDatabase(requestData.fileName);
    databases.set(newDatabaseId, newDatabase);
    postMessage({ id, data: { databaseId: newDatabaseId } });
  } catch (error) {
    postMessage({ id, error: error.message });
  }
}

function prepareRequest(id, requestData) {
  try {
    const newStatementId = nextStatementId++;
    const resultData = {
      statementId: newStatementId,
      parameterCount: 0,
      columnNames: [],
    };
    const database = databases.get(requestData.databaseId);
    if (!database) {
      postMessage({ id, error: `Invalid database ID: ${requestData.databaseId}` });
      return;
    }
    const statement = database.prepare(requestData.sql);
    statements.set(newStatementId, statement);
    statementDatabases.set(newStatementId, requestData.databaseId);
    resultData.parameterCount = sqlite3.capi.sqlite3_bind_parameter_count(statement);
    for (let i = 0; i < statement.columnCount; i++) {
      resultData.columnNames.push(sqlite3.capi.sqlite3_column_name(statement, i));
    }
    postMessage({ id, data: resultData });
  } catch (error) {
    postMessage({ id, error: error.message });
  }
}

function stepRequest(id, requestData) {
  const statement = statements.get(requestData.statementId);
  if (!statement) {
    postMessage({ id, error: `Invalid statement ID: ${requestData.statementId}` });
    return;
  }
  try {
    const resultData = {
      rows: [],
      columnTypes: [],
    };
    statement.reset();
    statement.clearBindings();
    for (let i = 0; i < requestData.bindings.length; i++) {
      statement.bind(i + 1, requestData.bindings[i]);
    }
    while (statement.step()) {
      if (!resultData.columnTypes.length) {
        for (let i = 0; i < statement.columnCount; i++) {
          resultData.columnTypes.push(sqlite3.capi.sqlite3_column_type(statement, i));
        }
      }
      resultData.rows.push(statement.get([]));
    }
    postMessage({ id, data: resultData });
  } catch (error) {
    postMessage({ id, error: error.message });
  }
}

function closeRequest(id, requestData) {
  if (requestData.statementId !== undefined && requestData.statementId != null) {
    const statement = statements.get(requestData.statementId);
    if (!statement) {
      postMessage({ id, error: `Invalid statement ID: ${requestData.statementId}` });
      return;
    }
    try {
      statement.finalize();
      statements.delete(requestData.statementId);
      statementDatabases.delete(requestData.statementId);
    } catch (error) {
      postMessage({ id, error: error.message });
      return;
    }
  }

  if (requestData.databaseId !== undefined && requestData.databaseId != null) {
    const database = databases.get(requestData.databaseId);
    if (!database) {
      postMessage({ id, error: `Invalid database ID: ${requestData.databaseId}` });
      return;
    }
    try {
      for (const [statementId, databaseId] of Array.from(statementDatabases.entries())) {
        if (databaseId === requestData.databaseId) {
          const statement = statements.get(statementId);
          if (statement) {
            statement.finalize();
          }
          statements.delete(statementId);
          statementDatabases.delete(statementId);
        }
      }
      database.close();
      databases.delete(requestData.databaseId);
    } catch (error) {
      postMessage({ id, error: error.message });
      return;
    }
    postMessage({ id, data: null });
  }
}

const commandMap = {
  open: openRequest,
  prepare: prepareRequest,
  step: stepRequest,
  close: closeRequest,
};

function handleMessage(e) {
  const requestMsg = e.data;
  if (!Object.hasOwn(requestMsg, "data") && requestMsg.data == null) {
    postMessage({ id: requestMsg.id, error: "Invalid request, missing 'data'." });
    return;
  }
  if (!Object.hasOwn(requestMsg.data, "cmd") && requestMsg.data.cmd == null) {
    postMessage({ id: requestMsg.id, error: "Invalid request, missing 'cmd'." });
    return;
  }
  const command = requestMsg.data.cmd;
  const requestHandler = commandMap[command];
  if (requestHandler) {
    requestHandler(requestMsg.id, requestMsg.data);
  } else {
    postMessage({ id: requestMsg.id, error: `Invalid request, unknown command: '${command}'.` });
  }
}

const messageQueue = [];
onmessage = (e) => {
  if (!sqlite3) {
    messageQueue.push(e);
  } else {
    handleMessage(e);
  }
};

sqlite3InitModule().then((instance) => {
  sqlite3 = instance;
  while (messageQueue.length > 0) {
    handleMessage(messageQueue.shift());
  }
});
