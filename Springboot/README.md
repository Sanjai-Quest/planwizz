# 🎓 PlanWizz - Spring Boot Backend

The Java 17 and Spring Boot 3 version of the PlanWizz Intelligent Timetable Generator backend, built to generate clash-free student timetables from PDF enrollment data using Constraint Satisfaction Problem (CSP) solver algorithms.

## ✨ Key Features
- 📄 **Apache PDFBox Parsing**: Extracts course slots and details dynamically from enrollment PDF uploads.
- 🧠 **CSP Solver Engine**: Re-implemented backtracking scheduling solver in Java 17 to find collision-free options.
- 🛠 **Spring Boot REST API**: Highly optimized, enterprise-ready endpoints supporting health checks, PDF upload, and compatibility checks.
- 🐳 **Docker-Ready**: Multi-stage Docker build config for cloud deployment.

## 🚦 Getting Started

### Prerequisites
- **Java JDK 17** or higher
- **Maven 3.8+**

### Running the Backend Locally

1. Navigate to this directory:
   ```bash
   cd Springboot
   ```
2. Build the application:
   ```bash
   mvn clean package
   ```
3. Run the application:
   ```bash
   mvn spring-boot:run
   ```
> The API will start and listen on port `8000`: `http://localhost:8000`

## 🧪 Running Integration Tests
To execute all REST endpoint and solver integration tests:
```bash
mvn test
```

## 🐳 Docker Build
To build and run the application locally inside a Docker container:
```bash
# Build the Docker image
docker build -t planwizz-backend .

# Run the container
docker run -p 8000:8000 planwizz-backend
```
