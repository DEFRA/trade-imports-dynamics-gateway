# Notification gateway — how it works (in plain terms)

A short guide to what the dynamics gateway does with notification events: the
happy path, how it handles errors, how the dead-letter queue (DLQ) replay works,
and why **deduplication** matters.

> Diagrams are embedded as SVG images (in [`diagrams/`](diagrams/)) so they
> display in any viewer, including the built-in editor preview. The Mermaid
> source for each is kept in a collapsible block beneath it and can be
> re-rendered with [`diagrams/README.md`](diagrams/README.md).

---

## 1. What the gateway is for

The gateway is a **relay**. The backend raises notification events; the gateway
forwards each one to **Azure Service Bus (ASB)**, where downstream (Dynamics)
picks them up.

![Architecture: backend to SNS to SQS to gateway to Azure Service Bus, with DLQ and admin app](diagrams/01-architecture.svg)

<details><summary>Diagram source (Mermaid)</summary>

```mermaid
flowchart LR
    BE["Backend<br/>(outbox)"] -->|publish| SNS["SNS FIFO topic"]
    SNS -->|fan-out| Q["SQS FIFO<br/>source queue"]
    Q -->|listener consumes| GW["Dynamics Gateway"]
    GW -->|forward| ASB["Azure Service Bus"]
    Q -.->|after 3 failed tries| DLQ["SQS FIFO<br/>DLQ"]
    ADMIN["Admin app<br/>(operator)"] -->|list / replay / delete| GW
```

</details>

Two ids travel with every event and matter throughout:

| Id | Set by | Purpose |
|----|--------|---------|
| **`messageGroupId`** = `aggregateId` | backend | FIFO ordering — events for the same aggregate stay in order |
| **`eventId`** (also the SQS `MessageDeduplicationId`) | backend | the stable identity used to avoid processing the same event twice |

The `eventId` is carried **both** as the SQS dedup id **and** inside the message
body, so it survives even when the transport-level id is changed.

---

## 2. Happy path — one event forwarded

![Happy path: queue delivers to listener, listener publishes to ASB, gets ack, deletes message](diagrams/02-happy-path.svg)

<details><summary>Diagram source (Mermaid)</summary>

```mermaid
sequenceDiagram
    participant Q as SQS source queue
    participant L as Gateway listener
    participant ASB as Azure Service Bus

    Q->>L: deliver event (groupId=A, eventId=evt-1)
    Note over L: validate: group id present?<br/>body non-blank & valid JSON?
    L->>ASB: publish (sessionId=A, messageId=evt-1)
    ASB-->>L: ack
    L->>Q: delete message (done)
```

</details>

The message stays **invisible** on the queue (30s visibility timeout) while the
gateway works. On success it is deleted, so no one else sees it.

---

## 3. How errors are handled

Every ASB failure is sorted into one of two buckets. This decides whether we
**retry** or **discard**.

![Error handling: transient failures retry then redeliver to DLQ after 3 receives; permanent failures are deleted immediately](diagrams/03-error-handling.svg)

<details><summary>Diagram source (Mermaid)</summary>

```mermaid
sequenceDiagram
    participant Q as SQS source queue
    participant L as Gateway listener
    participant ASB as Azure Service Bus
    participant DLQ as DLQ

    Q->>L: deliver event (groupId=A, eventId=evt-1)
    L->>ASB: publish (attempt 1)
    ASB-->>L: failure

    alt Transient failure (timeout, throttle, network) — RETRYABLE
        Note over L: in-process retry, up to 4 tries (1s to 2s to 4s + jitter)
        loop until success or 4 tries used
            L->>ASB: publish (retry)
        end
        alt a retry succeeds
            ASB-->>L: ack
            L->>Q: delete message (done)
        else still failing after 4 tries
            Note over L,Q: leave on queue (not acknowledged)
            Q->>Q: visibility timeout expires, redeliver
            Note over Q: once received 3 times, SQS moves it on
            Q->>DLQ: move to DLQ
        end
    else Permanent failure (bad message, unauthorised) — NON-RETRYABLE
        L->>Q: delete message immediately (retry won't help)
    end
```

</details>

**Key rule:** the whole in-process retry window (≈7s at defaults) must stay
**shorter than the 30s visibility timeout**. If a retry outlived the timeout, the
message would reappear and a second consumer could process it at the same time —
a duplicate. A startup check enforces this.

---

## 4. Replaying from the DLQ

Once a message is on the DLQ, an operator uses the admin app to **list**,
**replay**, or **delete** it. Replay re-sends it to the source queue so the
gateway tries again.

![Replay: operator asks gateway to replay, gateway finds it on the DLQ, re-sends to source queue, then deletes from DLQ](diagrams/04-replay.svg)

<details><summary>Diagram source (Mermaid)</summary>

```mermaid
sequenceDiagram
    participant OP as Operator (admin app)
    participant GW as Gateway (DlqService)
    participant DLQ as DLQ
    participant Q as SQS source queue

    OP->>GW: replay [evt-1]  (with admin secret)
    GW->>DLQ: receive & find evt-1
    GW->>Q: re-send (groupId=A, dedup id from evt-1)
    Q-->>GW: send OK
    GW->>DLQ: delete evt-1
    Note over GW: only messages that actually<br/>re-sent are deleted from the DLQ
```

</details>

Guardrails already in place:
- **List** is read-only (no secret). **Replay/delete** require the admin secret.
- Re-send happens **before** delete, so a failed re-send leaves the message on
  the DLQ.
- Ids that can't be found in the receive window are logged and **left** for a retry.

---

## 5. Deduplication — the important bit

SQS FIFO queues **ignore a second send with the same `MessageDeduplicationId`
for 5 minutes**. This normally protects us from accidental double-sends.

### Normal case — dedup protects us

![Normal dedup: a second send of the same eventId within 5 minutes is silently ignored, preventing a duplicate](diagrams/05a-dedup-normal.svg)

<details><summary>Diagram source (Mermaid)</summary>

```mermaid
sequenceDiagram
    participant BE as Backend
    participant Q as SQS source queue
    BE->>Q: send evt-1 (00:00) - accepted
    BE->>Q: send evt-1 again (00:30)
    Note over Q: same id within 5 min ->
    Note over Q: silently ignored (good: no duplicate)
```

</details>

### Gotcha — replaying too soon can be silently dropped

If a message reaches the DLQ quickly (3 tries × 30s ≈ 90s) and the operator
replays **within 5 minutes of the original send**, re-sending with the *same*
`eventId` hits the still-open dedup window:

![Gotcha: replaying within 5 minutes with the same dedup id is accepted but not delivered, then deleted from the DLQ, so the message is lost](diagrams/05b-dedup-gotcha.svg)

<details><summary>Diagram source (Mermaid)</summary>

```mermaid
sequenceDiagram
    participant BE as Backend
    participant Q as SQS source queue
    participant DLQ as DLQ
    participant OP as Operator

    BE->>Q: send evt-1 (00:00) - accepted
    Note over Q: fails 3 times -> DLQ at ~01:30
    OP->>Q: replay evt-1 (03:00, same dedup id)
    Note over Q: still inside 5-min window ->
    Note over Q: accepted but NOT delivered
    OP->>DLQ: (gateway) delete evt-1
    Note over OP,DLQ: message now gone from both queues -
    Note over OP,DLQ: lost with no error shown
```

</details>

**Why the `eventId`-in-the-body matters:** the fix for this is to give the
*replay* a fresh, unique transport dedup id (so SQS always accepts it) while
keeping the **`eventId` from the body** as the ASB `messageId`. That way ordering
and cross-system dedup stay correct, but a deliberate replay is never suppressed.

---

## 6. Many events at once — FIFO ordering

Events are ordered **per `messageGroupId` (aggregate)**. Different aggregates flow
independently and in parallel; within one aggregate, a stuck message blocks the
ones behind it until it clears.

![FIFO ordering: group A events processed strictly in order, group B flows independently in parallel](diagrams/06-fifo-ordering.svg)

<details><summary>Diagram source (Mermaid)</summary>

```mermaid
flowchart LR
    subgraph GA["Group A (aggregate A)"]
        direction LR
        A1["evt-1"] --> A2["evt-2"] --> A3["evt-3"]
    end
    subgraph GB["Group B (aggregate B)"]
        direction LR
        B1["evt-9"] --> B2["evt-10"]
    end
    GA -->|processed strictly in order| ASB["ASB"]
    GB -->|independent of Group A| ASB
```

</details>

- **Group A** is delivered `evt-1`, then `evt-2`, then `evt-3` — never out of order.
- If `evt-1` keeps failing, `evt-2` and `evt-3` **wait behind it** (and eventually
  `evt-1` goes to the DLQ, unblocking the rest).
- **Group B** is unaffected by Group A — it keeps flowing.

This also explains a DLQ replay/delete quirk: if you ask to replay `evt-3` while
`evt-1` (same group, not selected) is still ahead of it on the queue, the gateway
can't reach `evt-3` yet and reports it "not found" — clear the predecessor first.

---

## In one sentence

> The gateway forwards each backend notification to Azure Service Bus in order,
> retries transient failures briefly, sends anything that keeps failing to a DLQ
> for an operator to replay, and uses the event's stable `eventId` to make sure
> the same event is never processed twice.
