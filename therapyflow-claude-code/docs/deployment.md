# Deployment Guide

## Phase 1: Railway.app (Free — Development & Beta)

### Why Railway?
- Deploy with `git push` — zero config
- Free tier: enough for development + early users
- PostgreSQL included
- Auto-deploys from GitHub main branch

### Setup Steps

1. **Create account** at railway.app
2. **New Project → Deploy from GitHub repo**
3. **Add PostgreSQL** service (click + → Database → PostgreSQL)
4. **Set environment variables** in Railway dashboard:
```
DATABASE_URL=${{Postgres.DATABASE_URL}}  ← Railway auto-links
JWT_SECRET=generate-with: openssl rand -hex 32
JWT_ISSUER=therapyflow.io
GOOGLE_CLIENT_ID=from-google-cloud-console
GOOGLE_CLIENT_SECRET=from-google-cloud-console
```

5. **Add Procfile** or configure start command:
```
web: java -jar build/libs/PayrollApp.jar
```

6. **Add railway.toml**:
```toml
[build]
builder = "NIXPACKS"

[deploy]
startCommand = "java -jar build/libs/PayrollApp.jar"
healthcheckPath = "/health"
healthcheckTimeout = 300
```

7. **Deploy**: push to main → Railway auto-deploys

### Railway Limitations (Free Tier)
- 500 hours/month execution time
- 1GB RAM
- Enough for: development, testing, first 10-20 paying customers

---

## Phase 2: Hetzner VPS (Production — when paying customers arrive)

### Why Hetzner?
- €4.5/month CX22: 2 vCPU, 4GB RAM, 40GB SSD
- EU-based (Frankfurt) → GDPR compliant
- Handles 500+ workspaces easily
- 10x cheaper than AWS for same specs

### Server Setup (one-time)

```bash
# 1. Create server on hetzner.com
# Choose: Ubuntu 24.04, CX22, Frankfurt

# 2. Initial server setup
ssh root@YOUR_SERVER_IP
apt update && apt upgrade -y
apt install -y docker.io docker-compose-v2 ufw

# 3. Firewall
ufw allow ssh
ufw allow 80
ufw allow 443
ufw enable

# 4. Create app user
useradd -m -s /bin/bash therapyflow
usermod -aG docker therapyflow
```

### Docker Compose Setup

```yaml
# docker-compose.yml
version: '3.8'

services:
  app:
    image: ghcr.io/YOUR_ORG/payrollapp:latest
    restart: unless-stopped
    environment:
      DATABASE_URL: jdbc:postgresql://db:5432/therapyflow
      DATABASE_USER: ${DB_USER}
      DATABASE_PASSWORD: ${DB_PASSWORD}
      JWT_SECRET: ${JWT_SECRET}
      JWT_ISSUER: therapyflow.io
      GOOGLE_CLIENT_ID: ${GOOGLE_CLIENT_ID}
      GOOGLE_CLIENT_SECRET: ${GOOGLE_CLIENT_SECRET}
    ports:
      - "8080:8080"
    depends_on:
      db:
        condition: service_healthy

  db:
    image: postgres:16-alpine
    restart: unless-stopped
    environment:
      POSTGRES_DB: therapyflow
      POSTGRES_USER: ${DB_USER}
      POSTGRES_PASSWORD: ${DB_PASSWORD}
    volumes:
      - postgres_data:/var/lib/postgresql/data
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U ${DB_USER}"]
      interval: 10s
      timeout: 5s
      retries: 5

volumes:
  postgres_data:
```

### GitHub Actions CI/CD

```yaml
# .github/workflows/deploy.yml
name: Deploy to Hetzner

on:
  push:
    branches: [main]

jobs:
  test:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          java-version: '21'
          distribution: 'corretto'
      - run: ./gradlew test

  deploy:
    needs: test
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      
      - name: Build JAR
        run: ./gradlew shadowJar
      
      - name: Build & Push Docker image
        run: |
          docker build -t ghcr.io/${{ github.repository }}:latest .
          docker push ghcr.io/${{ github.repository }}:latest
      
      - name: Deploy to Hetzner
        uses: appleboy/ssh-action@v1
        with:
          host: ${{ secrets.HETZNER_IP }}
          username: therapyflow
          key: ${{ secrets.HETZNER_SSH_KEY }}
          script: |
            cd /home/therapyflow/app
            docker compose pull
            docker compose up -d --no-deps app
```

### Cloudflare Setup (always free)

1. Add domain to Cloudflare
2. Create A record: `api.therapyflow.io → YOUR_HETZNER_IP`
3. Enable Proxy (orange cloud) → hides server IP
4. SSL/TLS → Full (strict)
5. Done — HTTPS automatic

## Dockerfile

```dockerfile
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app
COPY build/libs/PayrollApp-all.jar app.jar

EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=10s \
  CMD wget -q -O- http://localhost:8080/health || exit 1

ENTRYPOINT ["java", "-Xmx512m", "-jar", "app.jar"]
```

## Migration Path: Railway → Hetzner

1. Export PostgreSQL from Railway: `pg_dump $RAILWAY_DB_URL > backup.sql`
2. Import to Hetzner: `psql $HETZNER_DB_URL < backup.sql`
3. Update Cloudflare DNS to point to Hetzner
4. Test, then cancel Railway plan
