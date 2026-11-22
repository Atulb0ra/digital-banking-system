# Banking System – Microservices Architecture

A distributed banking platform built using **Java, Spring Boot, Spring Cloud, Apache Kafka, MySQL, Redis, and Razorpay**.

The system supports account management, money transfers, OTP verification, fraud detection, refunds, payment order creation, webhook processing, and notifications.

---

## 1. System Architecture

```text
Client / Swagger
        |
        v
   API Gateway :8080
        |
        +-------------------+-------------------+
        |                   |                   |
        v                   v                   v
Account Service      Transaction          Payment
:8081                Service :8082        Service :8083
        |                   |                   |
        |                   v                   v
        |               Redis OTP          Razorpay
        |                   |                   |
        +---------------- Kafka ----------------+
                            |
              +-------------+-------------+
              |                           |
              v                           v
      Fraud Detection               Notification
        Service :8084                 Service :8085
```

### Communication

- **REST:** Client → API Gateway → Microservices
- **OpenFeign:** Transaction/Fraud services → Account Service
- **Kafka:** Fraud, OTP, refund, transaction, and payment events
- **Redis:** OTP storage, expiration, and gateway rate limiting
- **MySQL:** Persistent account, transaction, and payment records
- **Razorpay:** External payment gateway

## 2. Microservices

| Service | Port | Responsibility |
|---|---:|---|
| API Gateway | 8080 | Central routing, CORS, rate limiting, Swagger aggregation |
| Account Service | 8081 | Accounts, balances, blocking, credit and debit operations |
| Transaction Service | 8082 | Transfers, transaction lifecycle, OTP verification, refunds |
| Payment Service | 8083 | Razorpay payment orders and webhook processing |
| Fraud Detection | 8084 | Transaction velocity, amount, and balance risk checks |
| Notification | 8085 | Transaction, OTP, fraud, refund, and payment notifications |

## 3. Technology Stack

- Java 17
- Spring Boot
- Spring Cloud Gateway WebFlux
- Spring Cloud OpenFeign
- Spring Data JPA / Hibernate
- Apache Kafka
- Redis
- MySQL
- Razorpay Java SDK
- Springdoc OpenAPI / Swagger
- Maven
- Docker Compose
- Lombok

## 4. API Gateway Service

**Port:** 8080

### Responsibilities

- Acts as the single entry point for all banking microservices.
- Routes account, transaction, and payment API requests.
- Exposes Swagger/OpenAPI documentation for every service.
- Applies Redis-based request rate limiting.
- Configures CORS for frontend and Swagger clients.
- Prevents duplicate CORS headers in responses.
- Rewrites Swagger document paths before forwarding them.

### Gateway Routes

| Route | Destination |
|---|---|
| `/api/v1/accounts/` | Account Service — 8081 |
| `/api/v1/transactions/` | Transaction Service — 8082 |
| `/api/v1/payments/` | Payment Service — 8083 |
| `/v3/api-docs/account` | Account Service OpenAPI document |
| `/v3/api-docs/transaction` | Transaction Service OpenAPI document |
| `/v3/api-docs/payment` | Payment Service OpenAPI document |

### Swagger Configuration

The gateway serves Swagger UI at:

`http://localhost:8080/swagger-ui.html`

Swagger dropdown services:

- Account Service
- Transaction Service
- Payment Service

This allows all three API documentation interfaces to be accessed through one gateway interface.

### Rate Limiting

The gateway uses Redis as a reactive rate limiter:

| Route | Requests per second | Burst capacity |
|---|---:|---:|
| Account routes | 10 | 20 |
| Transaction routes | 10 | 20 |
| Payment routes | 5 | 10 |

### Gateway Benefits

- Centralized routing
- Simplified client integration
- Consistent CORS handling
- Protection through rate limiting
- Unified Swagger documentation
- Decouples frontend clients from individual service ports

## 5. Account Service

**Port:** 8081

### Responsibilities

- Create accounts
- Retrieve account details
- Get account balances
- Deduct sender balances
- Credit receiver or refunded balances
- Block compromised accounts
- Consume fraud and compensation events

### Main Endpoints

| Method | Endpoint |
|---|---|
| POST | `/api/v1/accounts` |
| GET | `/api/v1/accounts/{accountNumber}` |
| GET | `/api/v1/accounts/{accountNumber}/balance` |
| PUT | `/api/v1/accounts/{accountNumber}/block` |
| PUT | `/api/v1/accounts/{accountNumber}/deduct` |
| PUT | `/api/v1/accounts/{accountNumber}/credit` |

The `deductBalance()` operation rejects inactive accounts. The `creditBalance()` operation is used for receiver credits and compensating refunds.

## 6. Transaction Service

**Port:** 8082

### Responsibilities

- Initiate transfers
- Persist transaction records
- Deduct sender balances
- Publish transaction events
- Generate OTP values
- Validate submitted OTP values
- Complete successful transactions
- Refund failed or suspicious transfers
- Publish fraud, refund, and completion events

### Transaction Endpoints

| Method | Endpoint |
|---|---|
| POST | `/api/v1/transactions/transfer` |
| GET | `/api/v1/transactions/{transactionId}` |
| GET | `/api/v1/transactions/account/{accountNumber}` |
| POST | `/api/v1/transactions/{transactionId}/verify?otp={otp}` |

## 7. Fraud Detection Service

**Port:** 8084

Consumes transaction initiation events and evaluates:

1. Transaction velocity
2. Unusually large transaction amounts
3. Balance risk percentage
4. Suspicious activity thresholds

### Fraud Outcomes

- **Clean transaction:** Publish `fraud.check.clean`
- **Suspicious transaction:** Publish `verification.required`
- **Confirmed fraud:** Publish `fraud.detected`

The Account Service consumes the fraud event and blocks the sender account.

## 8. Notification Service

**Port:** 8085

Consumes Kafka events for:

- Generated OTPs
- Transaction completion
- Refunds
- Fraud detection
- Payment completion
- Payment failure

Notification delivery can later be integrated with email, SMS, WebSocket, push notifications, or an external provider.

## 9. Payment Service

**Port:** 8083

### Responsibilities

- Create Razorpay payment orders
- Convert application amounts into Razorpay subunits
- Store payment records in MySQL
- Return Razorpay checkout information
- Receive Razorpay webhook events
- Mark payments as COMPLETED or FAILED
- Publish payment events to Kafka
- Trigger payment notifications

### Payment Endpoints

| Method | Endpoint |
|---|---|
| POST | `/api/v1/payments/create-order` |
| POST | `/api/v1/payments/webhook` |

### Create Payment Flow

1. Client submits account number and payment amount.
2. Payment Service creates a Razorpay order.
3. Razorpay returns an order ID.
4. Payment is stored with CREATED status.
5. The frontend opens Razorpay Checkout.
6. Razorpay sends a payment webhook.
7. Payment status changes to COMPLETED or FAILED.
8. A Kafka payment event is published.
9. Notification Service processes the event.

```text
Create order
    |
Razorpay checkout
    |
Payment webhook
    |
COMPLETED / FAILED
    |
Kafka payment event
    |
Notification
```

**Note:** Razorpay webhook integration is not implemented yet. Implementing it requires deploying the application to a publicly accessible HTTPS domain and configuring the webhook URL in the Razorpay Dashboard.

## 10. Kafka Event Architecture

| Topic | Purpose |
|---|---|
| `transaction.initiated` | Start fraud analysis |
| `fraud.check.clean` | Approve transaction |
| `verification.required` | Require OTP verification |
| `transaction.otp.generated` | Notify the user of a generated OTP |
| `transaction.completed` | Publish successful completion |
| `transaction.refunded` | Publish refund compensation |
| `fraud.detected` | Block the suspicious account |
| `payment.completed` | Notify successful payment |
| `payment.failed` | Notify failed payment |

## 11. Standard Transfer Flow

```text
Client
  |
  v
API Gateway
  |
  v
Transaction Service
  |
  +--> Deduct sender balance
  |
  +--> Save transaction as PROCESSING
  |
  +--> Publish transaction.initiated
              |
              v
      Fraud Detection
              |
       +------+------+
       |             |
       v             v
 fraud.check.clean  verification.required
       |             |
       v             v
  COMPLETE     Generate OTP in Redis
                      |
                      v
              OTP verification
                      |
               +------+------+
               |             |
               v             v
           COMPLETE       FLAGGED + refund
```

## 12. OTP Verification

An OTP is generated after a suspicious transaction:

```text
Key: verification:otp:{transactionId}
Value: generated OTP
TTL: 5 minutes
```

Redis automatically expires OTP values after five minutes.

### Correct OTP

1. Read OTP from Redis.
2. Compare the submitted OTP.
3. Delete the Redis key.
4. Mark the transaction COMPLETE.
5. Publish `transaction.completed`.

### Incorrect OTP

1. Remove the OTP from Redis.
2. Publish `fraud.detected`.
3. Block the sender account.
4. Credit the deducted amount back.
5. Mark the transaction FLAGGED.
6. Publish `transaction.refunded`.

### Expired OTP

1. Redis returns no OTP.
2. Refund the sender account.
3. Mark the transaction FLAGGED.
4. Publish `transaction.refunded`.

## 13. Saga Compensation

The sender balance is deducted before fraud verification. If OTP verification expires or fails, the system performs a compensating transaction:

```text
Credit sender balance
  |
  v
Update transaction to FLAGGED
  |
  v
Publish transaction.refunded
  |
  v
Notify the user
```

This is a compensating-transactions SAGA pattern. It does not provide atomic distributed transactions, but it provides eventual consistency through Kafka events and compensation logic.

## 14. Databases

### MySQL

- `account_db`: Account records and balances
- `transaction_db`: Transaction lifecycle records
- `payment_db`: Razorpay order and payment records

### Redis

- OTP key-value storage
- Gateway rate-limiter state
- Five-minute OTP expiration

### Kafka Topics

Kafka provides asynchronous communication between transaction, fraud, payment, account, and notification services.

## 15. Project Setup and Installation

Follow these steps to run the Banking System locally.

### Prerequisites

Make sure the following tools are installed:

- Java 17
- Maven
- Docker Desktop
- Git
- IntelliJ IDEA or another Java IDE

### Step 1: Clone the Repository

```bash
git clone <YOUR_GITHUB_REPOSITORY_URL>
cd Banking-System
```

Replace the repository URL with the actual GitHub repository URL.

### Step 2: Configure Environment Variables

Configure the required environment variables before starting the services.

Example configuration:

```env
SPRING_DATASOURCE_USERNAME=root
SPRING_DATASOURCE_PASSWORD=root

RAZORPAY_KEY_ID=your_test_key
RAZORPAY_KEY_SECRET=your_test_secret
RAZORPAY_WEBHOOK_SECRET=your_webhook_secret

SPRING_KAFKA_BOOTSTRAP_SERVERS=localhost:9092

SPRING_REDIS_HOST=localhost
SPRING_REDIS_PORT=7000
```

Use your own local database credentials and Razorpay test credentials.

Make sure the variable names match the configuration used by the individual services. Configure them in the appropriate `.env` file, IDE run configuration, or shell environment as required by the project.

**Important:** Never commit actual database passwords, Razorpay credentials, or webhook secrets to GitHub.

### Step 3: Start Docker Infrastructure

The project uses Docker Compose to run the required infrastructure services.

From the directory containing the Docker Compose file, execute:

```bash
docker-compose up -d
```

This starts the following infrastructure:

| Service | Port |
|---|---:|
| MySQL | 3306 |
| Redis | 7000 |
| Kafka | 9092 |
| Zookeeper | 2181 |

Verify that the containers are running:

```bash
docker-compose ps
```

Wait until the infrastructure services are ready before starting the Spring Boot applications.

### Step 4: Build the Microservices

Build each Spring Boot service using Maven.

From each service directory, run:

```bash
mvn clean install
```

Repeat this for the individual service directories.

### Step 5: Run the Microservices

Start the services in the following order:

1. API Gateway — `8080`
2. Account Service — `8081`
3. Transaction Service — `8082`
4. Fraud Detection Service — `8084`
5. Notification Service — `8085`
6. Payment Service — `8083`


Run each service from its respective directory:

```bash
cd account-service
mvn spring-boot:run
```

Open separate terminals for the other services and execute:

```bash
cd transaction-service
mvn spring-boot:run
```

```bash
cd fraud-detection-service
mvn spring-boot:run
```

```bash
cd notification-service
mvn spring-boot:run
```

```bash
cd payment-service
mvn spring-boot:run
```

```bash
cd api-gateway
mvn spring-boot:run
```

Use the actual directory names present in the cloned repository if they differ.

### Step 6: Access the Application

Once the required services are running, access the gateway Swagger UI:

`http://localhost:8080/swagger-ui.html`

The gateway routes requests to the Account, Transaction, and Payment services.

**Important:** Swagger UI aggregation depends on the API Gateway and the Account, Transaction, and Payment services being available. If any of these services are stopped or their OpenAPI endpoints cannot be reached, the corresponding Swagger documentation may not load correctly.

The other services must also be running for their respective Kafka-based workflows to function correctly.

## 16. Swagger Documentation

### API Gateway Swagger UI

`http://localhost:8080/swagger-ui.html`

The gateway provides a unified Swagger interface for:

- Account Service
- Transaction Service
- Payment Service

### Aggregated OpenAPI Documents

| Service | URL |
|---|---|
| Account | `http://localhost:8080/v3/api-docs/account` |
| Transaction | `http://localhost:8080/v3/api-docs/transaction` |
| Payment | `http://localhost:8080/v3/api-docs/payment` |

### Direct Service Documentation

| Service | URL |
|---|---|
| Account | `http://localhost:8081/v3/api-docs` |
| Transaction | `http://localhost:8082/v3/api-docs` |
| Payment | `http://localhost:8083/v3/api-docs` |

## 17. Environment Variables

Configure the following environment variables according to your local setup:

| Variable | Description |
|---|---|
| `SPRING_DATASOURCE_USERNAME` | MySQL username |
| `SPRING_DATASOURCE_PASSWORD` | MySQL password |
| `RAZORPAY_KEY_ID` | Razorpay test API key |
| `RAZORPAY_KEY_SECRET` | Razorpay test API secret |
| `RAZORPAY_WEBHOOK_SECRET` | Razorpay webhook secret |
| `SPRING_KAFKA_BOOTSTRAP_SERVERS` | Kafka bootstrap address |

Do not commit real credentials or secrets to the repository.

## Future Improvements

- Implement Spring Security with JWT-based authentication and authorization.
- Implement SMS notifications for OTP verification and transaction alerts.
- Implement idempotency to prevent duplicate transaction and payment processing.
- Add integration testing for inter-service communication and transaction workflows.
- Implement Razorpay webhooks to handle payment status updates and verify successful payments.
