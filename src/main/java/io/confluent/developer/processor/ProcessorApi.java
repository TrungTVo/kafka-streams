package io.confluent.developer.processor;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.CountDownLatch;

import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.KafkaStreams;
import org.apache.kafka.streams.StreamsConfig;
import org.apache.kafka.streams.Topology;

import io.confluent.developer.StreamsUtils;
import io.confluent.developer.avro.ElectronicOrder;
import io.confluent.kafka.streams.serdes.avro.SpecificAvroSerde;

public class ProcessorApi {
    public static void main(String[] args) throws IOException {
        String tmpdir = System.getProperty("java.io.tmpdir");
        System.out.println("[State.dir] Directory location for state store: " + tmpdir);

        final Properties streamsProps = StreamsUtils.loadProperties();
        streamsProps.put(StreamsConfig.APPLICATION_ID_CONFIG, "processor-api-application");

        final String inputTopic = streamsProps.getProperty("processor.input.topic");
        final String outputTopic = streamsProps.getProperty("processor.output.topic");
        final Map<String, Object> configMap = StreamsUtils.propertiesToMap(streamsProps);

        final SpecificAvroSerde<ElectronicOrder> electronicSerde = StreamsUtils.getSpecificAvroSerde(configMap);
        final Topology topology = new Topology();
        topology.addSource(
                "source-node",
                Serdes.String().deserializer(),
                electronicSerde.deserializer(),
                inputTopic);

        topology.addProcessor(
                "aggregate-price",
                new TotalPriceOrderProcessorSupplier("total-price-store"),
                "source-node");

        topology.addSink(
                "sink-node",
                outputTopic,
                Serdes.String().serializer(),
                Serdes.String().serializer(),
                "aggregate-price");

        try (KafkaStreams kafkaStreams = new KafkaStreams(topology, streamsProps)) {
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
