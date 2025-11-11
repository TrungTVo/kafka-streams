package io.confluent.developer.aggregate;

import io.confluent.developer.StreamsUtils;
import io.confluent.developer.avro.ElectronicOrder;
import io.confluent.kafka.streams.serdes.avro.SpecificAvroSerde;
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
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.CountDownLatch;

public class StreamsAggregate {

    public static void main(String[] args) throws IOException {
        String tmpdir = System.getProperty("java.io.tmpdir");
        System.out.println("[State.dir] Directory location for state store: " + tmpdir);

        final Properties streamsProps = StreamsUtils.loadProperties();
        streamsProps.put(StreamsConfig.APPLICATION_ID_CONFIG, "aggregate-streams");

        StreamsBuilder builder = new StreamsBuilder();
        final String inputTopic = streamsProps.getProperty("aggregate.input.topic");
        final String outputTopic = streamsProps.getProperty("aggregate.output.topic");
        final Map<String, Object> configMap = StreamsUtils.propertiesToMap(streamsProps);

        final SpecificAvroSerde<ElectronicOrder> electronicSerde =
                StreamsUtils.getSpecificAvroSerde(configMap);

        final KStream<String, ElectronicOrder> electronicStream =
                builder.stream(inputTopic, Consumed.with(Serdes.String(), electronicSerde))
                        .peek((key, value) -> System.out.println("Incoming record - key: " + key + ", value: " + value));

        // Now take the electronicStream object, group by key and perform an aggregation
        // Don't forget to convert the KTable returned by the aggregate call back to a KStream using the toStream() method
        /**
         * "HDTV-2333" -> 9000.21
         * "ABCD-1111" -> 5833.98
         */
        electronicStream.groupByKey(Grouped.with(Serdes.String(), electronicSerde))
                        .aggregate(
                            () -> "0.0",
                            (key, electronicOrder, total) -> {
                                double currentTotal = Double.parseDouble(total);
                                double newTotal = currentTotal + electronicOrder.getPrice();
                                return Double.toString(newTotal);
                            },
                            Materialized.<String, String, KeyValueStore<Bytes, byte[]>>as("electronic-store")
                                .withKeySerde(Serdes.String())
                                .withValueSerde(Serdes.String())
                        )
                        .toStream()
                        .peek((key, value) -> System.out.println("Aggregated record - key: " + key + ", value(price): " + value))
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
