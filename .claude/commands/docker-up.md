---
description: Start Docker containers (Redis, etc.)
---

Start the Docker containers:

```bash
docker compose -f docker/docker-compose.yml up -d
```

After starting, verify containers are running:
```bash
docker compose -f docker/docker-compose.yml ps
```

Report the status of all containers.
