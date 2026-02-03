package io.confluent.developer.processor;

import java.util.Collections;
import java.util.Set;

import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.processor.api.Processor;
import org.apache.kafka.streams.processor.api.ProcessorSupplier;
import org.apache.kafka.streams.state.Stores;
import org.apache.kafka.streams.state.KeyValueStore;
import org.apache.kafka.streams.state.StoreBuilder;

import io.confluent.developer.avro.ElectronicOrder;

public class TotalPriceOrderProcessorSupplier implements ProcessorSupplier<String, ElectronicOrder, String, String> {
    private final String storeName;

    public TotalPriceOrderProcessorSupplier(String storeName) {
        this.storeName = storeName;
    }

    @Override
    public Processor<String, ElectronicOrder, String, String> get() {
        return new TotalPriceOrderProcessor(storeName);
    }

    @Override
    public Set<StoreBuilder<?>> stores() {
        StoreBuilder<KeyValueStore<String, String>> totalPriceStoreBuilder = Stores.keyValueStoreBuilder(
            Stores.persistentKeyValueStore(this.storeName),
            Serdes.String(),
            Serdes.String());
        return Collections.singleton(totalPriceStoreBuilder);
    }

}
