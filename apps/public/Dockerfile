FROM node:24-alpine AS build
WORKDIR /app
COPY package*.json ./
RUN npm ci --no-audit --no-fund
COPY . .
RUN npm run build
FROM node:24-alpine AS renderer
WORKDIR /app
COPY --from=build /app/dist/public /app/dist/public
USER node
EXPOSE 4000
CMD ["node", "dist/public/server/server.mjs"]
FROM nginx:1.28-alpine AS web
COPY --from=build /app/dist/public/browser /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf
EXPOSE 80
