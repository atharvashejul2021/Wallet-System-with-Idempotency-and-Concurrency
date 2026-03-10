# Wallet System with Idempotency & Concurrency

A secure digital wallet backend system built with Spring Boot 3.2, similar to Paytm / PhonePe.

## Tech Stack
- Java 17
- Spring Boot 3.2
- Spring Security + JWT (JJWT 0.11.5)
- Spring Data JPA + H2 (in-memory)
- Lombok
- JaCoCo (test coverage)

---

## How to Run Locally

### Prerequisites
- Java 17+
- Maven 3.8+

### Steps
```bash
git clone <your-repo-url>
cd wallet-system
mvn spring-boot:run
```

- App runs at: **http://localhost:8080**
- H2 Console: **http://localhost:8080/h2-console**
  - JDBC URL: `jdbc:h2:mem:walletdb`
  - Username: `sa` | Password: *(empty)*

### Run Tests
```bash
mvn test
# Coverage report at: target/site/jacoco/index.html
```

---

## Security Implementation

JWT-based stateless authentication using JJWT.

- All endpoints except `/auth/**` and `/h2-console/**` require a valid JWT.
- Token is passed via `Authorization: Bearer <token>` header.
- **Critical security rule**: Wallet ID is always derived from the authenticated user extracted from JWT — never from the request body. This prevents horizontal privilege escalation.
- Passwords are encrypted using `BCryptPasswordEncoder`.
- Role-based access:
  - `ROLE_USER` — wallet operations
  - `ROLE_ADMIN` — read-only admin views

---

## Idempotency Handling

Every mutating wallet operation (`/wallet/add`, `/wallet/transfer`) requires an `Idempotency-Key` header.

- The key is stored in the `transactions` table with a **DB-level UNIQUE constraint**, enforcing idempotency at the database level (not just in application code).
- On any duplicate request with the same key, the service immediately returns the existing transaction result without re-processing.
- This safely handles network retries, double-clicks, or client timeouts.

---

## Concurrency Strategy

### Add Money — Optimistic Locking
- `Wallet` entity has a `@Version` field (Long).
- JPA's optimistic locking detects concurrent modifications — if two threads try to update the same wallet simultaneously, one will receive an `OptimisticLockException` and must retry.
- Suitable for add-money since conflicts are less frequent.

### Transfer — Pessimistic Locking
- Uses `SELECT ... FOR UPDATE` (`PESSIMISTIC_WRITE` lock) on both wallets.
- **Deadlock prevention**: Locks are always acquired in ascending wallet ID order, ensuring two concurrent transfers between the same pair of wallets always lock in the same sequence.
- The entire transfer runs in a `SERIALIZABLE` transaction for full atomicity.

---

## Transaction Boundaries

| Method | Isolation | Notes |
|--------|-----------|-------|
| `register` | Default (READ_COMMITTED) | User + wallet creation atomic |
| `addMoney` | READ_COMMITTED | Idempotency check + balance update |
| `transfer` | SERIALIZABLE | Full atomic debit+credit with pessimistic locks |
| `getWallet` | readOnly = true | No locking, no writes |
| `getTransactions` | readOnly = true | Paginated, no writes |

---

## API Reference

| Method | Endpoint | Role | Header |
|--------|----------|------|--------|
| POST | /auth/register | Public | — |
| POST | /auth/login | Public | — |
| POST | /wallet/add | USER | Idempotency-Key |
| POST | /wallet/transfer | USER | Idempotency-Key |
| GET  | /wallet | USER | — |
| GET  | /wallet/transactions | USER | — |
| GET  | /admin/wallets | ADMIN | — |
| GET  | /admin/transactions | ADMIN | — |

---

## Sample API Calls

```bash
# Register
curl -X POST http://localhost:8080/auth/register \
  -H "Content-Type: application/json" \
  -d '{"email":"alice@test.com","password":"pass123"}'

# Login
curl -X POST http://localhost:8080/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"alice@test.com","password":"pass123"}'
# Save the "token" from the response

# Add Money
curl -X POST http://localhost:8080/wallet/add \
  -H "Authorization: Bearer <TOKEN>" \
  -H "Idempotency-Key: add-001" \
  -H "Content-Type: application/json" \
  -d '{"amount":1000}'

# Transfer
curl -X POST http://localhost:8080/wallet/transfer \
  -H "Authorization: Bearer <TOKEN>" \
  -H "Idempotency-Key: txfr-001" \
  -H "Content-Type: application/json" \
  -d '{"receiverEmail":"bob@test.com","amount":200}'

# Get Wallet Balance
curl http://localhost:8080/wallet \
  -H "Authorization: Bearer <TOKEN>"

# Get Transactions (paginated)
curl "http://localhost:8080/wallet/transactions?page=0&size=10" \
  -H "Authorization: Bearer <TOKEN>"
```
