# Observability

## Publishing

`EmfMeterRegistry` publishes timers, distribution summaries, counters and gauges to CloudWatch as EMF once a minute (`aws.emf.step`), in the `aws.emf.namespace` namespace, with a meter's tags as its only dimensions. It does not publish function counters, function timers, long task timers or time gauges. A meter is registered, and so published, only when its name or a dot-separated prefix of it is enabled under `management.metrics.enable` in `application.yml`: `controller: true` lets through every `controller.*` meter.

`aws.emf.enabled` is the one switch for publishing. `aws.emf.environment` is blank on CDP, which leaves the EMF library to detect ECS and send to the platform's agent; set it to `Local` to write the EMF documents to stdout. With `aws.emf.enabled` false, as in the integration tests, only the cumulative registry below exists and nothing is published.

Every meter is also recorded in a cumulative in-memory registry, and the actuator's `/metrics` endpoint reads that one. `EmfMeterRegistry` is a step registry: a counter there holds only the last completed minute's count, so reading it through `/metrics` would show 0 for anything counted since. `MetricsConfig` puts both registries behind one composite `MeterRegistry`, marked `@Primary`, which is what the code injects.

## External-call metrics

Every call to a system outside the INS boundary is recorded by `ExternalCallRecorder`, which also keeps the call's outcome for `external.dependency.available`, as two meters, tagged `dependency` and `operation`:

- `external.call.duration`: a timer of how long the call took, published in milliseconds
- `external.call.failure`: a distribution summary of 1 for a failed call and 0 for a successful one, so its average is the failure rate

Each call is also logged as one line with the nested ECS fields `event.category` (`external-call`), `event.action` (`<dependency>/<operation>`), `event.outcome` and `event.duration` (nanoseconds), and on a failure `error.type` and `error.message`. `EcsEventEncoder` writes the `event.*` fields nested, because CDP drops them flat.

| Dependency | Operation | Measured in |
|---|---|---|
| `azure-service-bus` | `send-message` | `QueueMessageSender`, which runs `ServiceBusSenderClient.sendMessage` through `ExternalCallRecorder.measure` |
| `sqs` | the SQS action in kebab case: `receive-message`, `delete-message`, `delete-message-batch`, `change-message-visibility`, `change-message-visibility-batch`, `get-queue-attributes`, `get-queue-url`, `start-message-move-task`, `purge-queue`; any other action is `other` | `SqsCallRecorder`, an AWS SDK `ExecutionInterceptor` on the one `SqsAsyncClient` |

`SqsCallRecorder` sees every SQS call the service makes, including the listener container's long polls and acknowledgements, and records an SDK call once, its retries counted in its duration. A long poll that finds nothing still takes up to its wait time, so a `receive-message` duration is not a latency.

## Other metrics

| Metric | Type | Tags | What it records |
|---|---|---|---|
| `external.dependency.available` | gauge | `dependency` | 1 after the dependency's most recent call succeeded, 0 after it failed; not published until it has been called. The listener polls SQS every few seconds, so `sqs` is always current; `azure-service-bus` changes only when a send happens |
| `notification.message` | counter | `outcome` (`forwarded`, `failed`), `schema.version` (`0.1.0`, `0.2.0`, `0.3.0`, `0.4.0`) | Each PIMS payload the listener maps and sends, per schema version. A failed 0.1.0 send fails the SQS message; a failed 0.2.0, 0.3.0 or 0.4.0 send is logged and not retried |
| `notification.error` | counter | `action` (`retry`, `discarded`) | Each SQS message the listener could not process: left for redelivery after a transient failure, or deleted after a permanent one |
| `sqs.queue.depth` | gauge | `queue` (`notification`, `notification-dlq`) | `ApproximateNumberOfMessages` on the source queue and on its dead-letter queue, read from SQS each time the gauge is published |
| `controller.<operation>.time`, `service.<operation>.time` | timer | `class`, `method`, `exception` | Each call to a `@Timed` endpoint or service method. The `sqs.queue.depth` gauge reads through the services, so each reading of it is a call too: `service.queueDepth.time` counts the `notification` gauge's reads as well as `GET /queue/notifications`, and `service.dlqDepth.time` counts the `notification-dlq` gauge's reads |
| `listener.notification.time` | timer | `class`, `method`, `exception` | Each SQS message the listener handles, all its sends included |
| `jvm.memory.committed`, `jvm.memory.used`, `jvm.memory.max` | gauge | `area`, `id` | JVM memory, in bytes |
| `jvm.threads.live`, `jvm.threads.peak` | gauge | — | JVM thread counts |

A message reaches the dead-letter queue when SQS redrives it after its last receive, which the gateway never sees, so `sqs.queue.depth{queue=notification-dlq}` above 0 is the signal that a message ran out of retries.

The counters are also readable per instance at `/metrics/<name>` as running totals since the task started, for example `/metrics/notification.message?tag=outcome:forwarded&tag=schema.version:0.1.0`. The performance tests read that one to count what the gateway forwarded.

## Following a notification across services

`trace.id` comes from the `x-cdp-request-id` header, so it is on every line of a `POST /events` request. A notification that arrives over SQS carries no trace id: the backend publishes it from its outbox, outside any request. Follow it by its `aggregateId` instead. It is the SQS message group id, logged on the line the listener writes when it receives the message, sent as the Service Bus `sessionId` and logged as `sessionId` on every send line, and logged on the retry and discard lines.

Each send's Service Bus `messageId` is the event id for 0.1.0, and the event id with `-v2`, `-v3` or `-v4` appended for 0.2.0, 0.3.0 and 0.4.0. An event with no event id falls back to the SQS deduplication id, and then to a random UUID.
