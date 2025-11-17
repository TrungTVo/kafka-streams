package io.confluent.developer.commonSample;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.confluent.developer.avro.AuthorValue;

import java.io.InputStream;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class AuthorDataLoader {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    public static List<AuthorMessage> loadAuthorsData() {
        System.out.println("Loading author data from authors_data.json...");
        try (InputStream in = AuthorDataLoader.class.getResourceAsStream("/authors_data.json")) {
            if (in == null) {
                System.err.println("Warning: authors_data.json not found in resources");
                return List.of();
            }

            List<Map<String, Object>> items = MAPPER.readValue(in, new TypeReference<>() {});
            List<AuthorMessage> out = new ArrayList<>();

            Instant instant = Instant.now();
            for (Map<String, Object> item : items) {
                String key = (String) item.get("key");
                @SuppressWarnings("unchecked")
                Map<String, Object> v = (Map<String, Object>) item.get("value");
                String name = (String) v.get("name");
                int age = v.get("age") instanceof Number ? ((Number) v.get("age")).intValue() : Integer.parseInt(v.get("age").toString());
                String email = (String) v.get("email");
                String timestamp = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneOffset.UTC).format(instant);

                AuthorValue authorValue = new AuthorValue(name, age, email, timestamp);
                out.add(new AuthorMessage(key, authorValue));
                instant = instant.plusSeconds(10L);
            }

            System.out.printf("Successfully loaded %d author records%n", out.size());
            return out;
        } catch (Exception e) {
            System.err.println("Failed to load authors_data.json: " + e.getMessage());
            e.printStackTrace();
            throw new RuntimeException("Failed to load authors_data.json", e);
        }
    }
}
