# Database Configuration

## MySQL Connection Details
- **Host**: `localhost` (or `10.0.2.2` from Android emulator)
- **Port**: `3306`
- **Database**: `attendance`
- **Username**: `attend`
- **Password**: `strongpass`
- **Root Password**: `root`

## Docker Container
- **Container Name**: `attend-mysql`
- **Image**: `mysql:8.4`
- **Volume**: `attend_mysql_data`

## Spring Boot Backend Configuration
```properties
# application.properties
spring.datasource.url=jdbc:mysql://localhost:3306/attendance?useSSL=false&serverTimezone=UTC&characterEncoding=utf8mb4&useUnicode=true
spring.datasource.username=attend
spring.datasource.password=strongpass
spring.jpa.database-platform=org.hibernate.dialect.MySQL8Dialect
spring.jpa.hibernate.ddl-auto=update
spring.jpa.show-sql=true
```

## Android App API Configuration
Update your API base URL in the Android app to point to your Spring Boot backend:
```java
// In ApiClient.java
public static final String BASE_URL = "http://10.0.2.2:8080/"; // For Android emulator
// or
public static final String BASE_URL = "http://localhost:8080/"; // For physical device
```

## Test Connection
```bash
# Test MySQL connection
docker exec -it attend-mysql mysql -u attend -p attendance
# Enter password: strongpass
```

