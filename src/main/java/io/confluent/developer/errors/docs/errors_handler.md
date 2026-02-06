# Errors Handler

Custom Errors Handlers are placed in `handlers` folder.

For `StreamsUncaughtExceptionHandler`, note the difference between `StreamThreadExceptionResponse.SHUTDOWN_CLIENT` and `StreamThreadExceptionResponse.SHUTDOWN_APPLICATION`:

`SHUTDOWN_CLIENT`
---
Meaning: Shut down only this KafkaStreams client (i.e., this JVM/process / container / pod). Other running instances with the same application.id keep running.

Practical effect (in a scaled app):

* This instance transitions to ERROR/shutdown and closes its threads/resources.
* The remaining instances in the same `application.id` consumer group will rebalance and take over the partitions/tasks that were on the dead instance.
* You lose capacity, but the application (as a whole) can keep processing.

Use when: the fault is “local” (e.g., corrupted local state dir, disk full on one node, bad env/config on one pod) and you want the rest of the fleet to continue.

`SHUTDOWN_APPLICATION`
---
Meaning: Try to shut down the whole Kafka Streams application, i.e., all instances running with the same application.id.

Practical effect (in a scaled app):

* This instance will shut down, and Streams will attempt to signal other instances of the same app to also shut down.
* There’s no absolute guarantee every remote instance receives/acts on the shutdown signal (network partitions, process already unhealthy, etc.).
* Net intent: stop the entire app everywhere, not just degrade capacity.

Use when: the fault is “global/critical” (e.g., a bug in user code that will crash any thread on any instance, a bad deployment, schema incompatibility that breaks processing) and you want a full stop.

## Run 2 Stream instances of the same app (same `application.id`)

In this example, we run 2 instances, each configured with default of 2 stream threads (`NUM_STREAM_THREADS_CONFIG`), or 4 stream threads in total.

Instance 1: `./gradlew runStreams -Pargs=errors -Pinstance=1`

Instance 2: `./gradlew runStreams -Pargs=errors -Pinstance=2`

In this example, we only trigger uncaught error handler for `instance 1`.

Although two instances use same `application.id`, using `SHUTDOWN_CLIENT` will shut down only all stream threads of `instance 1` (the one triggered error). `Instance 2` threads will keep running. However, if using `SHUTDOWN_APPLICATION`, it will stop both instances and all their stream threads.

### Output

Instance 1
```
Running instance: 1
>>> State changed from CREATED to REBALANCING
    >>> StreamThread: streams-error-handling-2c9fe46d-16f0-42c4-9f39-9da1d289f0aa-StreamThread-1, state: CREATED
    >>> StreamThread: streams-error-handling-2c9fe46d-16f0-42c4-9f39-9da1d289f0aa-StreamThread-2, state: CREATED
>>> State changed from REBALANCING to RUNNING
    >>> StreamThread: streams-error-handling-2c9fe46d-16f0-42c4-9f39-9da1d289f0aa-StreamThread-2, state: RUNNING
    >>> StreamThread: streams-error-handling-2c9fe46d-16f0-42c4-9f39-9da1d289f0aa-StreamThread-1, state: RUNNING
Incoming record - key order value orderNumber-1001
Incoming record - key bogus value bogus-1
  --> THROWING ERROR NOW...
Incoming record - key bogus value bogus-2
Uncaught exception:     Simulated processing error
>>> State changed from RUNNING to PENDING_ERROR
    >>> StreamThread: streams-error-handling-2c9fe46d-16f0-42c4-9f39-9da1d289f0aa-StreamThread-2, state: RUNNING
    >>> StreamThread: streams-error-handling-2c9fe46d-16f0-42c4-9f39-9da1d289f0aa-StreamThread-1, state: RUNNING
>>> State changed from PENDING_ERROR to ERROR
```

Instance 2
```
Running instance: 2
>>> State changed from CREATED to REBALANCING
    >>> StreamThread: streams-error-handling-25885481-8b1f-4b09-acf2-4b54c3e5322e-StreamThread-2, state: CREATED
    >>> StreamThread: streams-error-handling-25885481-8b1f-4b09-acf2-4b54c3e5322e-StreamThread-1, state: CREATED
>>> State changed from REBALANCING to RUNNING
    >>> StreamThread: streams-error-handling-25885481-8b1f-4b09-acf2-4b54c3e5322e-StreamThread-1, state: RUNNING
    >>> StreamThread: streams-error-handling-25885481-8b1f-4b09-acf2-4b54c3e5322e-StreamThread-2, state: RUNNING
Incoming record - key order value orderNumber-1001
Outgoing record - key order value 1001
Incoming record - key order value orderNumber-5000
Outgoing record - key order value 5000
Incoming record - key order value orderNumber-999
Incoming record - key order value orderNumber-3330
Outgoing record - key order value 3330
Incoming record - key order value orderNumber-8400
Outgoing record - key order value 8400
```
