# Stage 1: Build
FROM golang:1.24-alpine AS builder

RUN apk add --no-cache git
RUN go install github.com/swaggo/swag/cmd/swag@latest

WORKDIR /app

COPY . .

RUN swag init -g cmd/main.go -o docs
RUN CGO_ENABLED=0 GOOS=linux go build -mod=vendor -ldflags="-s -w" -o /order-service ./cmd/main.go

# Stage 2: Runtime
FROM alpine:3.20

RUN apk add --no-cache ca-certificates tzdata \
    && addgroup -S quickbite && adduser -S quickbite -G quickbite

WORKDIR /app

COPY --from=builder /order-service .
COPY --from=builder /app/migrations ./migrations

RUN chown -R quickbite:quickbite /app
USER quickbite

EXPOSE 8082

CMD ["./order-service"]
