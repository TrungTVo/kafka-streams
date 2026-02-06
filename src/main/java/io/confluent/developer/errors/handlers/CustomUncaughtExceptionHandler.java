package io.confluent.developer.errors.handlers;

import org.apache.kafka.streams.errors.StreamsException;
import org.apache.kafka.streams.errors.StreamsUncaughtExceptionHandler;

public class CustomUncaughtExceptionHandler implements StreamsUncaughtExceptionHandler {

    @Override
    public StreamThreadExceptionResponse handle(Throwable exception) {
        System.err.println("Uncaught exception: " + exception.getCause().getMessage());
        if (exception instanceof StreamsException) {
            Throwable originalException = exception.getCause();
            if (originalException.getMessage().equals("Retryable transient error")) {
                return StreamThreadExceptionResponse.REPLACE_THREAD;
            }
        }
        return StreamThreadExceptionResponse.SHUTDOWN_CLIENT;
    }

}
