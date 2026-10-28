FROM node:22-alpine AS build
WORKDIR /workspace
COPY package.json package-lock.json ./
RUN npm ci --no-audit --no-fund
COPY index.html vite.config.js ./
COPY src ./src
# La SPA se publica en /inventary/ y llama a la API por el mismo origen; nginx reenvía /inventary/api/ al backend.
ARG VITE_BASE_PATH=/inventary/
ARG VITE_API_BASE_URL=/inventary/api/v1
ENV VITE_BASE_PATH=${VITE_BASE_PATH}
ENV VITE_API_BASE_URL=${VITE_API_BASE_URL}
RUN npm test && npm run build

FROM nginx:1.27-alpine
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY nginx-security-headers.conf /etc/nginx/security-headers.conf
COPY --from=build /workspace/dist /usr/share/nginx/html/inventary
EXPOSE 80
