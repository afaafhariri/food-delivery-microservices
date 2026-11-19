-- One database per service, plus Keycloak's. The only copy of this script: Docker Compose
-- mounts it from here, and the Kubernetes base turns it into the postgres-init ConfigMap
-- (Kustomize cannot read files outside its own directory, so it lives in k8s/).
--
-- Postgres runs it once, when its data volume is empty. For an existing volume, Keycloak's
-- database is created on demand (Compose: keycloak-db service; Kubernetes: Keycloak's init
-- container), and new service databases need a CREATE DATABASE by hand.
CREATE DATABASE quickbite_restaurant_db;
CREATE DATABASE quickbite_order_db;
CREATE DATABASE quickbite_delivery_db;
CREATE DATABASE quickbite_customer_db;
CREATE DATABASE quickbite_keycloak_db;
