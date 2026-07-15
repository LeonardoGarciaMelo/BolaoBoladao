# syntax=docker/dockerfile:1
FROM node:22-alpine AS build
WORKDIR /app
ENV CI=true
COPY package.json pnpm-lock.yaml pnpm-workspace.yaml ./
RUN corepack enable && pnpm install --frozen-lockfile
COPY . .
ARG PUBLIC_API_BASE_URL=http://localhost:8080/api
ENV PUBLIC_API_BASE_URL=${PUBLIC_API_BASE_URL}
ARG PUBLIC_PAYMENT_PROVIDER_URL=http://localhost:4000/api/v1
ENV PUBLIC_PAYMENT_PROVIDER_URL=${PUBLIC_PAYMENT_PROVIDER_URL}
RUN pnpm build

FROM nginx:1.27-alpine
COPY --from=build /app/dist /usr/share/nginx/html
EXPOSE 80
