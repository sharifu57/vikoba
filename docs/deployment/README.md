# Production Upload Proxy

For the web system-administrator workspace and first-account provisioning, see
[System 360 setup](system-admin.md).

The share-payment proof endpoint accepts multipart requests up to 20 MB. Spring Boot is configured with:

- `spring.servlet.multipart.max-file-size=20MB`
- `spring.servlet.multipart.max-request-size=20MB`

Nginx must allow the request before proxying it to Spring. Without `client_max_body_size`, Nginx may return `413 Request Entity Too Large` before the application receives the request.

Install the server block from `nginx-api.conf` in the active Nginx site for `api.vikoba360.com`, then validate and reload Nginx:

```bash
sudo nginx -t
sudo systemctl reload nginx
```

If the production proxy is managed by a hosting platform or load balancer, set its request-body limit to at least `20 MB` there instead.

## Deploy the backend over SSH

[`../../scripts/deploy-backend.sh`](../../scripts/deploy-backend.sh) builds the backend JAR (skipping tests), builds the Docker image for `linux/amd64`, streams the image to the server, and recreates the existing Docker Compose service. It defaults to the server layout currently used by VIKOBA360: Compose directory `/opt/apps/vikoba`, service `backend`, and image `vikoba-backend:1.0.0`.

Configure SSH key authentication from your Mac, then run from the backend repository root:

```bash
DEPLOY_HOST=185.183.55.100 ./scripts/deploy-backend.sh
```

Override `DEPLOY_USER`, `COMPOSE_DIR`, `COMPOSE_SERVICE`, `DEPLOY_IMAGE`, or `DEPLOY_PLATFORM` if your server setup changes. The Compose service must reference the same image tag used for `DEPLOY_IMAGE`. Keep database credentials, Kafka configuration, and `CORS_ALLOWED_ORIGINS` in the server's Compose environment or environment file, not in this script or the image. If `CORS_ALLOWED_ORIGINS` is set, include `https://app.vikoba360.com` because it overrides the application default.

The script does not change the server's Compose file, proxy, environment, or database. After deployment, review the printed service status and check logs with `docker compose logs --tail=100 backend` from `/opt/apps/vikoba`.
