# KafkaSyncCrud
## Task
- Реализовать 2 сервиса, один api, другой core
- Общение между сервисами реализовать через rabbit/kafka (либо через REST) на усмотрение
- core сервис должен работать с БД ElasticSearch
- api должен принимать CRUD запросы по Пользователям
- Запросы синхронные

## Running
- Run `com.example.api.BootApiServer`
- Run `com.example.core.BootCoreServer`

Note: The system may need some time to bootstrap such that initial requests may fail with status `500`.

## Configuration requirements
- Add `elasticsearch-creds.conf` to `/src/main/resources/secret` directory

```yaml
elasticsearch {
  hosts = ["{ES_HOST}:{ES_PORT}"]
  https = true
  username = "{ES_USERNAME}"
  password = "{ES_PASSWORD}"
}
```

- Kafka broker must be available on `localhost:9092`

- HTTP API will listen on port `8080`

Note: Kafka configuration is hardcoded in boot files, 
so don't forget to change them in both files.

## Architecture
![Architecture][arch_image]

## HTTP API
### Data format
Some endpoints require JSON formatted user data.
Currently, `id` field is created from `name` in `POST` and ignored in `PUT`.
```
{"name": "John Doe"}
```

### Endpoints
- Create user | **JSON body is required**

  `POST http://localhost:8080/users`

- Read users by query
  
  `GET http://localhost:8080/users[?q=query]`

- Read user by id

  `GET http://localhost:8080/users/{id}`

- Update user by id | **JSON body is required**

  `PUT http://localhost:8080/users/{id}`

- Delete user by id

  `DELETE http://localhost:8080/users/{id}`

[arch_image]: assets/arch.svg