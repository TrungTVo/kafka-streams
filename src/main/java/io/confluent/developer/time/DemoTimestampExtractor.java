package io.confluent.developer.time;

import io.confluent.developer.StreamsUtils;
import io.confluent.developer.avro.ElectronicOrder;
import io.confluent.kafka.streams.serdes.avro.SpecificAvroSerde;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.common.utils.Bytes;
import org.apache.kafka.streams.KafkaStreams;
import org.apache.kafka.streams.KeyValue;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.StreamsConfig;
import org.apache.kafka.streams.kstream.Consumed;
import org.apache.kafka.streams.kstream.Grouped;
import org.apache.kafka.streams.kstream.KStream;
import org.apache.kafka.streams.kstream.Materialized;
import org.apache.kafka.streams.kstream.Produced;
// import org.apache.kafka.streams.kstream.Suppressed;
import org.apache.kafka.streams.kstream.TimeWindows;
import org.apache.kafka.streams.state.WindowStore;

import java.io.IOException;
import java.time.Duration;
import java.time.ZoneId;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.CountDownLatch;

public class DemoTimestampExtractor {

    public static void main(String[] args) throws IOException {
        String tmpdir = System.getProperty("java.io.tmpdir");
        System.out.println("[State.dir] Directory location for state store: " + tmpdir);

        final Properties streamsProps = StreamsUtils.loadProperties();
        streamsProps.put(StreamsConfig.APPLICATION_ID_CONFIG, "extractor-windowed-streams");
        // For Debugging - Disable buffering/caching (emit every update immediately)
        streamsProps.put(StreamsConfig.STATESTORE_CACHE_MAX_BYTES_CONFIG, 0);

        StreamsBuilder builder = new StreamsBuilder();
        final String inputTopic = streamsProps.getProperty("extractor.input.topic");
        final String outputTopic = streamsProps.getProperty("extractor.output.topic");
        final Map<String, Object> configMap = StreamsUtils.propertiesToMap(streamsProps);

        final SpecificAvroSerde<ElectronicOrder> electronicSerde =
                StreamsUtils.getSpecificAvroSerde(configMap);

        final KStream<String, ElectronicOrder> electronicStream =
                builder.stream(
                            inputTopic,
                            Consumed.with(Serdes.String(), electronicSerde).withTimestampExtractor(new OrderTimestampExtractor())
                        )
                        .peek((key, value) -> System.out.println("Incoming record - key: " + key + ", value: " + value));

        // Now take the electronicStream object, group by key and perform an aggregation based on time windows
        // Don't forget to convert the KTable returned by the aggregate call back to a KStream using the toStream() method
        electronicStream.groupByKey(Grouped.with(Serdes.String(), electronicSerde))
                        .windowedBy(TimeWindows.ofSizeWithNoGrace(Duration.ofHours(1)))
                        .aggregate(
                            () -> "0.0",
                            (key, electronicOrder, total) -> {
                                double currentTotal = Double.parseDouble(total);
                                double newTotal = currentTotal + electronicOrder.getPrice();
                                return Double.toString(newTotal);
                            },
                            Materialized.<String, String, WindowStore<Bytes, byte[]>>as("electronic-wd-store")
                                .withKeySerde(Serdes.String())
                                .withValueSerde(Serdes.String())
                        )
                        // .suppress(Suppressed.untilWindowCloses(Suppressed.BufferConfig.unbounded()))
                        .toStream()
                        .peek((wk, value) -> System.out.println("Aggregated record - key: " + wk.key() 
                                        + ", windowStart: " + wk.window().startTime().atZone(ZoneId.systemDefault()).toLocalDateTime()
                                        + ", windowEnd: " + wk.window().endTime().atZone(ZoneId.systemDefault()).toLocalDateTime()
                                        + ", total price: " + value))
                        .map((wk, value) -> KeyValue.pair(wk.key(),value))
                        .to(outputTopic, Produced.with(Serdes.String(), Serdes.String()));
                        
        try (KafkaStreams kafkaStreams = new KafkaStreams(builder.build(), streamsProps)) {
            final CountDownLatch shutdownLatch = new CountDownLatch(1);

            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                System.out.println("Shutting down stream application...");
                kafkaStreams.close(Duration.ofSeconds(2));
                kafkaStreams.cleanUp();             // clean up local state store. If skip, then app restarts from previous aggregated state
                shutdownLatch.countDown();
            }));
            TopicLoader.runProducer();           // produce sample records
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
