package config

import (
	"fmt"
	"os"
)

type Config struct {
	DBHost       string
	DBPort       string
	DBName       string
	DBUser       string
	DBPassword   string
	KafkaBrokers string
	ServerPort   string
}

func Load() *Config {
	return &Config{
		DBHost:       getEnv("DB_HOST", "localhost"),
		DBPort:       getEnv("DB_PORT", "5432"),
		DBName:       getEnv("DB_NAME", "quickbite_order_db"),
		DBUser:       getEnv("DB_USER", "quickbite"),
		DBPassword:   getEnv("DB_PASSWORD", "quickbite123"),
		KafkaBrokers: getEnv("KAFKA_BROKERS", "localhost:9092"),
		ServerPort:   getEnv("SERVER_PORT", "8082"),
	}
}

func (c *Config) DSN() string {
	return fmt.Sprintf(
		"host=%s port=%s user=%s password=%s dbname=%s sslmode=disable",
		c.DBHost, c.DBPort, c.DBUser, c.DBPassword, c.DBName,
	)
}

func getEnv(key, fallback string) string {
	if val, ok := os.LookupEnv(key); ok {
		return val
	}
	return fallback
}
