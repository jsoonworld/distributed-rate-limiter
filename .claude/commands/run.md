---
description: Run the application locally
---

# Run Application

First, ensure Docker containers are running:
```bash
docker compose -f docker/docker-compose.yml up -d
```

Then start the Spring Boot application:
```bash
./gradlew bootRun
```

The application will start on the default port (usually 8080).

To stop: Press Ctrl+C
