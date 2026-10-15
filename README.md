# real-time-chat-application

A small real-time chat server and web UI: group rooms, 1:1 direct messages, join/leave, and live delivery
to everyone in a conversation. Spring Boot 4.1 on Java 25, PostgreSQL 16, and a single-page UI in plain
JavaScript.

## Features

- Three group rooms (`general`, `random`, `help`) seeded on startup; join and leave them.
- Direct messages: exactly one conversation per pair of users, created on first use.
- Send messages over REST, receive them live over a WebSocket.
- Gap-free per-conversation sequence numbers, history paging by `afterSeq`, and catch-up after reconnect.
- Idempotent sends via a client-chosen message id, so retries never duplicate a message.
- Unread counts per member.
- Every error is an RFC 9457 `application/problem+json` body with the matching status code.

## Design decisions

| Decision | Why |
|---|---|
| **Send over REST, receive over a plain WebSocket** | Sending gets ordinary HTTP status codes, retries and validation errors. The socket only pushes, so it needs no protocol beyond one JSON frame type. No STOMP or broker. |
| **A DM is a `DIRECT` conversation with exactly two members** | Rooms and DMs share all the message, history, push and unread code. A unique `direct_key` (`"lowerUserId:higherUserId"`) plus `INSERT … ON CONFLICT DO NOTHING` guarantees one DM per pair, even under concurrent requests. |
| **Messages ordered by a per-conversation `seq`** | Sending locks the conversation row (`SELECT … FOR UPDATE`), increments `last_seq` and inserts the message in one transaction. Concurrent sends get consecutive numbers, and a failed send rolls back its increment, so there are never gaps. Because of that, clients can spot a missed message (`seq` jumps) and unread is simple arithmetic. |
| **Push only after commit** | A `@TransactionalEventListener(AFTER_COMMIT)` pushes to members' sockets, so clients never see a message that was then rolled back. Each socket has its own outbox written by one virtual thread, so the sending request only enqueues. A slow client delays nobody else, and frames reach each client in order. |
| **Catch-up is the client's job** | On connect and reconnect the UI opens the socket *first*, then fetches history after the newest `seq` it holds. Anything sent in between arrives by one path or the other, and duplicates are dropped by `seq`. |
| **Idempotent send via `clientMessageId`** | A retry with the same id returns the original message (`200` instead of `201`). The lookup runs under the same row lock as the insert, so concurrent retries can't both store it. Reusing an id for different content is a `409`. |
| **Unread = `last_seq − last_read_seq`** | Each membership keeps a read pointer that only moves forward. Your own messages count as read, and joining a room starts with nothing unread. |
| **Schema recreated on every start** (`ddl-auto=create`) | Fast iteration for a prototype; there is no data worth migrating. |

## Out of scope

- **Authentication.** Users are identified by the username in the URL or body, so anyone can act as anyone. Users are created on first use.
- Typing indicators, presence, attachments, message edit/delete, notifications, rate limiting.
- Running more than one server instance. Socket sessions live in memory, so a push only reaches clients connected to the server that handled the send.
- Durable data. Restarting the app wipes the database.

## Running it

You need Java 25 and PostgreSQL. Maven comes with the repo (`./mvnw`). The app expects Postgres on
`localhost:5432` with user/password `postgres`/`postgres` and two databases:

```bash
sudo service postgresql start                       # if it isn't running
sudo runuser -u postgres -- psql -c "ALTER USER postgres PASSWORD 'postgres';"
sudo runuser -u postgres -- createdb chat           # used by the app
sudo runuser -u postgres -- createdb chat_test      # used by the tests (they recreate the schema)
```

Start the app:

```bash
./mvnw spring-boot:run
```

Then open <http://localhost:8080> in two tabs and log in as two different users. The username is kept per
tab. Join `# general` in both and chat, or start a DM by typing the other user's name.

## API

| Method & path | Body / query | Result |
|---|---|---|
| `GET /api/rooms` | | Group rooms with their members |
| `PUT /api/rooms/{id}/members/{username}` | | Join (idempotent) → `204` |
| `DELETE /api/rooms/{id}/members/{username}` | | Leave (idempotent) → `204` |
| `POST /api/dms` | `{"userA","userB"}` | The pair's DM: `201` if created, `200` if it existed |
| `POST /api/conversations/{id}/messages` | `{"sender","content","clientMessageId"?}` | `201` new message, `200` replayed retry. Members only (`403`) |
| `GET /api/conversations/{id}/messages` | `?user=&afterSeq=0&limit=50` | `{messages, hasMore}`, oldest first. Members only |
| `PUT /api/conversations/{id}/read` | `{"user","seq"}` | Advance the read pointer → `204` |
| `GET /api/users/{username}/conversations` | | The user's rooms and DMs with `lastSeq`, `lastReadSeq`, `unreadCount` |
| `ws://host/ws?user={username}` | | Pushes `{"type":"message","message":{…}}` for the user's conversations |

## Testing

```bash
./mvnw verify            # 27 tests against the chat_test database
scripts/smoke.sh         # curl checks against a running app; exits non-zero on any mismatch
scripts/smoke.sh http://host:port
```

The tests include:

- **Concurrency:** 200 parallel sends must get exactly `seq` 1..200, and 64 parallel DM creations for one
  pair (in both argument orders) must yield one conversation.
- **Idempotency:** 32 concurrent retries with the same `clientMessageId` must store the message once.
- **WebSocket delivery:** two members receive a message and a connected non-member doesn't. A stalled client
  must delay neither the sender nor other members.
- **Unread counts, history paging, and status codes plus problem+json bodies** for error cases.

The concurrency and stalled-client tests were checked by breaking the code they protect: without the row
lock, or with pushes written on the request thread, they fail.

## Known limitations

- **No heartbeat.** A connection that dies silently (for example, a proxy idle timeout) can leave the UI
  showing "online" without receiving pushes until it reconnects or reloads. The fix is server pings plus a
  client watchdog.
- **Quick repeats can be dropped.** If you send the same text twice before the first send's response
  arrives, the UI reuses the retry id and the server treats the second send as a retry.

## Built with Claude Code

This was built in one time-boxed session with [Claude Code](https://claude.com/claude-code), working through
a numbered list of prompts:

1. Environment setup.
2. A ten-line design proposal. The human made the key calls: REST to send, WebSocket to receive, DMs as
   two-member conversations, and per-conversation sequence numbers.
3. Feature by feature: scaffold, rooms, DMs, sending, live push, history, UI, idempotency, errors, unread.
   Each feature was run against the live app before moving on.

Two habits were worth keeping:

- **Make tests prove they can fail.** Each concurrency test was re-run with the protecting code removed.
- **Finish with a review pass.** Reviewing the whole codebase for bugs and races surfaced the three issues
  fixed at the end:
  - pushes blocking the sending request while holding a DB connection;
  - tests wiping the dev database;
  - a leave/send race.

  The two that remain are listed above.
