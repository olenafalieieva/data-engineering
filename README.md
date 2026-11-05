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

### Write concern (`w`)

The write concern `w` sets how many nodes must store a message before the master responds. It counts the master itself, so:

| `w` | Master responds after                              | Typical use                       |
|-----|----------------------------------------------------|-----------------------------------|
| 1   | Storing on the master only                         | Fastest; secondaries catch up later |
| 2   | The master and one secondary have stored it        | Survives loss of one node         |
| 3   | The master and both secondaries have stored it     | Full replication (the default)    |

Valid values are `1` to `N`, where `N` is the number of nodes (3 with two secondaries). Replication to the other secondaries continues in the background after the master responds. A value outside that range returns `400`.

Over HTTP, pass `writeConcern` in the JSON body. Omitting it means 3:

```bash
# w=1: returns immediately, even if a secondary is slow
curl -X POST localhost:8080/append -H 'Content-Type: application/json' \
  -d '{"content":"fast","writeConcern":1}'

# w=2: returns after the master and one secondary have stored it
curl -X POST localhost:8080/append -H 'Content-Type: application/json' \
  -d '{"content":"two","writeConcern":2}'

# w=3: waits for both secondaries
curl -X POST localhost:8080/append -H 'Content-Type: application/json' \
  -d '{"content":"all","writeConcern":3}'
```

If the required number of nodes can't store the message (for example, a secondary is down), the response is `503` with `"Not enough confirmations"`. The master keeps the message in its own list in that case.

In the console, add the concern after the message: `append <msg> <concern>`. Without it the concern is 3.

Messages are applied on every node in the same order the master received them, even when a secondary receives them out of order.

### Use the master's console

The master also has an interactive console (`append <msg> [concern]`, `list`, `exit`):

```bash
docker compose attach master
```

Console examples:

```text
> append hello          # writeConcern=3: master plus all secondaries
> append hello 1        # writeConcern=1: master only, returns immediately
> append hello 2        # writeConcern=2: master plus one secondary
> list
> exit
```

### Master request reference

| Request | Body | Response |
|---------|------|----------|
| `POST /append` | `{"content":"hello","writeConcern":3}` | `200` with `success`, `messageId`, `confirmations` |
| `POST /append` | `{"content":"hello"}` (no `writeConcern`) | Same as `writeConcern` 3 |
| `POST /append` | `writeConcern` outside `1`–`3`, or no `content` | `400`, empty body |
| `POST /append` | Write concern not reached | `503` with `error`: `Not enough confirmations` |
| `GET /list` | | `200` with all messages on the master |

```bash
# Default write concern (3)
curl -X POST localhost:8080/append -H 'Content-Type: application/json' \
  -d '{"content":"hello"}'

# Invalid write concern (400)
curl -i -X POST localhost:8080/append -H 'Content-Type: application/json' \
  -d '{"content":"hello","writeConcern":4}'

# List messages
curl localhost:8080/list
```

Detach without stopping the container with `Ctrl+P` then `Ctrl+Q`. Typing `exit` or pressing `Ctrl+C` stops the master container. Start it again with `docker compose up -d`.

### Stop and clean up

```bash
docker compose down
```

Stores are in memory only. Restarting the containers starts with empty stores.

### Simulate a slow secondary

Each secondary can delay storing every message by a number of milliseconds. The delay defaults to 0. Set it when starting the containers:

```bash
SLAVE2_DELAY_MS=5000 docker compose up -d
```

`SLAVE1_DELAY_MS` works the same way for `slave1`. With a 5-second delay on `slave2`, an append with write concern 3 takes about 5 seconds, while write concern 1 returns straight away. Run `docker compose up -d` without the variable to reset the delay.

## Run the unit tests (from scratch)

These steps assume a fresh clone of the repository.

```bash
git clone <repository-url>
cd replicated-log
```

Run all tests:

```bash
./gradlew cleanTest test
```

On Windows, use `gradlew.bat cleanTest test`.

`cleanTest` forces the tests to run every time. Without it, Gradle reports `BUILD SUCCESSFUL` with "3 up-to-date" and skips the tests when nothing has changed since the last run, so no test output is printed.

The first run downloads Gradle and the project dependencies, so it takes longer:

- `org.replog.MasterSecondaryTest`: replication, write concern, and blocking behavior with delayed secondaries (some tests take a few seconds because they check timing)

Expected result: `BUILD SUCCESSFUL`, with 13 tests passing.

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
