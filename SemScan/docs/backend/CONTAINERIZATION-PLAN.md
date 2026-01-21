# 🐳 SemScan Backend - Containerization Plan

This document outlines the complete plan for containerizing the SemScan Backend application.

---

## 📋 **Containerization Overview**

### **Goals**
- Create a standalone, containerized backend service
- Enable easy deployment and scaling
- Provide consistent development and production environments
- Support both single-container and multi-container deployments

### **Technologies**
- **Docker** - Containerization platform
- **Docker Compose** - Multi-container orchestration
- **MySQL 8.4** - Database container
- **Spring Boot** - Application container
- **Nginx** - Reverse proxy (optional)

---

## 🏗️ **Container Architecture**

### **Single Container Setup**
```
┌─────────────────────────────────────┐
│           Docker Host               │
│  ┌─────────────────────────────────┐│
│  │     SemScan Backend Container   ││
│  │  ┌─────────────────────────────┐││
│  │  │    Spring Boot Application  │││
│  │  │         (Port 8080)         │││
│  │  └─────────────────────────────┘││
│  └─────────────────────────────────┘│
└─────────────────────────────────────┘
```

### **Multi-Container Setup**
```
┌─────────────────────────────────────────────────────────┐
│                  Docker Host                            │
│  ┌─────────────────────┐    ┌─────────────────────────┐ │
│  │  SemScan Backend    │    │    MySQL Database       │ │
│  │     Container       │    │      Container          │ │
│  │ ┌─────────────────┐ │    │ ┌─────────────────────┐ │ │
│  │ │ Spring Boot App │ │◄──►│ │   MySQL 8.4        │ │ │
│  │ │   (Port 8080)   │ │    │ │   (Port 3306)      │ │ │
│  │ └─────────────────┘ │    │ └─────────────────────┘ │ │
│  └─────────────────────┘    └─────────────────────────┘ │
└─────────────────────────────────────────────────────────┘
```

---

## 📁 **Required Files for Containerization**

### **Dockerfile**
```dockerfile
# Use OpenJDK 17 as base image
FROM openjdk:17-jdk-slim

# Set working directory
WORKDIR /app

# Copy the JAR file
COPY target/semscan-backend-1.0.0.jar app.jar

# Expose port
EXPOSE 8080

# Health check
HEALTHCHECK --interval=30s --timeout=3s --start-period=5s --retries=3 \
  CMD curl -f http://localhost:8080/actuator/health || exit 1

# Run the application
ENTRYPOINT ["java", "-jar", "app.jar"]
```

### **Docker Compose (Development)**
```yaml
version: '3.8'

services:
  # SemScan Backend Service
  semscan-backend:
    build: .
    container_name: semscan-backend
    ports:
      - "8080:8080"
    environment:
      - SPRING_DATASOURCE_URL=jdbc:mysql://mysql:3306/attendance?useSSL=false&serverTimezone=UTC&characterEncoding=utf8mb4&useUnicode=true
      - SPRING_DATASOURCE_USERNAME=attend
      - SPRING_DATASOURCE_PASSWORD=strongpass
      - SPRING_DATASOURCE_DRIVER_CLASS_NAME=com.mysql.cj.jdbc.Driver
      - SPRING_JPA_HIBERNATE_DDL_AUTO=validate
      - SPRING_JPA_SHOW_SQL=true
      - SPRING_PROFILES_ACTIVE=docker
    depends_on:
      mysql:
        condition: service_healthy
    networks:
      - semscan-network
    restart: unless-stopped

  # MySQL Database Service
  mysql:
    image: mysql:8.4
    container_name: semscan-mysql
    environment:
      MYSQL_ROOT_PASSWORD: root
      MYSQL_DATABASE: attendance
      MYSQL_USER: attend
      MYSQL_PASSWORD: strongpass
    ports:
      - "3306:3306"
    volumes:
      - mysql_data:/var/lib/mysql
      - ./database:/docker-entrypoint-initdb.d
    networks:
      - semscan-network
    restart: unless-stopped
    healthcheck:
      test: ["CMD", "mysqladmin", "ping", "-h", "localhost"]
      timeout: 20s
      retries: 10

volumes:
  mysql_data:
    driver: local

networks:
  semscan-network:
    driver: bridge
```

### **Docker Compose (Production)**
```yaml
version: '3.8'

services:
  # SemScan Backend Service
  semscan-backend:
    image: semscan-backend:latest
    container_name: semscan-backend-prod
    ports:
      - "8080:8080"
    environment:
      - SPRING_DATASOURCE_URL=jdbc:mysql://mysql:3306/attendance?useSSL=true&serverTimezone=UTC&characterEncoding=utf8mb4&useUnicode=true
      - SPRING_DATASOURCE_USERNAME=${DB_USERNAME}
      - SPRING_DATASOURCE_PASSWORD=${DB_PASSWORD}
      - SPRING_DATASOURCE_DRIVER_CLASS_NAME=com.mysql.cj.jdbc.Driver
      - SPRING_JPA_HIBERNATE_DDL_AUTO=validate
      - SPRING_JPA_SHOW_SQL=false
      - SPRING_PROFILES_ACTIVE=production
      - APP_API_KEY=${API_KEY}
    depends_on:
      mysql:
        condition: service_healthy
    networks:
      - semscan-network
    restart: always
    deploy:
      resources:
        limits:
          memory: 512M
        reservations:
          memory: 256M

  # MySQL Database Service
  mysql:
    image: mysql:8.4
    container_name: semscan-mysql-prod
    environment:
      MYSQL_ROOT_PASSWORD: ${MYSQL_ROOT_PASSWORD}
      MYSQL_DATABASE: attendance
      MYSQL_USER: ${DB_USERNAME}
      MYSQL_PASSWORD: ${DB_PASSWORD}
    volumes:
      - mysql_data:/var/lib/mysql
      - ./database:/docker-entrypoint-initdb.d
    networks:
      - semscan-network
    restart: always
    healthcheck:
      test: ["CMD", "mysqladmin", "ping", "-h", "localhost"]
      timeout: 20s
      retries: 10

  # Nginx Reverse Proxy (Optional)
  nginx:
    image: nginx:alpine
    container_name: semscan-nginx
    ports:
      - "80:80"
      - "443:443"
    volumes:
      - ./nginx/nginx.conf:/etc/nginx/nginx.conf
      - ./nginx/ssl:/etc/nginx/ssl
    depends_on:
      - semscan-backend
    networks:
      - semscan-network
    restart: always

volumes:
  mysql_data:
    driver: local

networks:
  semscan-network:
    driver: bridge
```

### **Environment Files**
```bash
# .env (for production)
DB_USERNAME=attend
DB_PASSWORD=strongpass
MYSQL_ROOT_PASSWORD=root
API_KEY=test-api-key-12345
```

### **Docker Ignore File**
```dockerignore
# Maven
target/
pom.xml.tag
pom.xml.releaseBackup
pom.xml.versionsBackup
pom.xml.next
release.properties
dependency-reduced-pom.xml
buildNumber.properties
.mvn/timing.properties
.mvn/wrapper/maven-wrapper.jar

# IDE
.idea/
*.iws
*.iml
*.ipr
.vscode/
*.swp
*.swo

# OS
.DS_Store
Thumbs.db

# Logs
*.log

# Runtime data
pids
*.pid
*.seed
*.pid.lock

# Coverage directory used by tools like istanbul
coverage/

# Dependency directories
node_modules/

# Optional npm cache directory
.npm

# Optional REPL history
.node_repl_history

# Output of 'npm pack'
*.tgz

# Yarn Integrity file
.yarn-integrity

# dotenv environment variables file
.env
.env.test
.env.production
```

---

## 🚀 **Build and Deployment Commands**

### **Development Setup**
```bash
# Build the application
./mvnw clean package

# Build Docker image
docker build -t semscan-backend:latest .

# Run with Docker Compose
docker-compose up -d

# View logs
docker-compose logs -f semscan-backend

# Stop services
docker-compose down
```

### **Production Deployment**
```bash
# Build production image
docker build -t semscan-backend:prod .

# Tag for registry
docker tag semscan-backend:prod your-registry/semscan-backend:latest

# Push to registry
docker push your-registry/semscan-backend:latest

# Deploy with production compose
docker-compose -f docker-compose.prod.yml up -d

# Scale the service
docker-compose -f docker-compose.prod.yml up -d --scale semscan-backend=3
```

---

## 🔧 **Configuration for Containerization**

### **Application Properties (Docker Profile)**
```properties
# application-docker.properties
server.port=8080
server.servlet.context-path=/

# Database Configuration (using container networking)
spring.datasource.url=jdbc:mysql://mysql:3306/attendance?useSSL=false&serverTimezone=UTC&characterEncoding=utf8mb4&useUnicode=true
spring.datasource.username=attend
spring.datasource.password=strongpass
spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver

# JPA Configuration
spring.jpa.hibernate.ddl-auto=validate
spring.jpa.show-sql=false
spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.MySQL8Dialect

# CORS Configuration
cors.allowed.origins=http://localhost:8080,http://10.0.2.2:8080,http://localhost:3000
cors.allowed.methods=GET,POST,PUT,DELETE,PATCH,OPTIONS
cors.allowed.headers=*
cors.allow.credentials=true

# Logging
logging.level.com.semscan.backend=INFO
logging.level.org.springframework.web=INFO
logging.level.org.hibernate.SQL=WARN

# Management endpoints
management.endpoints.web.exposure.include=health,info,metrics
management.endpoint.health.show-details=when-authorized
```

### **Application Properties (Production Profile)**
```properties
# application-production.properties
server.port=8080
server.servlet.context-path=/

# Database Configuration
spring.datasource.url=jdbc:mysql://mysql:3306/attendance?useSSL=true&serverTimezone=UTC&characterEncoding=utf8mb4&useUnicode=true
spring.datasource.username=${DB_USERNAME}
spring.datasource.password=${DB_PASSWORD}
spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver

# JPA Configuration
spring.jpa.hibernate.ddl-auto=validate
spring.jpa.show-sql=false
spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.MySQL8Dialect

# Security Configuration
app.api-key=${API_KEY}

# CORS Configuration
cors.allowed.origins=${ALLOWED_ORIGINS:http://localhost:8080}

# Logging
logging.level.com.semscan.backend=WARN
logging.level.org.springframework.web=WARN
logging.level.org.hibernate.SQL=ERROR

# Management endpoints
management.endpoints.web.exposure.include=health,info
management.endpoint.health.show-details=never
```

---

## 📊 **Container Specifications**

### **Resource Requirements**
- **CPU**: 0.5-1 core
- **Memory**: 256MB-512MB
- **Storage**: 100MB-500MB
- **Network**: Port 8080 (HTTP)

### **Health Checks**
- **Endpoint**: `/actuator/health`
- **Interval**: 30 seconds
- **Timeout**: 3 seconds
- **Retries**: 3

### **Environment Variables**
- `SPRING_DATASOURCE_URL` - Database connection URL
- `SPRING_DATASOURCE_USERNAME` - Database username
- `SPRING_DATASOURCE_PASSWORD` - Database password
- `SPRING_PROFILES_ACTIVE` - Active Spring profile
- `APP_API_KEY` - API key for authentication

---

## 🔒 **Security Considerations**

### **Container Security**
- Use non-root user in container
- Scan images for vulnerabilities
- Keep base images updated
- Use secrets management for sensitive data

### **Network Security**
- Use internal networks for container communication
- Expose only necessary ports
- Implement proper CORS policies
- Use HTTPS in production

### **Data Security**
- Encrypt database connections
- Use strong passwords
- Implement proper API key management
- Regular security updates

---

## 📈 **Monitoring and Logging**

### **Logging Configuration**
```properties
# Logging to stdout for container logs
logging.pattern.console=%d{yyyy-MM-dd HH:mm:ss} - %msg%n
logging.level.com.semscan.backend=INFO
logging.level.org.springframework.web=INFO
```

### **Health Monitoring**
- Spring Boot Actuator health endpoints
- Docker health checks
- External monitoring tools (Prometheus, Grafana)

### **Log Aggregation**
- Docker logs
- ELK Stack (Elasticsearch, Logstash, Kibana)
- Cloud logging services

---

## 🚀 **Deployment Strategies**

### **Blue-Green Deployment**
1. Deploy new version to green environment
2. Test new version
3. Switch traffic to green environment
4. Keep blue environment as rollback option

### **Rolling Updates**
1. Update containers one by one
2. Maintain service availability
3. Automatic rollback on failure

### **Canary Deployment**
1. Deploy to small percentage of traffic
2. Monitor metrics and errors
3. Gradually increase traffic
4. Full deployment or rollback

---

## ✅ **Containerization Checklist**

### **Pre-Containerization**
- [ ] Backend application is complete and tested
- [ ] All dependencies are properly configured
- [ ] Database schema is ready
- [ ] Environment configurations are prepared

### **Containerization Setup**
- [ ] Dockerfile is created and tested
- [ ] Docker Compose files are configured
- [ ] Environment variables are defined
- [ ] Health checks are implemented
- [ ] Logging is configured

### **Testing**
- [ ] Container builds successfully
- [ ] Application starts correctly
- [ ] Database connection works
- [ ] API endpoints are accessible
- [ ] Health checks pass

### **Production Readiness**
- [ ] Security configurations are applied
- [ ] Resource limits are set
- [ ] Monitoring is configured
- [ ] Backup strategy is in place
- [ ] Documentation is complete

---

**Status**: Ready for containerization implementation! 🐳

**Next Steps**: 
1. Create new backend project
2. Copy all files from current project
3. Implement containerization files
4. Test containerized application
5. Deploy to production environment
