# Production Upload Proxy

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
