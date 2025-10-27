# Kafka Streams

## Setup sample Kafka cluster and broker
All of Confluent Kafka config is inside `cp-all-in-one` folder. Run:
```
docker compose -f ./cp-all-in-one/docker-compose.yml up -d
```

Run sample Kafka stream:
```
./gradlew runStreams -Pargs=<sample>
```
For example, run `basic` Kafka stream app:
```
./gradlew runStreams -Pargs=basic
```