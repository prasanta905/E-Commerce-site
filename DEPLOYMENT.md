# ShopSphere — Free Live Deployment Guide (2026)

Goal: put the **full stack** (Spring Boot API + MySQL 8 + React SPA) online for **$0/month**, with HTTPS and no surprise bills.

---

## TL;DR — recommended setup

One always-free Linux VM (Oracle Cloud) running the repo's production Docker Compose stack:

```
Internet
   │  HTTPS (443)
   ▼
 Caddy  ──►  frontend (nginx + React build)  ──►  backend (Spring Boot)  ──►  MySQL 8
   │                │                                  │
   └── auto TLS     └── /api, /uploads, /swagger-ui proxied
```

Why this shape:

- **Single origin** → the `SS_REFRESH` HttpOnly cookie and CORS work with zero cross-site hacks.
- **Real MySQL 8** → the Flyway migrations run unchanged.
- **Always on** → no cold starts after 15 minutes of inactivity.
- **Free** → Oracle Always Free resources do not expire.

Cost: **$0**. Oracle asks for a credit/debit card for identity verification (no charge for Always Free resources). A free DuckDNS subdomain provides the HTTPS hostname.

Repo files used: `docker-compose.prod.yml`, `Caddyfile`, `backend/Dockerfile`, `frontend/Dockerfile`, `frontend/nginx.conf`, `.env.example`.

> Honest note: Docker is **not installed** on the development machine, so these production files could not be executed locally. The CI pipeline builds the backend and frontend on every push, and the compose stack mirrors the verified dev stack — but the first cloud boot is the real test. If anything fails, `docker compose logs` will show exactly where.

---

## 1. What is actually free in 2026

| Option | Truly free? | What you get | Caveats |
|---|---|---|---|
| **Oracle Cloud Always Free** | ✅ forever | ARM VM: **2 OCPU / 12 GB** (Ampere A1), 200 GB block storage, 10 TB egress/month; plus a **50 GB MySQL HeatWave** instance; 2 micro AMD VMs | Card for verification; resources only free in your **home region**; idle instances may be reclaimed |
| **Render** free compute | ✅ with limits | Web service: **512 MB RAM**, free TLS, 5 GB bandwidth/month | **Sleeps after ~15 min idle** (cold start ~30–60 s); **no MySQL** (free Postgres only); ephemeral disk |
| **Aiven** free MySQL | ❌ retired | — | Free plan is **PostgreSQL only** now and powers off when idle |
| **Railway** | ❌ | ~$5 trial credit | Then usage-based billing (~$5/month minimum) |
| **Fly.io / Heroku** | ❌ | — | No meaningful free tier for new accounts |
| **Vercel / Cloudflare Pages** | ✅ | Static frontend hosting, global CDN | Frontend only — needs a backend host |
| **TiDB Cloud Serverless** | ✅ (limits apply) | MySQL-compatible serverless DB | Not identical to MySQL; verify Flyway compatibility before relying on it |

Oracle's idle policy (documented): an Always Free instance may be reclaimed if, over 7 days, **95th-percentile CPU < 20%**, **network < 20%**, and (for A1) **memory < 20%**. A demo with real visitors is fine; a completely untouched VM is at risk. Visiting the site / a free uptime monitor helps.

---

## 2. Path A — Oracle Cloud Always Free (recommended)

### 2.1 Create the account and VM

1. Sign up at <https://signup.oraclecloud.com>. Choose a **home region** close to your users (e.g. `ap-mumbai-1` for India). Always Free resources are only free in the home region.
2. Console → **Compute → Instances → Create instance**:
   - **Image:** Canonical Ubuntu **24.04**
   - **Shape:** Ampere → `VM.Standard.A1.Flex`
   - **OCPUs:** `2`, **Memory:** `12 GB` — this fits the monthly Always Free allowance for 24/7 use. (4 OCPU/24 GB exists, but it exhausts the monthly OCPU-hours in about half a month.)
   - **Boot volume:** 50 GB (default; 200 GB total free)
   - **Networking:** assign a **public IPv4**
   - **SSH keys:** upload your public key or download the generated private key — you need it to log in.
3. If you get **"out of host capacity"**: try another availability domain, retry later, or upgrade the account to Pay-As-You-Go (Always Free resources stay free; only usage above the limits is billed).

### 2.2 Open ports 80 and 443

**OCI Console → Networking → Virtual Cloud Networks → your VCN → Security Lists → Default Security List → Add Ingress Rules:**

| Source | Protocol | Destination port |
|---|---|---|
| `0.0.0.0/0` | TCP | `80` |
| `0.0.0.0/0` | TCP | `443` |

Keep SSH (22) restricted to your own IP if possible.

Ubuntu images on OCI may also carry host firewall rules. If the site is unreachable after the next steps, run:

```bash
sudo iptables -I INPUT 6 -m state --state NEW -p tcp --dport 80 -j ACCEPT
sudo iptables -I INPUT 6 -m state --state NEW -p tcp --dport 443 -j ACCEPT
sudo apt-get install -y iptables-persistent
sudo netfilter-persistent save
```

(or `sudo ufw allow 80,443/tcp` if `ufw status` shows it active).

### 2.3 Free DNS with DuckDNS

1. Go to <https://www.duckdns.org> → sign in with GitHub/Google.
2. Create a subdomain, e.g. `shopsphere-prasanta`.
3. Set its **current IP** to your VM's public IPv4.
4. Result: `shopsphere-prasanta.duckdns.org` → your VM.

Caddy will obtain a Let's Encrypt certificate for this name automatically. If the VM's public IP changes later (e.g. instance recreated), update DuckDNS.

### 2.4 Install Docker

SSH into the VM (`ssh ubuntu@YOUR_VM_IP`), then:

```bash
curl -fsSL https://get.docker.com | sudo sh
sudo usermod -aG docker $USER
newgrp docker
docker compose version   # must print v2.x
```

### 2.5 Get the code (private repo → deploy key)

On the VM:

```bash
ssh-keygen -t ed25519 -C "shopsphere-vm" -f ~/.ssh/shopsphere_deploy -N ""
cat ~/.ssh/shopsphere_deploy.pub
```

Copy that public key into **GitHub → repo → Settings → Deploy keys → Add deploy key** (leave "Allow write access" unchecked). Then:

```bash
cat >> ~/.ssh/config <<'EOF'
Host github.com
  HostName github.com
  User git
  IdentityFile ~/.ssh/shopsphere_deploy
  IdentitiesOnly yes
EOF
chmod 600 ~/.ssh/config

git clone git@github.com:prasanta905/E-Commerce-site.git shopsphere
cd shopsphere
```

> Prefer HTTPS? Make the repo public and `git clone https://github.com/prasanta905/E-Commerce-site.git`. A deploy key keeps it private.

### 2.6 Configure secrets

```bash
cat > .env <<EOF
MYSQL_ROOT_PASSWORD=$(openssl rand -base64 24)
MYSQL_USER=shopsphere_user
MYSQL_PASSWORD=$(openssl rand -base64 24)
JWT_SECRET_KEY=$(openssl rand -base64 64)
JWT_COOKIE_SECURE=true
CORS_ALLOWED_ORIGINS=https://YOUR_NAME.duckdns.org
PUBLIC_DOMAIN=YOUR_NAME.duckdns.org
RAZORPAY_ENABLED=false
YOUPI_ENABLED=false
MAIL_CONSOLE_FALLBACK=true
MAIL_FROM=noreply@shopsphere.local
EOF
chmod 600 .env
```

Replace `YOUR_NAME` with your DuckDNS subdomain. Never commit `.env` (it is gitignored).

### 2.7 Launch

```bash
docker compose --env-file .env -f docker-compose.prod.yml up -d --build
```

First build takes roughly 10–20 minutes on 2 ARM cores (Maven + npm downloads). Then:

```bash
docker compose -f docker-compose.prod.yml ps
docker compose -f docker-compose.prod.yml logs -f caddy
```

Open **https://YOUR_NAME.duckdns.org** — the storefront should load with a valid padlock. Flyway creates the schema and seeds the demo data on first boot.

Useful URLs:

| URL | What |
|---|---|
| `https://YOUR_NAME.duckdns.org` | Storefront |
| `https://YOUR_NAME.duckdns.org/swagger-ui.html` | Swagger UI (proxied by nginx) |
| `https://YOUR_NAME.duckdns.org/actuator/health` | Health endpoint |

### 2.8 Secure it immediately

1. **Change the seeded admin password immediately.** Use the password from your own seed data — treat the repository default as public. UI: Profile → Change Password, or:
   ```bash
   curl -X POST https://YOUR_NAME.duckdns.org/api/v1/users/me/password \
     -H "Authorization: Bearer $ACCESS_TOKEN" \
     -H "Content-Type: application/json" \
     -d '{"currentPassword":"<seeded-admin-password>","newPassword":"<strong-new-password>"}'
   ```
   (This endpoint was fixed to persist correctly and now has integration tests.)
2. **Change or delete the seeded customer accounts** before sharing the URL.
3. **Optionally hide Swagger** — add to the backend environment in `docker-compose.prod.yml`:
   ```yaml
   SPRINGDOC_API_DOCS_ENABLED: "false"
   SPRINGDOC_SWAGGER_UI_ENABLED: "false"
   ```
4. Keep `.env` at `chmod 600`; never commit it.
5. Keep OCI port 22 limited to your IP; only 80/443 should be public.
6. The production compose publishes **only Caddy**; MySQL and the backend have no host ports.

### 2.9 Razorpay (optional)

Add test keys to `.env` and restart:

```env
RAZORPAY_ENABLED=true
RAZORPAY_KEY_ID=rzp_test_XXXXXXXX
RAZORPAY_KEY_SECRET=XXXXXXXXXXXXXXXX
RAZORPAY_WEBHOOK_SECRET=some-dev-secret
```

Webhook URL to register in the Razorpay dashboard:

```
https://YOUR_NAME.duckdns.org/api/v1/payments/webhook
```

Use test card `4111 1111 1111 1111`, any future expiry, CVV `123`. Without keys, online payment returns `503 RAZORPAY_NOT_CONFIGURED` and COD continues to work.

### 2.10 YouPI PG self-hosted UPI (optional, experimental)

**YouPI PG is experimental research software, not an NPCI/RBI-certified payment aggregator.** It has no bank API: a UPI session becomes `RECONCILED` only after an admin imports a bank-statement CSV and one line matches the receiving account, amount in paise and 12-digit UTR. SMS/Gmail observers are alerting inputs, never settlement proof. Read the upstream `youpi-pg` warnings before using real money.

YouPI is **not** part of `docker-compose.prod.yml`. Run it separately (on the VM host or a private host) and keep its merchant API port `6100` off the public internet; only the checkout portal and ShopSphere's webhook receiver need reachability.

1. **Start the gateway** from the sibling `youpi-pg` checkout (Node >= 24.22.1):
   ```bash
   cp .env.example .env
   # set:
   #   YOUPI_ALLOWED_RETURN_ORIGINS=https://YOUR_NAME.duckdns.org
   #   YOUPI_ALLOWED_ORIGINS=https://YOUR_NAME.duckdns.org
   #   ADMIN_API_TOKEN=<random 64 hex chars>
   npm install
   npm start
   ```
2. **Configure the receiving VPA and webhook** (`Authorization: Bearer $ADMIN_API_TOKEN`):
   ```bash
   curl -X POST http://127.0.0.1:6100/api/v1/admin/upi-settings \
     -H "Authorization: Bearer $ADMIN_API_TOKEN" \
     -H "Content-Type: application/json" \
     -d '{"upiId":"your-name@bank","payeeName":"ShopSphere"}'

   curl -X POST http://127.0.0.1:6100/api/v1/admin/merchant-settings \
     -H "Authorization: Bearer $ADMIN_API_TOKEN" \
     -H "Content-Type: application/json" \
     -d '{"webhookUrl":"https://YOUR_NAME.duckdns.org/api/v1/payments/webhook/youpi","webhookSecret":"<random-secret>"}'

   curl -X POST http://127.0.0.1:6100/api/v1/admin/merchant-key/rotate \
     -H "Authorization: Bearer $ADMIN_API_TOKEN"
   # copy the one-time merchantKey from the response
   ```
3. **Point ShopSphere at it** in `.env` and restart the backend:
   ```env
   YOUPI_ENABLED=true
   YOUPI_BASE_URL=http://host.docker.internal:6100
   YOUPI_MERCHANT_ID=<merchant id>
   YOUPI_MERCHANT_KEY=<rotated merchantKey>
   YOUPI_WEBHOOK_SECRET=<the same webhookSecret>
   YOUPI_RETURN_BASE_URL=https://YOUR_NAME.duckdns.org
   ```
   `docker-compose.prod.yml` already maps `host.docker.internal` to the Docker host.
4. **Verify**: place a UPI order, confirm the browser redirects to the YouPI checkout, and confirm the order stays `PENDING`. It must remain pending after an observer alert (`OBSERVED_SUCCESS`) and move forward only after the YouPI admin imports a matching statement line (`RECONCILED`). YouPI also requires a fresh SMS-observer heartbeat before creating new sessions outside its test mode.

Operational notes:
- Refunds are **manual bank transfers**, recorded in ShopSphere's admin refund ledger. YouPI has no refund API.
- `FAILED`/`EXPIRED`/`CANCELLED` webhooks do not cancel the ShopSphere order or release stock; the customer can retry the hosted checkout.
- YouPI webhook retries reuse `X-Gateway-Delivery-Id`; ShopSphere deduplicates them in `youpi_events`.
- A distinct reconciled delivery for an already-paid order is recorded as `REJECTED` (“manual refund review”); it never changes the order or the original UTR. Check `youpi_events` for this after a customer reports a double debit.

### 2.11 Updates, logs, backups

```bash
# update
git pull
docker compose --env-file .env -f docker-compose.prod.yml up -d --build

# logs
docker compose -f docker-compose.prod.yml logs -f backend

# database backup
docker compose -f docker-compose.prod.yml exec -T db \
  sh -c 'exec mysqldump -uroot -p"$MYSQL_ROOT_PASSWORD" shopsphere' \
  > "shopsphere-$(date +%F).sql"

# disk / memory
docker stats --no-stream
```

Flyway applies new migrations automatically on backend start.

### 2.12 Troubleshooting

| Symptom | Cause / fix |
|---|---|
| `out of host capacity` | Try another availability domain, retry, or upgrade to Pay-As-You-Go (Always Free stays free) |
| Site times out | OCI Security List missing 80/443, or host iptables/ufw blocking them (§2.2) |
| Caddy cannot get a certificate | DNS not pointing at the VM yet, or port 80 blocked. Check `docker compose logs caddy` |
| `502 Bad Gateway` | Backend still booting or crashed → `docker compose logs backend` |
| MySQL healthcheck fails | `.env` password changed after the volume was created. Either restore the old password or recreate the volume (`docker compose down -v` — **deletes data**) |
| Backend cannot connect to DB | Check `SPRING_DATASOURCE_*`, and that `db` is healthy |
| UPI button returns `YOUPI_NOT_CONFIGURED` | `YOUPI_ENABLED`, merchant id/key, webhook secret or return base URL is empty; set all `YOUPI_*` values and restart |
| UPI button returns `YOUPI_GATEWAY_ERROR` | YouPI is down, unreachable from the backend container (`YOUPI_BASE_URL`), or refusing sessions because no SMS observer heartbeat is fresh |
| UPI order stays PENDING after paying | Expected until YouPI imports a matching bank-statement line and emits `RECONCILED`; an observer alert alone is not settlement |

---

## 3. Path B — Render + Vercel (no server to manage)

This path is faster to start but has real limits. Use it only if you do not want a VM.

**Backend on Render (free web service, Docker):**

1. Render → New → **Web Service** → connect `prasanta905/E-Commerce-site` → Root directory `backend` → Docker → Free plan.
2. Environment variables:
   ```env
   SPRING_PROFILES_ACTIVE=docker
   SPRING_DATASOURCE_URL=jdbc:mysql://HOST:PORT/shopsphere?sslMode=REQUIRED&useUnicode=true&characterEncoding=utf8&serverTimezone=UTC
   SPRING_DATASOURCE_USERNAME=...
   SPRING_DATASOURCE_PASSWORD=...
   JWT_SECRET_KEY=<long random>
   JWT_COOKIE_SECURE=true
   CORS_ALLOWED_ORIGINS=https://YOUR-APP.vercel.app
   JAVA_TOOL_OPTIONS=-Xmx320m -XX:MaxMetaspaceSize=96m
   ```
3. Health check path: `/actuator/health`.

**Database:** Render offers free **PostgreSQL**, not MySQL. For MySQL you need a free MySQL-compatible provider — **TiDB Cloud Serverless** is the main $0 option in 2026; verify Flyway compatibility first. Aiven's free MySQL plan was retired.

**Frontend on Vercel (free):**

- Import the repo, root `frontend`, framework Vite, build `npm run build`, output `dist`.
- Add `frontend/vercel.json` so the browser sees a **single origin** (keeps the refresh cookie working):
  ```json
  {
    "rewrites": [
      { "source": "/api/:path*", "destination": "https://YOUR-BACKEND.onrender.com/api/:path*" },
      { "source": "/uploads/:path*", "destination": "https://YOUR-BACKEND.onrender.com/uploads/:path*" }
    ]
  }
  ```

**Caveats:**

- Free service **sleeps after ~15 minutes idle** → first request waits 30–60 s.
- 512 MB RAM is tight for Spring Boot; keep the JVM flags above.
- Free instances have **ephemeral disk** → uploaded product images disappear on redeploy.
- Build minutes: 500/month free on Render.
- TiDB is MySQL-compatible, not MySQL — migrations and SQL edge cases must be tested.

If any of these are deal-breakers, Path A avoids all of them for the same $0.

---

## 4. Cost summary

| Item | Cost |
|---|---|
| Oracle Always Free VM (2 OCPU / 12 GB, 200 GB storage) | **$0** |
| Oracle MySQL HeatWave Always Free (50 GB) — optional | **$0** |
| DuckDNS subdomain + Let's Encrypt certificate | **$0** |
| Outbound traffic (10 TB/month on OCI) | **$0** |
| Optional custom domain | ~$8–12 / year |
| Render + Vercel + TiDB (Path B) | **$0** with the limits above |

---

## 5. Files added for deployment

| File | Purpose |
|---|---|
| `docker-compose.prod.yml` | Production stack: no DB/backend host ports, restart policies, Caddy |
| `Caddyfile` | Automatic HTTPS for `PUBLIC_DOMAIN` |
| `frontend/nginx.conf` | SPA fallback + `/api`, `/uploads`, Swagger, health proxying |
| `.env.example` | Template with production variables, including the optional `YOUPI_*` UPI settings |
| `DEPLOYMENT.md` | This guide (including the experimental YouPI UPI section) |

Every push to `Main` runs CI (backend tests against MySQL 8 + frontend production build), so the commit you deploy is the commit that passed.
