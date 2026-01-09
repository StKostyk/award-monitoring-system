# =============================================================================
# Award Monitoring System - Frontend Dockerfile
# =============================================================================
# Multi-stage build for Angular application with nginx
# Final image: nginx Alpine (~25MB)
# =============================================================================

# ---------------------------------------------------------------------------
# Stage 1: Build
# ---------------------------------------------------------------------------
FROM node:20-alpine AS builder

RUN apk add --no-cache python3 make g++ git

WORKDIR /build

ENV NODE_OPTIONS="--max_old_space_size=8192"

# Cache dependencies
COPY package*.json ./
RUN npm ci --legacy-peer-deps --prefer-offline --no-audit

# Build application
COPY . .
RUN npx ng build \
    --configuration=production \
    --optimization=true \
    --aot=true \
    --source-map=false

# ---------------------------------------------------------------------------
# Stage 2: Runtime
# ---------------------------------------------------------------------------
FROM nginx:alpine

RUN apk add --no-cache curl

# Security: Remove default content
RUN rm -rf /usr/share/nginx/html/*

# Copy built application
COPY --from=builder /build/dist/frontend/browser /usr/share/nginx/html

# Custom nginx configuration
COPY nginx.conf /etc/nginx/conf.d/default.conf

# Security: Run as non-root (nginx user = 101)
RUN chown -R nginx:nginx /usr/share/nginx/html && \
    chown -R nginx:nginx /var/cache/nginx && \
    chown -R nginx:nginx /var/log/nginx && \
    touch /var/run/nginx.pid && \
    chown -R nginx:nginx /var/run/nginx.pid

USER nginx

EXPOSE 80

# Health check
HEALTHCHECK --interval=30s --timeout=5s --start-period=10s --retries=3 \
    CMD curl -f http://localhost/health || exit 1

CMD ["nginx", "-g", "daemon off;"]

