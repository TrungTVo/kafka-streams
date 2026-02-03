package io.confluent.developer.processor;

import java.time.Duration;

import org.apache.kafka.streams.KeyValue;
import org.apache.kafka.streams.processor.PunctuationType;
import org.apache.kafka.streams.processor.api.Processor;
import org.apache.kafka.streams.processor.api.ProcessorContext;
import org.apache.kafka.streams.processor.api.Record;
import org.apache.kafka.streams.state.KeyValueStore;

import io.confluent.developer.avro.ElectronicOrder;

public class TotalPriceOrderProcessor implements Processor<String, ElectronicOrder, String, String> {
    private final String storeName;
    private ProcessorContext<String, String> context;
    private KeyValueStore<String, String> store;

    public TotalPriceOrderProcessor(String storeName) {
        this.storeName = storeName;
    }

    @Override
    public void init(final ProcessorContext<String, String> context) {
        this.context = context;
        this.store = context.getStateStore(storeName);
        this.context.schedule(Duration.ofSeconds(75), PunctuationType.STREAM_TIME, this::forwardAll);
    }

    private void forwardAll(final long timestamp) {
        this.store.all().forEachRemaining((KeyValue<String, String> entry) -> {
            this.context.forward(new Record<>(entry.key, entry.value, timestamp));
            System.out.println("Punctuation forwarded record - key: " + entry.key + ", value: " + entry.value);
        });
    }
    
    @Override
    public void process(Record<String, ElectronicOrder> record) {
        String key = record.key();
        ElectronicOrder order = record.value();
        String currentTotal = store.get(key);
        if (currentTotal == null) {
            currentTotal = "0.0";
        }
        String newTotal = String.valueOf(Double.parseDouble(currentTotal) + order.getPrice());
        store.put(key, newTotal);
        System.out.println("Processed record - key: " + key + ", order price: " + order.getPrice() + ", updated total price: " + newTotal);
    }
    
}
