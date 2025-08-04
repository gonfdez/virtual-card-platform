# Virtual Card Platform - SDE II Challenge

## 📋 Overview

Virtual card platform backend built with Spring Boot, featuring card creation, balance management, and robust concurrency handling through optimistic locking.

**Public Repository:** [https://github.com/gonfdez/virtual-card-platform](https://github.com/gonfdez/virtual-card-platform)

## 🚀 Development Phases

1. **Initial Setup and Data Model**: Project setup and core entities (`Card`, `Transaction`)
2. **Business Logic and REST API**: Service layer and controller implementation with all endpoints
3. **Integration Tests**: End-to-end testing through HTTP endpoints with database integration
4. **Concurrency Handling**: Optimistic locking implementation with retry logic and integration test to verify concurrency behavior
5. **Unit Tests**: Isolated service layer testing with mocked dependencies

## 🧪 Testing Strategy

- **Integration Tests**: Full end-to-end testing including concurrency verification with 100 simultaneous requests
- **Unit Tests**: Service layer logic testing with Mockito mocks

## 🔄 Concurrency Solution

- **Optimistic Locking**: `@Version` field automatically detects concurrent modifications
- **Retry Logic**: Up to 3 attempts with exponential backoff for failed operations  
- **Fresh Transactions**: `REQUIRES_NEW` propagation ensures clean state on retries
- **Graceful Degradation**: Returns HTTP 409 Conflict when max retries exceeded

## ✅ Technical Requirements Implemented

- **Spring Boot with Java**: Complete implementation using Spring Boot framework
- **Spring Data JPA**: Used for all data persistence operations with H2 in-memory database
- **Unit and Integration Tests**: Comprehensive test coverage for all functionality
- **Transactional Safety**: `@Transactional` annotations with optimistic concurrency control
- **Proper Layering**: Clean Controller → Service → Repository architecture
- **HTTP Status Codes**: Meaningful status codes (200, 201, 400, 404, 409) with proper validation

## 🌟 Bonus Features Implemented

- **Optimistic Locking**: `@Version` field on Card entity for concurrency control

## ⚖️ Trade-offs Made Due to Time Constraints

- **Unit Tests**: Initially relied on manual Postman testing and integration tests. Unit tests were added in the final phase
- **Error Responses**: Used simple string responses instead of structured error DTOs
- **Validation**: Basic validation instead of comprehensive Bean Validation annotations

## 🚀 Potential Improvements with More Time

### Technical Enhancements
- Custom exception classes with structured error responses
- Comprehensive logging with correlation IDs
- Bean Validation for input validation
- API documentation with Swagger/OpenAPI

### Feature Enhancements  
- Card status management (ACTIVE/BLOCKED)
- Transaction pagination support
- Rate limiting per card
- Enhanced audit trail with metadata

## 📚 Learning Strategy & Tools Adopted

This was my first experience with Java Spring Boot. I used a structured learning approach:

- **Official Documentation**: Continuously referenced Spring Boot, Spring Data JPA, and Spring Web documentation
- **Video Learning**: Supplemented with YouTube tutorials for practical examples
- **AI-Assisted Development**: Used Claude AI as a learning partner while ensuring I understood every implementation detail
- **Hands-on Experimentation**: Iterative development with Postman for manual testing

### Key Concepts Mastered
- Spring Boot architecture and dependency injection
- JPA/Hibernate entity modeling and optimistic locking
- Transaction management and propagation strategies
- Spring testing with `@SpringBootTest` and Mockito