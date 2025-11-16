# PRE-SETUP with sample topic and messages
## Create sample topic
```
kafka-topics \
  --bootstrap-server broker:9092 \
  --create \
  --topic author \
  --partitions 4 \
  --replication-factor 1
```

## List all schemas
```
curl http://localhost:8081/subjects | jq
```

## Delete topic's key or value schema (if want to start fresh)
```
curl -X DELETE http://localhost:8081/subjects/author-key
curl -X DELETE http://localhost:8081/subjects/author-value
```

## Check all schema versions
```
curl http://localhost:8081/subjects/author-key/versions     // [1,2,3,...]
curl http://localhost:8081/subjects/author-value/versions   // [1,2,3,...]

// check schema ID
curl http://localhost:8081/subjects/author-key/versions/1
curl http://localhost:8081/subjects/author-value/versions/1
```

## Register AVRO schema to topic
Key:
```
curl -X POST \
  -H "Content-Type: application/vnd.schemaregistry.v1+json" \
  --data "$(printf '{"schema": %s}' "$(jq -Rs . < src/main/avro/author_key.avsc)")" \
  http://localhost:8081/subjects/author-key/versions
```

Value:
```
curl -X POST \
  -H "Content-Type: application/vnd.schemaregistry.v1+json" \
  --data "$(printf '{"schema": %s}' "$(jq -Rs . < src/main/avro/author_value.avsc)")" \
  http://localhost:8081/subjects/author-value/versions
```

## Produce sample messages
Inside `schema-registry` container:
```
kafka-avro-console-producer --bootstrap-server broker:29092 \
    --topic author \
    --property parse.key=true \
    --property key.separator=: \
    --property key.schema.id=<KEY_SCHEMA_ID> \
    --property value.schema.id=<VALUE_SCHEMA_ID>
```
Key and value is separated by `:`. Sample message:
```
"programming":{"name":"trung vo","age":29,"email":"trung.vo@gmail.com"}
"programming":{"name":"john doe","age":30,"email":"john.doe@gmail.com"}
"doctor":{"name":"phuong chu","age":29,"email":"phuong.chu@gmail.com"}
"athlete":{"name":"roger federer","age":49,"email":"roger.fed@gmail.com"}
"car":{"name":"mike khang","age":49,"email":"mike.khang@gmail.com"}
"car":{"name":"Dung Nguyen","age":25,"email":"dung.nguyen@gmail.com"}
"programming":{"name":"ben quoc","age":20,"email":"ben.quoc@gmail.com"}
"athlete":{"name":"trung vo","age":29,"email":"trung.vo@gmail.com"}
"car":{"name":"phuong chu","age":29,"email":"phuong.chu@gmail.com"}
"programming":{"name":"trung vo","age":29,"email":"trung.vo@gmail.com"}
```