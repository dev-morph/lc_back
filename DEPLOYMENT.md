# OAO Backend Deployment

## Production Domain Plan

- Frontend: `https://www.oao365.com`
- Frontend apex redirect: `https://oao365.com`
- Backend API: `https://api.oao365.com`

## EC2 Docker Compose MVP

For the first MVP, backend and MariaDB can run on one EC2 instance:

```text
Cloudflare DNS
api.oao365.com -> EC2 public IPv4

EC2 Docker Compose
Caddy :80/:443 -> app:8080
Spring Boot app -> mariadb:3306
MariaDB volume -> Docker named volume
```

Open EC2 security group inbound rules:

```text
22/tcp   your IP only
80/tcp   0.0.0.0/0
443/tcp  0.0.0.0/0
```

Do not expose MariaDB `3306` publicly.

### EC2 Commands

Install Docker and Docker Compose plugin on EC2, then:

```bash
git clone <repo-url>
cd <repo>/oao_back
cp .env.prod.example .env.prod
vi .env.prod
docker compose --env-file .env.prod -f compose.prod.yaml up -d --build
```

Check logs:

```bash
docker compose --env-file .env.prod -f compose.prod.yaml logs -f app
docker compose --env-file .env.prod -f compose.prod.yaml logs -f caddy
```

Health check:

```bash
curl https://api.oao365.com/api/health
```

## GitHub Actions CI/CD

This repo includes:

```text
.github/workflows/deploy-ec2.yml
deploy/remote-deploy.sh
```

GitHub Actions behavior:

- On `main` push, run `./gradlew test`.
- If tests pass, SSH into EC2.
- On EC2, fetch latest `main`, rebuild Docker images, and run Compose.
- If MariaDB is already running, create a best-effort SQL backup in `backups/`.

Add these GitHub repository secrets:

```text
EC2_HOST=<EC2 public IP or api.oao365.com>
EC2_USER=ubuntu
EC2_SSH_KEY=<private key contents>
EC2_SSH_PORT=22
EC2_APP_DIR=/home/ubuntu/oao_back
```

Initial EC2 setup still needs to be done once:

```bash
git clone <repo-url> /home/ubuntu/oao_back
cd /home/ubuntu/oao_back
cp .env.prod.example .env.prod
vi .env.prod
docker compose --env-file .env.prod -f compose.prod.yaml up -d --build
```

After that, pushes to `main` deploy automatically.

## Required Environment Variables

```properties
SERVER_PORT=8080

KAKAO_CLIENT_ID=...
KAKAO_CLIENT_SECRET=...
OAUTH_SUCCESS_REDIRECT_URL=https://www.oao365.com/oauth/success

CORS_ALLOWED_ORIGINS=https://oao365.com,https://www.oao365.com

DB_URL=jdbc:mariadb://mariadb:3306/oao
DB_USERNAME=...
DB_PASSWORD=...
SPRING_DOCKER_COMPOSE_ENABLED=false

VERIFICATION_MESSAGE_PROVIDER=solapi-sms
SOLAPI_API_KEY=...
SOLAPI_API_SECRET=...
SOLAPI_FROM_NUMBER=...
VERIFICATION_DEV_CODE_RESPONSE_ENABLED=false

OAO_DEV_TOOLS_ENABLED=false
OAO_DEV_TOOLS_SECRET=
```

## Kakao Developers

Add the production Kakao Login redirect URI:

```text
https://api.oao365.com/login/oauth2/code/kakao
```

Add web platform domains:

```text
https://oao365.com
https://www.oao365.com
```

## DNS

For EC2 Docker Compose, connect:

```text
api.oao365.com -> EC2 public IPv4
```

In Cloudflare, keep the record as `DNS only` at first until HTTPS is confirmed.
