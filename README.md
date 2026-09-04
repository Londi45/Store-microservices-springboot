# Microservices with Spring Boot

A microservices-based e-commerce backend built with Spring Boot 3, Spring Cloud, and Netflix Eureka. The system handles product management, inventory tracking, and order processing across independently deployable services.

---

## Architecture Overview

```
                        ┌─────────────────┐
                        │   API Gateway   │
                        │   (port 8080)   │
                        └────────┬────────┘
                                 │
              ┌──────────────────┼──────────────────┐
              │                  │                  │
   ┌──────────▼────────┐ ┌───────▼───────┐ ┌───────▼──────────┐
   │  Product Service  │ │ Order Service │ │Inventory Service │
   │   (random port)   │ │  (port 8081)  │ │  (random port)   │
   └──────────┬────────┘ └───────┬───────┘ └───────┬──────────┘
              │                  │                  │
              │          ┌───────▼───────┐          │
              │          │  calls stock  ├──────────┘
              │          │    check      │
              │          └───────────────┘
              │
   ┌──────────▼────────┐
   │  Discovery Server │
   │  (Eureka, 8761)   │
   └───────────────────┘
```

All client requests go through the **API Gateway**. Services register themselves with the **Discovery Server (Eureka)** on startup. The gateway uses load-balanced routing (`lb://`) to forward requests to services by name rather than hardcoded addresses.

---

## Services

| Service | Port | Database | Description |
|---|---|---|---|
| discovery-server | 8761 | — | Eureka service registry |
| api-gateway | 8080 | — | Single entry point, routes traffic |
| product-service | random | MongoDB | Product catalogue management |
| order-service | 8081 | MySQL | Order placement and management |
| inventory-service | random | MySQL | Stock tracking per product |

---

## Tech Stack

- **Java 17**
- **Spring Boot 3.1.2**
- **Spring Cloud 2022.0.4**
- **Spring Cloud Gateway** — API gateway with load balancing
- **Netflix Eureka** — Service discovery and registration
- **Spring Data MongoDB** — Product persistence
- **Spring Data JPA + MySQL** — Order and inventory persistence
- **WebClient (WebFlux)** — Reactive inter-service HTTP communication
- **Lombok** — Boilerplate reduction
- **Testcontainers** — Integration testing with real MongoDB container
- **Maven** — Multi-module build

---

## Prerequisites

Before running the project make sure you have the following installed:

- Java 17
- Maven 3.6+
- MongoDB (running on `localhost:27017`)
- MySQL (running on `localhost:3306`)
- Docker Desktop (required for running tests)

---

## Environment Variables

Order-service and inventory-service read database credentials from environment variables. Set these before starting:

```bash
# Windows PowerShell
$env:db_username = "root"
$env:db_password = "yourpassword"

# Windows CMD
set db_username=root
set db_password=yourpassword
```

Or set them permanently via System Properties → Environment Variables.

---

## Getting Started

### 1. Build the project

```bash
cd Microservices-Springboot-main
mvn clean install -DskipTests
```

### 2. Start services in order

Services must start in this order — Eureka must be up before anything else registers.

**Step 1 — Discovery Server**
```bash
cd discovery-server
mvn spring-boot:run
```
Eureka dashboard available at: http://localhost:8761

**Step 2 — Inventory Service**
```bash
cd inventory-service
mvn spring-boot:run
```

**Step 3 — Product Service**
```bash
cd product-service
mvn spring-boot:run
```

**Step 4 — Order Service**
```bash
cd order-service
mvn spring-boot:run
```

**Step 5 — API Gateway**
```bash
cd api-gateway
mvn spring-boot:run
```

Once all services are running, verify they appear in the Eureka dashboard at http://localhost:8761.

---

## API Reference

All requests go through the API Gateway at `http://localhost:8080`.

### Product Service

#### Create a product
```http
POST /api/product
Content-Type: application/json

{
  "name": "iPhone 13",
  "description": "Apple iPhone 13 128GB",
  "price": 1200.00
}
```
- Returns `201 Created`
- Automatically creates an inventory record with `quantity = 0`
- The product's MongoDB `id` becomes the `skuCode` in inventory

#### Get all products
```http
GET /api/product
```
- Returns `200 OK` with a list of all products

---

### Inventory Service

#### Update stock quantity
```http
PUT /api/inventory/{skuCode}?quantity=50
```
- Replace `{skuCode}` with the product's MongoDB id
- Sets the stock quantity to the given value
- Returns `200 OK`

#### Check stock
```http
GET /api/inventory?skuCode=abc123&skuCode=def456
```
- Returns in-stock status for each skuCode
- Returns `200 OK`

---

### Order Service

#### Place an order
```http
POST /api/order
Content-Type: application/json

{
  "orderLineItemsDtoList": [
    {
      "skuCode": "abc123",
      "price": 1200.00,
      "quantity": 1
    }
  ]
}
```
- Returns `201 Created` with message `"Order placed successfully."`
- Returns `500` with `"Product is not in stock, please try again later."` if any item has `quantity = 0`

---

## Complete Flow: Add Product → Stock It → Order It

```
1. Create product
   POST /api/product
   { "name": "iPhone 13", "description": "...", "price": 1200 }
   → saves to MongoDB, creates inventory with quantity=0
   → note the returned product id (e.g. "6abc123...")

2. Set stock quantity
   PUT /api/inventory/6abc123...?quantity=100
   → inventory now has 100 units

3. Place order
   POST /api/order
   { "orderLineItemsDtoList": [{ "skuCode": "6abc123...", "price": 1200, "quantity": 1 }] }
   → order-service checks inventory (all in stock)
   → saves order to MySQL with a UUID order number
```

---

## Inter-Service Communication

```
product-service  ──POST──▶  inventory-service   (on product creation)
order-service    ──GET───▶  inventory-service   (stock check before saving order)
```

Both calls use **WebClient** with Eureka load balancing (`lb://service-name`). Services are resolved by name — no hardcoded IPs or ports.

---

## Database Schema

### MongoDB — product-service
**Collection: `product`**
| Field | Type | Description |
|---|---|---|
| _id | String | Auto-generated MongoDB id (used as skuCode) |
| name | String | Product name |
| description | String | Product description |
| price | BigDecimal | Product price |

### MySQL — inventory-service
**Table: `t_inventory`**
| Column | Type | Description |
|---|---|---|
| id | Long | Auto-increment primary key |
| sku_code | String | Links to product MongoDB id |
| quantity | Integer | Current stock level |

### MySQL — order-service
**Table: `t_order`**
| Column | Type | Description |
|---|---|---|
| id | Long | Auto-generated primary key |
| order_number | String | UUID generated per order |

**Table: `t_order_line_items`**
| Column | Type | Description |
|---|---|---|
| id | Long | Auto-increment primary key |
| sku_code | String | Product skuCode |
| price | BigDecimal | Price at time of order |
| quantity | Integer | Quantity ordered |

---

## Project Structure

```
Microservices-Springboot-main/
├── pom.xml                          # Parent POM (Java 17, Spring Boot 3.1.2)
├── api-gateway/                     # Spring Cloud Gateway
├── discovery-server/                # Netflix Eureka Server
├── product-service/                 # Product catalogue (MongoDB)
│   ├── config/WebClientConfig.java
│   ├── controller/ProductController.java
│   ├── service/ProductService.java
│   ├── model/Product.java
│   └── dto/ProductRequest, ProductResponse, InventoryRequest
├── inventory-service/               # Stock management (MySQL)
│   ├── controller/InventoryController.java
│   ├── service/InventoryService.java
│   ├── model/Inventory.java
│   └── dto/InventoryRequest, InventoryResponse
└── order-service/                   # Order processing (MySQL)
    ├── config/WebClientConfig.java
    ├── controller/OrderController.java
    ├── service/OrderService.java
    ├── model/Order, OrderLineItems
    └── dto/OrderRequest, OrderLineItemsDto, InventoryResponse
```

---

## Running Tests

Tests in `product-service` use Testcontainers to spin up a real MongoDB instance. Docker Desktop must be running.

```bash
mvn test
```

To skip tests:
```bash
mvn clean install -DskipTests
```

---

## Package

All classes use the `com.gilbert` base package.
