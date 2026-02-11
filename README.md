# TurboXpress Identity & User Management Service

A Spring Boot 3.4+ backend service using Java 21+ that manages users and roles for the TurboXpress ecosystem with JWT-based authentication and a decorator pattern for dynamic permission management.

## Features

- **JWT Authentication**: Stateless authentication with roles and permissions embedded in tokens
- **Decorator Pattern**: Dynamic permission decoration based on user roles
- **Role-Based Access Control**: Support for CUSTOMER, COURIER, MERCHANT, and ADMIN roles
- **Admin Impersonation**: Generate temporary "Ghost" tokens for admin to access the system as other users
- **Comprehensive Validation**: Jakarta Validation for DTOs with phone number and email constraints
- **Global Exception Handling**: Clean API error responses

## Technology Stack

- **Spring Boot**: 3.4+
- **Java**: 21+
- **Database**: PostgreSQL
- **Security**: Spring Security with JWT
- **ORM**: Spring Data JPA
- **Validation**: Jakarta Validation

## Prerequisites

- Java 21 or higher
- PostgreSQL 12 or higher
- Gradle 8.0 or higher

## Database Setup

1. Create a PostgreSQL database:
```bash
psql -U postgres -c "CREATE DATABASE turboxpress_identity;"
```

2. The schema will be automatically initialized on application startup from `src/main/resources/schema.sql`

## Configuration

Update `src/main/resources/application.properties` with your database credentials:

```properties
spring.datasource.url=jdbc:postgresql://localhost:5432/turboxpress_identity
spring.datasource.username=your_username
spring.datasource.password=your_password
jwt.secret=your_secret_key_here
```

## Running the Application

```bash
# Build the project
./gradlew build

# Run the application
./gradlew bootRun
```

The application will start on `http://localhost:8080`

## Sample Users

The database is initialized with the following sample users (password: `password123`):

| Phone Number   | Role     | User ID                                |
|---------------|----------|----------------------------------------|
| +1234567890   | CUSTOMER | a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11  |
| +1234567891   | COURIER  | b1ffcd99-9c0b-4ef8-bb6d-6bb9bd380a22  |
| +1234567892   | MERCHANT | c2ggde99-9c0b-4ef8-bb6d-6bb9bd380a33  |
| +1234567893   | ADMIN    | d3hhef99-9c0b-4ef8-bb6d-6bb9bd380a44  |

## API Endpoints

### Authentication

#### Login
```http
POST /api/auth/login
Content-Type: application/json

{
  "phoneNumber": "+1234567890",
  "password": "password123"
}
```

**Response:**
```json
{
  "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "type": "Bearer",
  "userId": "a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11",
  "fullName": "John Doe",
  "phoneNumber": "+1234567890",
  "roles": ["CUSTOMER"],
  "permissions": ["ORDER_PLACEMENT", "ORDER_TRACKING", "PAYMENT_MANAGEMENT", "ADDRESS_MANAGEMENT"],
  "context": {
    "loyaltyPoints": 0,
    "preferredPaymentMethod": "CARD"
  }
}
```

### Admin Endpoints (Requires ADMIN role)

#### Get All Users (Paginated)
```http
GET /api/admin/users?page=0&size=10
Authorization: Bearer <admin_token>
```

**Response:**
```json
{
  "content": [...],
  "pageNumber": 0,
  "pageSize": 10,
  "totalElements": 4,
  "totalPages": 1,
  "isLast": true
}
```

#### Impersonate User
```http
POST /api/admin/impersonate/{userId}
Authorization: Bearer <admin_token>
```

**Response:**
```json
{
  "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "type": "Bearer",
  "impersonation": true,
  "adminId": "d3hhef99-9c0b-4ef8-bb6d-6bb9bd380a44",
  "targetUser": {...},
  "expiresIn": 3600000
}
```

## Permission Decorator Pattern

The system uses a decorator pattern to dynamically add permissions based on user roles:

### Customer Permissions
- `ORDER_PLACEMENT`
- `ORDER_TRACKING`
- `PAYMENT_MANAGEMENT`
- `ADDRESS_MANAGEMENT`

### Courier Permissions
- `VEHICLE_MANAGEMENT`
- `LIVE_TRACKING`
- `DELIVERY_MANAGEMENT`
- `ROUTE_OPTIMIZATION`

### Merchant Permissions
- `STORE_MANAGEMENT`
- `INVENTORY_MANAGEMENT`
- `ORDER_MANAGEMENT`
- `ANALYTICS_ACCESS`

**Context includes:** `storeId`, `storeName`, `businessType`, `operatingHours`

### Admin Permissions
- `USER_MANAGEMENT`
- `SYSTEM_CONFIGURATION`
- `IMPERSONATION`
- `AUDIT_ACCESS`
- `FULL_ANALYTICS`

## Testing with cURL

### Login as Customer
```bash
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"phoneNumber": "+1234567890", "password": "password123"}'
```

### Login as Admin
```bash
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"phoneNumber": "+1234567893", "password": "password123"}'
```

### Get All Users (Admin)
```bash
curl -X GET "http://localhost:8080/api/admin/users?page=0&size=10" \
  -H "Authorization: Bearer <admin_token>"
```

### Impersonate User (Admin)
```bash
curl -X POST http://localhost:8080/api/admin/impersonate/a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11 \
  -H "Authorization: Bearer <admin_token>"
```

## Project Structure

```
src/main/kotlin/com/turboxpress/identity/
├── config/
│   └── SecurityConfig.kt
├── controller/
│   ├── AuthController.kt
│   └── AdminController.kt
├── dto/
│   ├── LoginRequest.kt
│   ├── LoginResponse.kt
│   ├── UserResponse.kt
│   ├── ImpersonateResponse.kt
│   └── PageResponse.kt
├── entity/
│   ├── User.kt
│   └── Role.kt
├── exception/
│   ├── GlobalExceptionHandler.kt
│   ├── ErrorResponse.kt
│   ├── UserNotFoundException.kt
│   └── InvalidCredentialsException.kt
├── repository/
│   ├── UserRepository.kt
│   └── RoleRepository.kt
├── security/
│   ├── JwtService.kt
│   ├── CustomUserDetailsService.kt
│   ├── JwtAuthenticationFilter.kt
│   ├── UserPrincipal.kt
│   └── decorator/
│       ├── PermissionDecorator.kt
│       ├── BaseUserPrincipal.kt
│       ├── CustomerPermissionDecorator.kt
│       ├── CourierPermissionDecorator.kt
│       ├── MerchantPermissionDecorator.kt
│       └── AdminPermissionDecorator.kt
├── service/
│   ├── AuthService.kt
│   └── UserService.kt
└── IdentityApplication.kt
```

## Security Considerations

1. **JWT Secret**: Change the default JWT secret in production
2. **Password Hashing**: Uses BCrypt for secure password storage
3. **Impersonation Tokens**: Have shorter expiration (1 hour vs 24 hours)
4. **CORS**: Configure allowed origins for production
5. **HTTPS**: Always use HTTPS in production

## License

MIT
