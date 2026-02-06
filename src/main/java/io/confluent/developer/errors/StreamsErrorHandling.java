package io.confluent.developer.errors;

import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.KafkaStreams;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.StreamsConfig;
import org.apache.kafka.streams.ThreadMetadata;
import org.apache.kafka.streams.kstream.Consumed;
import org.apache.kafka.streams.kstream.KStream;
import org.apache.kafka.streams.kstream.Produced;

import io.confluent.developer.errors.handlers.CustomUncaughtExceptionHandler;
import io.confluent.developer.errors.handlers.DeserializationErrorHandler;

import java.io.FileInputStream;
import java.io.IOException;
import java.time.Duration;
import java.util.Properties;
import java.util.concurrent.CountDownLatch;

public class StreamsErrorHandling {
    // This is for learning purposes only!
    static boolean throwErrorNow = true;
    static String instanceId;

    public static void main(String[] args) throws IOException {
        // Read instance ID from system property (default to 1)
        instanceId = System.getProperty("instance", "1");
        System.out.println("Running instance: " + instanceId);
        
        final Properties streamsProps = new Properties();
        try (FileInputStream fis = new FileInputStream("src/main/resources/streams.properties")) {
            streamsProps.load(fis);
        }
        streamsProps.put(StreamsConfig.NUM_STREAM_THREADS_CONFIG, 2);
        // ALL instances share the SAME application ID - they form a consumer group
        streamsProps.put(StreamsConfig.APPLICATION_ID_CONFIG, "streams-error-handling");
        streamsProps.put(StreamsConfig.DEFAULT_DESERIALIZATION_EXCEPTION_HANDLER_CLASS_CONFIG, DeserializationErrorHandler.class);

        StreamsBuilder builder = new StreamsBuilder();
        final String inputTopic = streamsProps.getProperty("error.input.topic");
        final String outputTopic = streamsProps.getProperty("error.output.topic");

        final String orderNumberStart = "orderNumber-";

        KStream<String, String> str = builder.stream(inputTopic,
                Consumed.with(Serdes.String(), Serdes.String()));

        str.peek((key, value) -> System.out.println("Incoming record - key " + key + " value " + value))
                .filter((key, value) -> value.contains(orderNumberStart))
                .mapValues(value -> {
                    if (throwErrorNow && instanceId.equals("1")) {
                        throwErrorNow = false;
                        System.out.println("  --> THROWING ERROR NOW...");
                        throw new IllegalStateException("    Simulated processing error");
                    }
                    return value.substring(value.indexOf("-") + 1);
                })
                .filter((key, value) -> Long.parseLong(value) > 1000)
                .peek((key, value) -> System.out.println("Outgoing record - key " + key + " value " + value))
                .to(outputTopic, Produced.with(Serdes.String(), Serdes.String()));


        try (
            KafkaStreams kafkaStreams = new KafkaStreams(builder.build(), streamsProps)
        ) {
            kafkaStreams.setStateListener(( newState, oldState ) -> {
                System.out.println(">>> State changed from " + oldState + " to " + newState);
                kafkaStreams.metadataForLocalThreads().forEach((ThreadMetadata data) -> {
                    System.out.println("    >>> StreamThread: " + data.threadName() + ", state: " + data.threadState());
                });
            });
            
            kafkaStreams.setUncaughtExceptionHandler(new CustomUncaughtExceptionHandler());
            final CountDownLatch shutdownLatch = new CountDownLatch(1);

            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                System.out.println("\n[SHUTDOWN] Closing...");
                kafkaStreams.close(Duration.ofSeconds(2));
                shutdownLatch.countDown();
            }));
            // TopicLoader.runProducer();
            try {
                kafkaStreams.start();
                shutdownLatch.await();
            } catch (Throwable e) {
                System.exit(1);
            }
        }
        System.exit(0);
    }
}
