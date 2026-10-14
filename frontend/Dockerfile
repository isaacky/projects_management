# syntax=docker/dockerfile:1

# ---- build ----
FROM node:22-alpine AS build
WORKDIR /app
COPY package.json package-lock.json* ./
RUN if [ -f package-lock.json ]; then npm ci; else npm install; fi
COPY . .
# Development builds only: production gets the tenant from the user's token.
ARG VITE_DEV_TENANT_ID=""
ENV VITE_DEV_TENANT_ID=${VITE_DEV_TENANT_ID}
RUN npm run build

# ---- serve ----
FROM nginx:1.27-alpine
# Filled in from the environment at start (envsubst), into /etc/nginx/conf.d/default.conf.
COPY nginx.conf.template /etc/nginx/templates/default.conf.template
# Keycloak realm the app signs in with (the issuer browsers see), e.g. https://pmp.example/auth/realms/pmp.
ENV PMP_OIDC_ISSUER_URI="" \
    PMP_OIDC_CLIENT_ID="pmp-web" \
    PMP_CSP_EXTRA_CONNECT=""
COPY --from=build /app/dist /usr/share/nginx/html
EXPOSE 80
