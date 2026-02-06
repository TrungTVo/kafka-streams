package io.confluent.developer.errors.handlers;

import java.util.Map;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.streams.errors.DeserializationExceptionHandler;
import org.apache.kafka.streams.processor.ProcessorContext;

public class DeserializationErrorHandler implements DeserializationExceptionHandler {

    @Override
    public void configure(Map<String, ?> configs) {
    }

    @Override
    public DeserializationHandlerResponse handle(
        ProcessorContext context, 
        ConsumerRecord<byte[], byte[]> record,
        Exception exception) 
    {
        System.out.println("Deserialization error occurred for record: " + record + ", with exception: " + exception.getMessage());
        return DeserializationHandlerResponse.FAIL;
    }

}
