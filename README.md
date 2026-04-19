# Camel Routing

Приложение для надежной маршрутизации сообщений из AWS SQS в AWS EventBridge с поддержкой ретраев, Dead Letter Queue и автоматического восстановления.

## Модель данных (DynamoDB)

```mermaid
erDiagram
    EventMessageEntity {
        string message_id PK "UUID"
        string event_type PK "Тип события"
        string order_id "ID заказа"
        string payload "JSON payload"
        string status "PENDING/FAILED/PROCESSED"
        number created_at "Timestamp создания"
        number processed_at "Timestamp обработки"
        number retry_count "Количество ретраев"
        string error_message "Последняя ошибка"
    }
    
    EventMessageEntity ||--o{ GlobalIndexes : has
    
    GlobalIndexes {
        string order_id_index "Поиск по order_id"
        string status_created_at_index "Поиск по статусу и дате"
    }
```

## Маршруты

### Основной маршрут

```mermaid
sequenceDiagram
    participant SQS as AWS SQS
    participant Camel as Camel Route
    participant DB as DynamoDB
    participant EB as EventBridge

    SQS->>Camel: Receive message
    activate Camel
    Camel->>Camel: Unmarshal to SqsMessageDto
    
    alt Save to DB
        Camel->>DB: Save with status PENDING
        DB-->>Camel: Saved
    else Save Failed
        Camel-->>SQS: Exception (no delete)
    end
    
    Camel->>EB: PutEvents
    alt Success
        EB-->>Camel: Success
        Camel->>DB: Mark as PROCESSED
    else Failure
        EB-->>Camel: Error
        Camel->>DB: Mark as FAILED (retry_count++)
    end
    
    Camel-->>SQS: Delete message (on success)
    deactivate Camel
```

### Ретрай

```mermaid
sequenceDiagram
    participant Timer as Timer (periodic)
    participant DB as DynamoDB
    participant EB as EventBridge

    loop Every retryPeriodMs
        Timer->>DB: findFailedMessagesForRetry<br/>(retryCount < maxAttempts)
        DB-->>Timer: List of failed messages
        
        loop For each message
            Timer->>EB: Retry PutEvents
            alt Success
                EB-->>Timer: OK
                Timer->>DB: Mark as PROCESSED
            else Failure
                EB-->>Timer: Error
                Timer->>DB: Mark as FAILED (retry_count++)
            end
        end
    end
```

### Отправка в DLQ

```mermaid
sequenceDiagram
    participant Timer as Timer (periodic)
    participant DB as DynamoDB
    participant DLQ as Dead Letter Queue

    loop Every retryPeriodMs
        Timer->>DB: findMessagesExceedingRetryLimit<br/>(retryCount >= maxAttempts)
        DB-->>Timer: List of exhausted messages
        
        loop For each message
            Timer->>DLQ: Send with metadata
            DLQ-->>Timer: Success
            Timer->>DB: Delete from main table
        end
    end
```

### Восстановление из DLQ

```mermaid
sequenceDiagram
    participant Timer as Timer (periodic)
    participant DLQ as Dead Letter Queue
    participant SQS as Main Queue

    loop Every retryPeriodMs
        Timer->>DLQ: Receive messages
        DLQ-->>Timer: Messages
        
        loop For each message
            Timer->>Timer: Increment retry_count
            Timer->>Timer: Wrap to SqsMessageDto
            Timer->>SQS: Send to main queue
            Timer->>DLQ: Delete from DLQ
        end
    end
```

## Технологический стек

- **Java 21**
- **Quarkus 3.17.5** - фреймворк DI
- **Apache Camel** - библиотека для маршрутизации
- **AWS SDK** - библиотека для SQS, EventBridge, DynamoDB, Lambda, SES, KMS, IAM
- **Micrometer** - метрики
- **Mutiny** - библиотека для реактивной парадигмы
- **MapStruct, Lombok, Jackson** - утилитные библиотеки
- **LocalStack** - локальное AWS окружение

## Конфигурация приложения

Рекомендую осмотреть `env` файлик, а также `application.properties`

## Запуск проекта

### 1. Локальный запуск с LocalStack

```bash
cp .env.example .env

docker-compose up -d --build

./deploy-lambda.sh
```

### 2. Отправка тестового сообщения

```bash
aws --endpoint-url=http://localhost:4566 sqs send-message \
  --queue-url http://localhost:4566/000000000000/event-processor-queue-dev \
  --message-body '{
    "id": "msg-12345678-1234-1234-1234-123456789012",
    "type": "order.created",
    "timestamp": 1734567890123,
    "data": {
      "orderId": "ORD-12345",
      "amount": 1500.50,
      "customerId": "CUST-67890",
      "currency": "USD"
    },
    "metadata": {
      "source": "webapp",
      "version": "1.0",
      "retryCount": 0
    }
  }'
```

## Метрики

Написаны с помощью Jakarta interceptors. Пример метрик:

| Метрика | Описание |
|---------|----------|
| `dynamodb.save` | Время сохранения в DynamoDB |
| `dynamodb.find_failed_for_retry` | Время поиска failed сообщений |
| `dlq.messages.sent` | Количество сообщений, отправленных в DLQ |
| `dlq.redrive.success` | Количество успешно восстановленных сообщений |
| `dlq.redrive.failed` | Количество ошибок при восстановлении |

## CloudFormation

`template-app.yaml` реализует пример инфраструктуры для приложения, а `template-lambda.yaml` для орагнизации прав для лямбды

## Диаграмма состояний сообщения

```mermaid
stateDiagram-v2
    [*] --> PENDING: Сохранение в DynamoDB
    PENDING --> PROCESSED: Успешная отправка в EB
    PENDING --> FAILED: Ошибка отправки
    
    FAILED --> PENDING: Ретрай (retryCount < max)
    FAILED --> DLQ: retryCount >= max
    
    DLQ --> PENDING: Re-drive (восстановление)
    DLQ --> [*]: Ручное удаление
    
    PROCESSED --> [*]: Удаление из таблицы
```