package io.confluent.developer.commonSample;

import io.confluent.developer.StreamsUtils;
import io.confluent.developer.avro.AuthorValue;
import io.confluent.kafka.serializers.KafkaAvroSerializer;
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

public class TopicLoader {

    public static void main(String[] args) throws IOException {
        runProducer();
    }

    public static void runProducer() throws IOException {
        System.out.println("TopicLoader starting...");
        Properties properties = StreamsUtils.loadProperties();
        properties.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        properties.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, KafkaAvroSerializer.class);

        // Load author data before proceeding
        List<AuthorMessage> authors = AuthorDataLoader.loadAuthorsData();
        
        if (authors.isEmpty()) {
            System.err.println("ERROR: No author data loaded. Cannot proceed with producing messages.");
            return;
        }

        try(Admin adminClient = Admin.create(properties);
            Producer<String, AuthorValue> producer = new KafkaProducer<>(properties)) {
            final String authorTopic = "author";
            var topics = List.of(StreamsUtils.createTopic(authorTopic));
            try {
                CreateTopicsResult createTopicsResult = adminClient.createTopics(topics);
                createTopicsResult.all().get(); // Wait for topics to be created
                System.out.println("Topics created successfully");

                System.out.printf("Producing %d author messages to topic %s%n", authors.size(), authorTopic);

                Callback callback = StreamsUtils.callback();

                authors.forEach((AuthorMessage authorMessage) -> {
                    ProducerRecord<String, AuthorValue> producerRecord = new ProducerRecord<>(authorTopic, authorMessage.getKey(), authorMessage.getValue());
                    producer.send(producerRecord, callback);
                });
            } catch (Exception e) {
                System.err.printf("Error creating topics or producing messages: %s%n", e.getMessage());
                throw new RuntimeException(e);
            }
        }
    }
}

