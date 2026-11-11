# ShopSphere frontend — Vite build served by nginx (Java 21 build not needed)
# Build context: frontend/  ->  docker build -t shopsphere-frontend .
FROM node:22-alpine AS build
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci
COPY . .
# API calls are reverse-proxied by nginx to the backend service
RUN npm run build

FROM nginx:1.27-alpine
COPY --from=build /app/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf
EXPOSE 80
HEALTHCHECK --interval=30s --timeout=3s \
  CMD wget -qO- http://localhost/ | grep -q html || exit 1
