# KafkaSyncCrud
## Task
- Реализовать 2 сервиса, один api, другой core
- Общение между сервисами реализовать через rabbit/kafka (либо через REST) на усмотрение
- core сервис должен работать с БД ElasticSearch
- api должен принимать CRUD запросы по Пользователям
- Запросы синхронные

## Architecture
![Architecture][arch_image]

## HTTP API
### Data format
Some endpoints require JSON formatted user data.
Currently, `Id` field is created from `name` in `POST` and ignored in `PUT`.
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