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
- Exchange a GitHub OIDC token for a short-lived AWS role session.
- Send the tested SHA to the `OaoDeployBackend` Systems Manager document.
- The document runs as `ubuntu` in `/home/ubuntu/workdir/lc_back`.
- Build the app, require a non-empty SQL backup, deploy and check the public health endpoint.
- Public uploads and private verification documents use separate named volumes.

Repository Actions variables (not secrets):

```properties
AWS_REGION=ap-northeast-2
EC2_INSTANCE_ID=i-004dfd68aefd00c4e
SSM_DEPLOY_DOCUMENT=OaoDeployBackend
AWS_DEPLOY_ROLE_ARN=arn:aws:iam::264284393033:role/oao-github-deploy-role
```

The IAM trust policy accepts only `repo:dev-morph/lc_back:ref:refs/heads/main`.
The deployment policy allows SendCommand only on the named document and instance;
GetCommandInvocation is used to report the result. The existing EC2 role needs
AmazonSSMManagedInstanceCore in addition to its email permissions.

Review `deploy/configure-ssm-oidc.py` and run it in authenticated AWS CloudShell
with no flags to preview or `--apply` to configure the approved resources.
No private SSH key or long-lived AWS credential is uploaded to GitHub. SSH ingress
may remain restricted to the operator's IP. SSM Agent must be online.

First-time EC2 setup remains as above. The deployment script refuses tracked
server edits and stale commits, requires a database backup, and retains backups
under `backups/`. A failed database migration requires investigation before retry;
do not automatically restore a backup over writes made after deployment.

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

Amazon SES uses `AWS_SES_REGION=ap-northeast-2` and
`EMAIL_FROM=LoveCatcher <hello@oao365.com>`. Attach the SES IAM role to EC2 and
allow IMDSv2 access from the container (response hop limit 2). No static AWS key
is required. Other optional integrations and administrator bootstrap variables
are listed in `.env.prod.example` and passed through by Compose.

Backups and actual `.env` files are ignored by Git and Docker build context.
Before retrying a failed deployment, check the Actions logs and server app logs;
do not restore a pre-migration database over live writes automatically.

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
