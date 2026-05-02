package io.confluent.developer.errors;

import io.confluent.developer.StreamsUtils;
import org.apache.kafka.clients.admin.Admin;
import org.apache.kafka.clients.admin.CreateTopicsResult;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.producer.Callback;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;

import java.io.IOException;
import java.util.List;
import java.util.Properties;
import java.util.Set;
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
            final String inputTopic = properties.getProperty("error.input.topic");
            final String outputTopic = properties.getProperty("error.output.topic");
            var topics = List.of(StreamsUtils.createTopic(inputTopic), StreamsUtils.createTopic(outputTopic));
            try {
                final Set<String> existingTopics = adminClient.listTopics().names().get();
                final List<NewTopic> topicsToCreate = StreamsUtils.topicsToCreate(topics, existingTopics);
                if (!topicsToCreate.isEmpty()) {
                    CreateTopicsResult createTopicsResult = adminClient.createTopics(topicsToCreate);
                    createTopicsResult.all().get(); // Wait for topics to be created
                    System.out.println("Topics created successfully");
                } else {
                    System.out.println("Topics already exist");
                }

                Callback callback = StreamsUtils.callback();

                var rawRecords = List.of("orderNumber-1001",
                        "orderNumber-5000",
                        "orderNumber-999",
                        "orderNumber-3330",
                        "bogus-1",
                        "bogus-2",
                        "orderNumber-8400");
                var producerRecords = rawRecords.stream()
                                                .map(r -> {
                                                    // different keys might go to same partition, here assign manually to different partitions
                                                    if (r.startsWith("order")) {
                                                        return new ProducerRecord<>(inputTopic, 0, "order", r);
                                                    } else {
                                                        return new ProducerRecord<>(inputTopic, 1, "bogus", r);
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