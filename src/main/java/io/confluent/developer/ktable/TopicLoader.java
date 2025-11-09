package io.confluent.developer.ktable;

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
            final String inputTopic = properties.getProperty("ktable.input.topic");
            final String outputTopic = properties.getProperty("ktable.output.topic");
            var topics = List.of(StreamsUtils.createTopic(inputTopic), StreamsUtils.createTopic(outputTopic));
            try {
                CreateTopicsResult createTopicsResult = adminClient.createTopics(topics);
                createTopicsResult.all().get(); // Wait for topics to be created
                System.out.println("Topics created successfully");

                Callback callback = (metadata, ex) -> {
                    if (ex != null) {
                        System.out.printf("Producing records encountered error %s %n", ex);
                    } else {
                        System.out.printf("Record produced - offset - %d timestamp - %d %n", metadata.offset(),
                                metadata.timestamp());
                    }
                };

                var rawRecords = List.of("orderNumber-1001",
                        "orderNumber-5000",
                        "orderNumber-999",
                        "orderNumber-3330",
                        "bogus-1",
                        "bogus-2",
                        "orderNumber-8400",
                        "bogus-3",
                        "transaction-2000",
                        "transaction-3000");
                var producerRecords = rawRecords.stream()
                        .map(r -> {
                            // different keys might go to same partition
                            if (r.startsWith("orderNumber")) {
                                return new ProducerRecord<String, String>(inputTopic, "orderNumberKey", r);
                            } else if (r.startsWith("bogus")) {
                                return new ProducerRecord<String, String>(inputTopic, "bogusKey", r);
                            } else {
                                return new ProducerRecord<String, String>(inputTopic, "transactionKey", r);
                            }
                        })
                        .collect(Collectors.toList());
                producerRecords.forEach(pr -> producer.send(pr, callback));
                
                // Ensure all messages are sent before closing
                producer.flush();
            } catch (Exception e) {
                System.err.printf("Error creating topics or producing messages: %s%n", e.getMessage());
                throw new RuntimeException(e);
            }
        }
    }
}
