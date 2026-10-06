# 🎮 AWESOME™ Game Store

**AWESOME™ Game Store** is a full-stack video game price aggregation and comparison platform built with **Java 17, Spring Boot 3, JPA/Hibernate, PostgreSQL, and Vanilla JavaScript**.

The platform collects game deals from multiple digital storefronts through the **CheapShark API**, allowing users to search for games, compare prices across stores, track games through a wishlist, and monitor price drops automatically.

---

## 🚀 Features

* 🎮 **Game Deal Aggregation** — Fetch game deals from multiple digital storefronts.
* 🔎 **Game Search** — Search games and filter available deals.
* 💰 **Cross-Store Price Comparison** — Compare prices across different stores and identify the best available deal.
* 🏪 **Store Information** — Retrieve supported storefronts and their metadata.
* ❤️ **Wishlist & Price Tracking** — Track games and define target prices.
* 🔔 **Automated Price Monitoring** — Scheduled background jobs periodically check tracked games for price drops.
* 💱 **Multi-Currency Support** — Display and normalize prices across multiple currencies.
* ⚡ **In-Memory Caching** — Cache frequently accessed store metadata to reduce unnecessary external API requests.
* 🛡️ **Global Exception Handling** — Standardized API error responses using Spring's exception-handling mechanisms.
* 🌐 **Responsive Web Interface** — Vanilla HTML, CSS, and JavaScript frontend integrated with the Spring Boot REST API.

---

## 🛠️ Tech Stack

### Backend

* **Java 17**
* **Spring Boot 3**
* Spring Web
* Spring Data JPA
* **Hibernate**
* Maven
* REST APIs
* Spring `RestClient`

### Database

* **PostgreSQL**
* H2 Database for development/testing

### Frontend

* HTML5
* CSS3
* Vanilla JavaScript

### External API

* **CheapShark API v1.0**

### Development Tools

* Git
* GitHub
* IntelliJ IDEA / Eclipse
* Postman / cURL

---

## 🏗️ Architecture

The application follows a layered backend architecture:

```text
                    ┌──────────────────────┐
                    │   Vanilla JS / UI   │
                    └──────────┬───────────┘
                               │
                               │ REST / JSON
                               ▼
                    ┌──────────────────────┐
                    │    REST Controllers │
                    └──────────┬───────────┘
                               │
                               ▼
                    ┌──────────────────────┐
                    │   Service Layer     │
                    │                      │
                    │ Deal Aggregation     │
                    │ Wishlist Management  │
                    │ Currency Conversion  │
                    └───────┬───────┬──────┘
                            │       │
                ┌───────────┘       └────────────┐
                ▼                                ▼
      ┌──────────────────┐             ┌──────────────────┐
      │ Spring Data JPA  │             │ CheapShark API   │
      │ / Hibernate      │             │ RestClient       │
      └────────┬─────────┘             └──────────────────┘
               │
               ▼
      ┌──────────────────┐
      │   PostgreSQL     │
      └──────────────────┘
```

---

## 📂 Project Structure

```text
src/
└── main/
    ├── java/com/gamestore/
    │
    ├── config/
    │   ├── RestClientConfig.java
    │   └── WebCorsConfig.java
    │
    ├── controller/
    │   ├── CurrencyController.java
    │   ├── DealController.java
    │   ├── StoreController.java
    │   └── WishlistController.java
    │
    ├── dto/
    │   ├── CheapSharkDealDto.java
    │   ├── CheapSharkStoreDto.java
    │   ├── request/
    │   └── response/
    │
    ├── exception/
    │   └── GlobalExceptionHandler.java
    │
    ├── model/
    │   └── WishlistItem.java
    │
    ├── repository/
    │   └── WishlistRepository.java
    │
    ├── runner/
    │   └── DealFetchStartupRunner.java
    │
    ├── scheduler/
    │   └── PriceSyncScheduler.java
    │
    └── service/
        ├── DealAggregatorService.java
        ├── GameDealClientService.java
        ├── WishlistService.java
        └── impl/
            ├── CheapSharkClientServiceImpl.java
            ├── DealAggregatorServiceImpl.java
            └── WishlistServiceImpl.java

resources/
├── application.yml
└── static/
    ├── index.html
    ├── details.html
    ├── css/
    └── js/
```

---

## 🔌 REST API

### Stores

```http
GET /api/v1/stores
```

Returns supported game storefronts and their metadata.

### Deals

```http
GET /api/v1/deals
```

Retrieves featured game deals.

```http
GET /api/v1/deals/search?query={game}
```

Searches for available deals.

Optional filters:

```text
storeId
maxPrice
```

### Price Comparison

```http
GET /api/v1/deals/compare?title={game}
```

Compares available listings across different storefronts and identifies the cheapest option.

### Currencies

```http
GET /api/v1/currencies
```

Returns supported currencies and conversion information.

### Wishlist

```http
POST /api/v1/wishlist
```

Creates or updates a tracked game.

```http
GET /api/v1/wishlist?email={email}&currency={currency}
```

Retrieves tracked games.

```http
DELETE /api/v1/wishlist/{id}
```

Removes a tracked game.

### Price Synchronization

```http
POST /api/v1/wishlist/sync-now
```

Manually triggers price synchronization.

---

## ⏰ Automated Price Tracking

AWESOME™ includes a scheduled price synchronization system.

The background scheduler periodically checks tracked games against current storefront prices.

```text
             Scheduled Task
                   │
                   ▼
          Retrieve Wishlist
                   │
                   ▼
          Fetch Current Prices
                   │
                   ▼
          Compare Target Price
                   │
             ┌─────┴─────┐
             │           │
          Price Drop   No Drop
             │           │
             ▼           ▼
        Mark Alert    Continue
```

The scheduler is implemented using Spring's `@Scheduled` mechanism.

---

## 💾 Database

The application uses **PostgreSQL** with **JPA/Hibernate** for persistent wishlist and price-tracking data.

Example entity:

```text
WishlistItem
├── id
├── userEmail
├── gameTitle
├── cheapSharkGameId
├── targetPriceUsd
├── lastCheckedPriceUsd
├── preferredCurrency
├── bestStoreName
├── triggered
├── createdAt
└── updatedAt
```

---

## 🌍 Supported Currencies

The application supports price conversion and display for:

* USD — $
* EUR — €
* GBP — £
* INR — ₹
* CAD — $
* AUD — $

Prices used for target-price tracking are normalized to a common base currency before persistence.

---

## ⚙️ Getting Started

### 1. Clone the repository

```bash
git clone https://github.com/MohammadSaadShaikh/AWESOME-Game-Store.git
```

```bash
cd AWESOME-Game-Store
```

### 2. Configure the Database

Create a PostgreSQL database and update your `application.yml`:

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/game_store
    username: YOUR_USERNAME
    password: YOUR_PASSWORD
```

Update the credentials according to your local PostgreSQL configuration.

### 3. Build the Project

Using Maven:

```bash
mvn clean install
```

### 4. Run the Application

```bash
mvn spring-boot:run
```

The application will start on:

```text
http://localhost:8080
```

---

## 🧪 API Testing

The REST APIs can be tested using:

* Postman
* cURL
* Browser for GET endpoints

Example:

```bash
curl http://localhost:8080/api/v1/deals
```

Example game search:

```bash
curl "http://localhost:8080/api/v1/deals/search?query=Cyberpunk"
```

---

## 🔄 Data Flow

```text
User
 │
 ▼
Frontend
 │
 ▼
Spring Boot REST API
 │
 ▼
Service Layer
 │
 ├──────────────► PostgreSQL
 │
 ▼
CheapShark REST API
 │
 ▼
Deal Processing & Enrichment
 │
 ▼
JSON Response
 │
 ▼
Frontend
```

---

## 💡 Technical Highlights

* Layered **Controller → Service → Repository** architecture
* RESTful API design
* Java Records for DTOs
* Spring Data JPA and Hibernate ORM
* PostgreSQL persistence
* External REST API integration using Spring `RestClient`
* Thread-safe caching using `ConcurrentHashMap`
* Scheduled background processing with `@Scheduled`
* Global exception handling using `@RestControllerAdvice`
* DTO-based request/response architecture
* Multi-currency price normalization
* Responsive frontend integrated with backend REST APIs

---

## 📌 Future Improvements

* 🔐 Spring Security authentication and authorization
* 🔑 JWT-based user authentication
* 📧 Email notifications for price drops
* 📊 Historical price charts
* 🗄️ Redis caching
* 🐳 Docker containerization
* ☁️ Cloud deployment
* 🧪 Automated unit and integration testing
* 📱 Mobile application
* 🤖 Personalized game deal recommendations
* 🔄 Integration with additional storefront APIs

---

## 👨‍💻 Author

**Mohammad Saad Mubeen Shaikh**

Computer Science & Engineering
VIT Bhopal University

* GitHub: [MohammadSaadShaikh](https://github.com/MohammadSaadShaikh)
* LinkedIn: [Saad Shaikh](https://www.linkedin.com/in/saadshaikh786)

---

## ⭐ Project

If you find **AWESOME™ Game Store** useful or interesting, consider giving the repository a ⭐.

**Built with Java & Spring Boot.**
