# Guía de despliegue

Frontend en Vercel; API, Keycloak y bases en una máquina gratuita de Oracle Cloud.

## 1. Cuenta de Oracle Cloud

1. Entra a <https://signup.oraclecloud.com> y crea la cuenta.
2. **Región principal (home region):** elige **Colombia Central (Bogotá)**. No se puede cambiar después.
3. Pide tarjeta solo para verificar; no cobra.
4. Cuando la cuenta esté activa: menú ☰ → **Billing & Cost Management** → **Upgrade and Manage Payment** → **Upgrade to Pay As You Go**.
   - Los recursos *Always Free* siguen siendo gratis y Oracle no recupera la máquina por inactividad.
   - Opcional: ☰ → **Billing** → **Budgets** → presupuesto de 1 USD con alerta a tu correo.

## 2. Máquina virtual

1. ☰ → **Compute** → **Instances** → **Create instance**.
2. **Name:** `opspilot`.
3. **Image:** *Change image* → **Canonical Ubuntu 24.04** (la versión normal, no *Minimal*).
4. **Shape:** *Change shape* → **Ampere** → `VM.Standard.A1.Flex` → **2 OCPU** y **12 GB**.
   - Si sale *Out of capacity*, prueba otro *Availability domain* o reintenta más tarde.
5. **Networking:** deja *Create new virtual cloud network* y marca **Assign a public IPv4 address**.
6. **Add SSH keys:** *Generate a key pair for me* → **Save private key** (guárdala como `~/.ssh/opspilot.key`).
7. **Create.** Cuando quede *Running*, copia la **Public IP address**.
8. En tu Mac:
   ```bash
   chmod 600 ~/.ssh/opspilot.key
   ```

## 3. Abrir los puertos 80 y 443 en Oracle

1. En la instancia → **Subnet** (enlace) → **Security Lists** → *Default Security List*.
2. **Add Ingress Rules** dos veces:
   - Source CIDR `0.0.0.0/0`, IP Protocol **TCP**, Destination port **80**.
   - Source CIDR `0.0.0.0/0`, IP Protocol **TCP**, Destination port **443**.

## 4. DNS en Cloudflare (fuken404.dev)

Tres registros, todos con la **nube gris (DNS only)**:

| Tipo | Nombre | Valor |
|---|---|---|
| A | `api.opspilot` | IP pública de Oracle |
| A | `auth.opspilot` | IP pública de Oracle |
| CNAME | `opspilot` | el que te dé Vercel en el paso 7 |

## 5. Preparar el servidor

```bash
scp -i ~/.ssh/opspilot.key deploy/setup-server.sh ubuntu@IP_PUBLICA:~
ssh -i ~/.ssh/opspilot.key ubuntu@IP_PUBLICA "bash setup-server.sh"
```

Te pide un correo para los avisos de Let's Encrypt. Los secretos se generan solos en `/opt/opspilot/.env`.

## 6. GitHub (despliegue automático)

1. Llave solo para despliegues, en tu Mac:
   ```bash
   ssh-keygen -t ed25519 -N "" -C "github-deploy" -f ~/.ssh/opspilot-deploy
   ```
   ```bash
   ssh -i ~/.ssh/opspilot.key ubuntu@IP_PUBLICA "cat >> ~/.ssh/authorized_keys" < ~/.ssh/opspilot-deploy.pub
   ```
   ```bash
   ssh-keyscan -t ed25519 IP_PUBLICA
   ```
2. Repositorio → **Settings → Secrets and variables → Actions**:
   - **Secrets:** `DEPLOY_SSH_KEY` = contenido de `~/.ssh/opspilot-deploy` · `DEPLOY_KNOWN_HOSTS` = la línea que imprimió `ssh-keyscan`.
   - **Variables:** `DEPLOY_HOST` = IP pública.
3. **Actions → Despliegue → Run workflow.** Al terminar, <https://api.opspilot.fuken404.dev/actuator/health> responde `UP`.
4. Las imágenes quedan privadas en GitHub Packages; el servidor las descarga con el token del propio despliegue.

## 7. Vercel (frontend)

1. **Add New → Project** → importa `fuken404/OpsPilot`.
2. **Root Directory:** `frontend`. El resto lo toma de `frontend/vercel.json`.
3. **Deploy.** Luego **Settings → Domains** → agrega `opspilot.fuken404.dev` y copia el CNAME a Cloudflare (paso 4).
4. **Settings → General → Node.js Version:** 24.x.

## Mantenimiento

- **Ver logs:** `ssh -i ~/.ssh/opspilot.key ubuntu@IP_PUBLICA "cd /opt/opspilot && docker compose -f compose.prod.yaml logs -f api"`
- **Consola de Keycloak y correos de la demo (túnel SSH):**
  ```bash
  ssh -i ~/.ssh/opspilot.key -L 8080:localhost:8080 -L 8025:localhost:8025 ubuntu@IP_PUBLICA
  ```
  Con la sesión abierta: <http://localhost:8080/admin> (usuario `admin`, contraseña en `/opt/opspilot/.env`) y <http://localhost:8025> (Mailpit).
- **Copias:** en el volumen `backups`; para subirlas a Object Storage, llena las variables `S3_*` del `.env`.
