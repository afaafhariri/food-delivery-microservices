-- Same as scripts/init-databases.sql (used by Docker Compose). Keep them in sync.
-- Kustomize cannot read files outside its own directory tree, hence the copy.
CREATE DATABASE quickbite_restaurant_db;
CREATE DATABASE quickbite_order_db;
CREATE DATABASE quickbite_delivery_db;
CREATE DATABASE quickbite_customer_db;
