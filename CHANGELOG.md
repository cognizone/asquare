x.x.x
- deprecated URI generator in favor of https://github.com/cognizone/semanticz-irigenerator

## [0.8.0] - 2025-07-09

### Changed
- Upgraded from Springboot 2.7.18 to Springboot 3.5.3
- Upgraded from Java 11 to Java 17
- Upgraded gradle to 7.6.4 to be compatible with Java 17
- Upgraded from Jena 4 to Jena 5
- Upgraded rest of libs to match the new java/spring versions

- Removed security and security2 modules, also asquareroot/security package because security should be managed by https://github.com/cognizone/security
- Removed LocalTdbRdfStoreService as it works only with Jena 4. Please make sure you correctly migrate your db (https://jena.apache.org/documentation/tdb2/tdb2_migration.html) as tdb1 and tdb2 are not compatible.
Below you will find some of the differences of LocalTdbRdfStoreService and existing Tdb2StoreService

# `LocalTdbRdfStoreService` vs `Tdb2StoreService`

Both `LocalTdbRdfStoreService` and `Tdb2StoreService` implement the `RdfStoreService` interface and provide access to a Jena RDF store. However, they differ significantly in terms of initialization logic, timeout handling, transaction control and additional utilities.

---

## Functional Differences

| Feature                   | `LocalTdbRdfStoreService`                          | `Tdb2StoreService`                            |
|---------------------------|----------------------------------------------------|-----------------------------------------------|
| Dataset source            | File-based only                                   | File-based or in-memory                       |
| Model exposed?            | Yes (`getModel()` – Deprecated warning)           | Yes (model field used internally)             |
| Custom transactions       | ❌ No (uses `Txn`)                                 | ✅ Yes (via `Tdb2Transaction`)                |
| Force release dataset     | ✅ `forceRelease()`                                | ❌ Not available                               |
| Timeout Management        | ✅ Built-in support for timeouts                   | ❌ No built-in support                         |

---

## Differences in Public Methods

| Method Signature                        | Present In   | Absent In   | Notes                                                                 |
|----------------------------------------|--------------|-------------|-----------------------------------------------------------------------|
| `Model getModel()`                     | LocalTdb     | Tdb2        | Deprecated and warns about memory leaks                              |
| `void forceRelease()`                  | LocalTdb     | Tdb2        | Forcefully releases connections via `StoreConnection`                |
| `boolean isEmpty()`                    | LocalTdb     | Tdb2        | Not present in Tdb2                                                  |
| `long size()`                          | LocalTdb     | Tdb2        | Not present in Tdb2                                                  |
| `Model constructAllTriples()`          | LocalTdb     | Tdb2        | Utility for a full triple dump                                       |
| `Tdb2Transaction getTransaction()`     | Tdb2         | LocalTdb    | Exposes internal transaction manager                                 |
| `void compact()`                       | Tdb2         | LocalTdb    | Optimizes TDB2 dataset using `DatabaseMgr`                           |

---

## Common Method Differences

### `executeSelectQuery(Query, QuerySolutionMap, Handler, Context)`

| Feature         | `LocalTdb`                                      | `Tdb2`                                           |
|----------------|--------------------------------------------------|--------------------------------------------------|
| QueryExecution | Built with timeout support                       | Created directly using `QueryExecutionFactory`   |
| Safety         | Uses `safeQuery` with logging & recovery         | Basic try-catch with logging only               |

---

## Transaction Management

| Feature         | `LocalTdb`                     | `Tdb2`                                                |
|----------------|----------------------------------|--------------------------------------------------------|
|                | Uses `Txn` wrappers              | Wraps operations in a `Tdb2Transaction` helper object |

---

## Error Handling

| Feature         | `LocalTdb`                     | `Tdb2`                                                |
|----------------|----------------------------------|--------------------------------------------------------|
|                | Query timeouts                   | Wraps operations in a Tdb2Transaction helper object.           |
