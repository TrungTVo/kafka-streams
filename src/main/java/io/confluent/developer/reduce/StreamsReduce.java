package io.confluent.developer.reduce;

import io.confluent.developer.StreamsUtils;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.common.utils.Bytes;
import org.apache.kafka.streams.KafkaStreams;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.StreamsConfig;
import org.apache.kafka.streams.kstream.Consumed;
import org.apache.kafka.streams.kstream.Grouped;
import org.apache.kafka.streams.kstream.KStream;
import org.apache.kafka.streams.kstream.Materialized;
import org.apache.kafka.streams.kstream.Produced;
import org.apache.kafka.streams.state.KeyValueStore;

import java.io.IOException;
import java.time.Duration;
import java.util.Properties;
import java.util.concurrent.CountDownLatch;

public class StreamsReduce {

    public static void main(String[] args) throws IOException {
        String tmpdir = System.getProperty("java.io.tmpdir");
        System.out.println("[State.dir] Directory location for state store: " + tmpdir);

        final Properties streamsProps = StreamsUtils.loadProperties();
        streamsProps.put(StreamsConfig.APPLICATION_ID_CONFIG, "reduce-streams");

        StreamsBuilder builder = new StreamsBuilder();
        final String inputTopic = streamsProps.getProperty("reduce.input.topic");
        final String outputTopic = streamsProps.getProperty("reduce.output.topic");

        final KStream<String, String> recordStream =
                builder.stream(inputTopic, Consumed.with(Serdes.String(), Serdes.String()))
                        .peek((key, value) -> System.out.println("Incoming record - key: " + key + ", value: " + value));

        // Now take the recordStream, group by key and perform a reduction
        // Don't forget to convert the KTable returned by the aggregate call back to a KStream using the toStream() method
        /**
         * "even" -> "666"
         * "odd" -> "789"
         */
        recordStream.groupByKey(Grouped.with(Serdes.String(), Serdes.String()))
                        .reduce(
                            (max, value) -> Integer.toString(Math.max(Integer.parseInt(max), Integer.parseInt(value))),
                            Materialized.<String, String, KeyValueStore<Bytes, byte[]>>as("reduce-store")
                                .withKeySerde(Serdes.String())
                                .withValueSerde(Serdes.String())
                        )
                        .toStream()
                        .peek((key, value) -> System.out.println("Reduced record - key: " + key + ", max value: " + value))
                        .to(outputTopic, Produced.with(Serdes.String(), Serdes.String()));
                        
        try (KafkaStreams kafkaStreams = new KafkaStreams(builder.build(), streamsProps)) {
            final CountDownLatch shutdownLatch = new CountDownLatch(1);

            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                System.out.println("Shutting down stream application...");
                kafkaStreams.close(Duration.ofSeconds(2));
                kafkaStreams.cleanUp();             // clean up local state store. If skip, then app restarts from previous aggregated state
                shutdownLatch.countDown();
            }));
            TopicLoader.runProducer();
            try {
                kafkaStreams.cleanUp();
                kafkaStreams.start();
                shutdownLatch.await();
            } catch (Throwable e) {
                System.exit(1);
            }
        }
        System.exit(0);
    }
}
