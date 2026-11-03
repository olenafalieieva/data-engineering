# Replicated Log

A master node accepts messages over HTTP and replicates each one to two secondary nodes. A write waits until the required number of nodes have stored it (the write concern, 1–3, default is 3).

- **Master**: `POST /append`, `GET /list` (port 8080)
- **Secondaries**: `POST /append` (used by the master), `GET /list` (ports 8081 and 8082)

## Prerequisites

- **Docker** (Docker Desktop, or Docker Engine with the Compose plugin), to run the containers
- **JDK 17 or newer**, to run the unit tests locally (the Gradle wrapper downloads Gradle itself)

Nothing else needs to be installed.

## Run in Docker

From the project root:

```bash
docker compose up -d --build
```

This builds the image once per service and starts three containers:

| Service  | Port | Role                                |
|----------|------|-------------------------------------|
| `master` | 8080 | Accepts writes, replicates them     |
| `slave1` | 8081 | Secondary, id `slave1`              |
| `slave2` | 8082 | Secondary, id `slave2`              |

Check that they are running:

```bash
docker compose ps
```

### Send messages

Append with the default write concern (3, i.e. master plus both secondaries):

```bash
curl -X POST localhost:8080/append \
  -H 'Content-Type: application/json' \
  -d '{"content":"hello","writeConcern":3}'
```

The response is `200` with `"success":true` when the write concern is met. If it is not met, the response is `503` with the error text.

Read the stored messages from each node:

```bash
curl localhost:8080/list
curl localhost:8081/list
curl localhost:8082/list
```

### Use the master's console

The master also has an interactive console (`append <msg> [concern]`, `list`, `exit`):

```bash
docker compose attach master
```

Detach without stopping the container with `Ctrl+P` then `Ctrl+Q`. Press `Ctrl+C` to stop the master instead.

### Stop and clean up

```bash
docker compose down
```

Stores are in memory only. Restarting the containers starts with empty stores.

## Run the unit tests (from scratch)

These steps assume a fresh clone of the repository.

```bash
git clone <repository-url>
cd replicated-log
```

Run all tests:

```bash
./gradlew test
```

On Windows, use `gradlew.bat test`.

The first run downloads Gradle and the project dependencies, so it takes longer:

- `org.replog.MasterSecondaryTest`: replication, write concern, and blocking behavior with delayed secondaries (some tests take a few seconds because they check timing)

Expected result: `BUILD SUCCESSFUL`, with 9 tests passing.

To see the results:

- Console summary: printed at the end of the Gradle run.
- HTML report: `build/reports/tests/test/index.html`
- XML results: `build/test-results/test/*.xml`


## Run without Docker (optional)

Build the distribution, then start each node in its own terminal:

```bash
./gradlew installDist

# Secondaries
java -cp "build/install/replicated-log/lib/*" org.replog.SecondaryApplication --id slave1 --port 8081
java -cp "build/install/replicated-log/lib/*" org.replog.SecondaryApplication --id slave2 --port 8082

# Master
java -cp "build/install/replicated-log/lib/*" org.replog.MasterApplication --port 8080 \
  --slave-endpoints slave1=http://localhost:8081,slave2=http://localhost:8082
```

Master options: `--port`, `--network http` (the only supported mode), `--slave-endpoints slave1=http://host:8081,slave2=http://host:8082`.

Secondary options: `--id`, `--port`, `--delay <ms>` (sleeps before storing each message, used to test blocking replication).
