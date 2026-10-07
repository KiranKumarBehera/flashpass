# ===================================================
# Stage 1: Build the React Application (Builder)
# ===================================================
FROM node:20-alpine AS builder

WORKDIR /app

# Copy package descriptors first to leverage Docker layer caching
COPY package*.json ./
RUN npm install

# Copy source code and build production bundle
COPY . .
RUN npm run build

# ===================================================
# Stage 2: Ultra-Lightweight Production Nginx Server
# ===================================================
FROM nginx:alpine

# Remove default Nginx website
RUN rm -rf /usr/share/nginx/html/*

# Copy compiled static assets from Stage 1 into Nginx HTML folder
COPY --from=builder /app/dist /usr/share/nginx/html

# Copy our custom Nginx config for SPA routing and Gzip compression
COPY nginx.conf /etc/nginx/conf.d/default.conf

# Expose HTTP port 80
EXPOSE 80

# Start Nginx in foreground
CMD ["nginx", "-g", "daemon off;"]
