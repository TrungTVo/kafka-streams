package io.confluent.developer.reduce;

import io.confluent.developer.StreamsUtils;
import org.apache.kafka.clients.admin.Admin;
import org.apache.kafka.clients.admin.CreateTopicsResult;
import org.apache.kafka.clients.producer.Callback;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;

import java.io.IOException;
import java.util.List;
import java.util.Properties;
import java.util.stream.Collectors;

public class TopicLoader {

    public static void main(String[] args) throws IOException {
        runProducer();
    }

    public static void runProducer() throws IOException {
        Properties properties = StreamsUtils.loadProperties();
        properties.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        properties.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);

        try (Admin adminClient = Admin.create(properties);
                Producer<String, String> producer = new KafkaProducer<>(properties)) {
            final String inputTopic = properties.getProperty("reduce.input.topic");
            final String outputTopic = properties.getProperty("reduce.output.topic");
            var topics = List.of(StreamsUtils.createTopic(inputTopic), StreamsUtils.createTopic(outputTopic));
            try {
                CreateTopicsResult createTopicsResult = adminClient.createTopics(topics);
                createTopicsResult.all().get(); // Wait for topics to be created
                System.out.println("Topics created successfully");

                Callback callback = (metadata, exception) -> {
                    if (exception != null) {
                        System.out.printf("Producing records encountered error %s %n", exception);
                    } else {
                        System.out.printf("Record produced - offset - %d timestamp - %d %n", metadata.offset(),
                                metadata.timestamp());
                    }

                };

                var rawRecords = List.of("123", "456", "789", "222", "555", "666");
                var producerRecords = rawRecords.stream()
                                                .map(num -> {
                                                    if (Integer.parseInt(num) % 2 == 0) {
                                                        return new ProducerRecord<>(inputTopic, 0, "even", num);
                                                    } else {
                                                        return new ProducerRecord<>(inputTopic, 1, "odd", num);
                                                    }
                                                })
                                                .collect(Collectors.toList());
                producerRecords.forEach((pr -> producer.send(pr, callback)));
            } catch (Exception e) {
                System.err.printf("Error creating topics or producing messages: %s%n", e.getMessage());
                throw new RuntimeException(e);
            }

        }
    }
}