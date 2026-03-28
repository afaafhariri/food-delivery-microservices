-- ========================================
-- Seed data for QuickBite Food Delivery
-- Run against individual databases
-- ========================================

-- ========================================
-- RESTAURANT DB
-- ========================================
\c quickbite_restaurant_db;

INSERT INTO restaurants (id, name, address, cuisine_type, operating_hours, active, created_at, updated_at)
VALUES
  ('a1b2c3d4-e5f6-7890-abcd-ef1234567801', 'Spice Garden', '123 Main St, Colombo', 'Sri Lankan', '08:00-22:00', true, NOW(), NOW()),
  ('a1b2c3d4-e5f6-7890-abcd-ef1234567802', 'Pizza Palace', '456 Galle Rd, Colombo', 'Italian', '10:00-23:00', true, NOW(), NOW()),
  ('a1b2c3d4-e5f6-7890-abcd-ef1234567803', 'Dragon Wok', '789 Kandy Rd, Colombo', 'Chinese', '11:00-21:00', true, NOW(), NOW());

INSERT INTO menu_items (id, restaurant_id, name, description, price, category, available, created_at, updated_at)
VALUES
  -- Spice Garden
  ('b1b2c3d4-e5f6-7890-abcd-ef1234567801', 'a1b2c3d4-e5f6-7890-abcd-ef1234567801', 'Rice and Curry', 'Traditional Sri Lankan rice and curry plate', 850.00, 'Main', true, NOW(), NOW()),
  ('b1b2c3d4-e5f6-7890-abcd-ef1234567802', 'a1b2c3d4-e5f6-7890-abcd-ef1234567801', 'Kottu Roti', 'Chopped roti with vegetables and egg', 750.00, 'Main', true, NOW(), NOW()),
  ('b1b2c3d4-e5f6-7890-abcd-ef1234567803', 'a1b2c3d4-e5f6-7890-abcd-ef1234567801', 'String Hoppers', 'Steamed rice noodle nests', 500.00, 'Main', true, NOW(), NOW()),
  ('b1b2c3d4-e5f6-7890-abcd-ef1234567804', 'a1b2c3d4-e5f6-7890-abcd-ef1234567801', 'Fish Ambul Thiyal', 'Sour fish curry', 1200.00, 'Main', true, NOW(), NOW()),
  ('b1b2c3d4-e5f6-7890-abcd-ef1234567805', 'a1b2c3d4-e5f6-7890-abcd-ef1234567801', 'Watalappam', 'Coconut custard pudding', 350.00, 'Dessert', true, NOW(), NOW()),
  -- Pizza Palace
  ('b1b2c3d4-e5f6-7890-abcd-ef1234567806', 'a1b2c3d4-e5f6-7890-abcd-ef1234567802', 'Margherita Pizza', 'Classic tomato, mozzarella, basil', 1500.00, 'Pizza', true, NOW(), NOW()),
  ('b1b2c3d4-e5f6-7890-abcd-ef1234567807', 'a1b2c3d4-e5f6-7890-abcd-ef1234567802', 'Pepperoni Pizza', 'Pepperoni with mozzarella', 1800.00, 'Pizza', true, NOW(), NOW()),
  ('b1b2c3d4-e5f6-7890-abcd-ef1234567808', 'a1b2c3d4-e5f6-7890-abcd-ef1234567802', 'Garlic Bread', 'Toasted bread with garlic butter', 450.00, 'Starter', true, NOW(), NOW()),
  ('b1b2c3d4-e5f6-7890-abcd-ef1234567809', 'a1b2c3d4-e5f6-7890-abcd-ef1234567802', 'Caesar Salad', 'Romaine lettuce with Caesar dressing', 700.00, 'Salad', true, NOW(), NOW()),
  ('b1b2c3d4-e5f6-7890-abcd-ef1234567810', 'a1b2c3d4-e5f6-7890-abcd-ef1234567802', 'Tiramisu', 'Classic Italian coffee dessert', 600.00, 'Dessert', true, NOW(), NOW()),
  -- Dragon Wok
  ('b1b2c3d4-e5f6-7890-abcd-ef1234567811', 'a1b2c3d4-e5f6-7890-abcd-ef1234567803', 'Fried Rice', 'Egg fried rice with vegetables', 900.00, 'Main', true, NOW(), NOW()),
  ('b1b2c3d4-e5f6-7890-abcd-ef1234567812', 'a1b2c3d4-e5f6-7890-abcd-ef1234567803', 'Sweet and Sour Chicken', 'Crispy chicken in sweet and sour sauce', 1100.00, 'Main', true, NOW(), NOW()),
  ('b1b2c3d4-e5f6-7890-abcd-ef1234567813', 'a1b2c3d4-e5f6-7890-abcd-ef1234567803', 'Spring Rolls', 'Crispy vegetable spring rolls', 400.00, 'Starter', true, NOW(), NOW()),
  ('b1b2c3d4-e5f6-7890-abcd-ef1234567814', 'a1b2c3d4-e5f6-7890-abcd-ef1234567803', 'Wonton Soup', 'Pork wontons in clear broth', 550.00, 'Soup', true, NOW(), NOW()),
  ('b1b2c3d4-e5f6-7890-abcd-ef1234567815', 'a1b2c3d4-e5f6-7890-abcd-ef1234567803', 'Kung Pao Prawns', 'Spicy prawns with peanuts', 1400.00, 'Main', true, NOW(), NOW());

-- ========================================
-- CUSTOMER DB
-- ========================================
\c quickbite_customer_db;

INSERT INTO customers (id, name, email, phone, active, created_at, updated_at)
VALUES
  ('c1b2c3d4-e5f6-7890-abcd-ef1234567801', 'Kasun Perera', 'kasun@example.com', '+94771234501', true, NOW(), NOW()),
  ('c1b2c3d4-e5f6-7890-abcd-ef1234567802', 'Nimal Silva', 'nimal@example.com', '+94771234502', true, NOW(), NOW()),
  ('c1b2c3d4-e5f6-7890-abcd-ef1234567803', 'Amaya Fernando', 'amaya@example.com', '+94771234503', true, NOW(), NOW()),
  ('c1b2c3d4-e5f6-7890-abcd-ef1234567804', 'Dilshan Jayawardena', 'dilshan@example.com', '+94771234504', true, NOW(), NOW()),
  ('c1b2c3d4-e5f6-7890-abcd-ef1234567805', 'Sachini Bandara', 'sachini@example.com', '+94771234505', true, NOW(), NOW());

INSERT INTO customer_addresses (id, customer_id, label, address_line, city, postal_code, created_at)
VALUES
  ('d1b2c3d4-e5f6-7890-abcd-ef1234567801', 'c1b2c3d4-e5f6-7890-abcd-ef1234567801', 'Home', '10 Flower Rd, Colombo 7', 'Colombo', '00700', NOW()),
  ('d1b2c3d4-e5f6-7890-abcd-ef1234567802', 'c1b2c3d4-e5f6-7890-abcd-ef1234567801', 'Office', '25 Union Pl, Colombo 2', 'Colombo', '00200', NOW()),
  ('d1b2c3d4-e5f6-7890-abcd-ef1234567803', 'c1b2c3d4-e5f6-7890-abcd-ef1234567802', 'Home', '5 Temple Ln, Kandy', 'Kandy', '20000', NOW()),
  ('d1b2c3d4-e5f6-7890-abcd-ef1234567804', 'c1b2c3d4-e5f6-7890-abcd-ef1234567802', 'Office', '88 Peradeniya Rd, Kandy', 'Kandy', '20000', NOW()),
  ('d1b2c3d4-e5f6-7890-abcd-ef1234567805', 'c1b2c3d4-e5f6-7890-abcd-ef1234567803', 'Home', '32 Galle Face Ter, Colombo 3', 'Colombo', '00300', NOW()),
  ('d1b2c3d4-e5f6-7890-abcd-ef1234567806', 'c1b2c3d4-e5f6-7890-abcd-ef1234567803', 'Office', '120 Bauddhaloka Mw, Colombo 4', 'Colombo', '00400', NOW()),
  ('d1b2c3d4-e5f6-7890-abcd-ef1234567807', 'c1b2c3d4-e5f6-7890-abcd-ef1234567804', 'Home', '15 Lake Dr, Colombo 8', 'Colombo', '00800', NOW()),
  ('d1b2c3d4-e5f6-7890-abcd-ef1234567808', 'c1b2c3d4-e5f6-7890-abcd-ef1234567804', 'Office', '77 Duplication Rd, Colombo 4', 'Colombo', '00400', NOW()),
  ('d1b2c3d4-e5f6-7890-abcd-ef1234567809', 'c1b2c3d4-e5f6-7890-abcd-ef1234567805', 'Home', '3 Marine Dr, Colombo 6', 'Colombo', '00600', NOW()),
  ('d1b2c3d4-e5f6-7890-abcd-ef1234567810', 'c1b2c3d4-e5f6-7890-abcd-ef1234567805', 'Office', '50 Havelock Rd, Colombo 5', 'Colombo', '00500', NOW());

-- ========================================
-- DELIVERY DB
-- ========================================
\c quickbite_delivery_db;

INSERT INTO drivers (id, name, phone, vehicle_type, available, created_at, updated_at)
VALUES
  ('e1b2c3d4-e5f6-7890-abcd-ef1234567801', 'Ruwan Wijesinghe', '+94771234601', 'Motorcycle', true, NOW(), NOW()),
  ('e1b2c3d4-e5f6-7890-abcd-ef1234567802', 'Chaminda Rajapaksa', '+94771234602', 'Bicycle', true, NOW(), NOW());

-- ========================================
-- ORDER DB
-- ========================================
\c quickbite_order_db;

INSERT INTO orders (id, customer_id, restaurant_id, delivery_address, status, total_amount, created_at, updated_at)
VALUES
  ('f1b2c3d4-e5f6-7890-abcd-ef1234567801', 'c1b2c3d4-e5f6-7890-abcd-ef1234567801', 'a1b2c3d4-e5f6-7890-abcd-ef1234567801', '10 Flower Rd, Colombo 7', 'DELIVERED', 1600.00, NOW() - INTERVAL '2 days', NOW()),
  ('f1b2c3d4-e5f6-7890-abcd-ef1234567802', 'c1b2c3d4-e5f6-7890-abcd-ef1234567802', 'a1b2c3d4-e5f6-7890-abcd-ef1234567802', '5 Temple Ln, Kandy', 'PREPARING', 3300.00, NOW() - INTERVAL '1 hour', NOW()),
  ('f1b2c3d4-e5f6-7890-abcd-ef1234567803', 'c1b2c3d4-e5f6-7890-abcd-ef1234567803', 'a1b2c3d4-e5f6-7890-abcd-ef1234567803', '32 Galle Face Ter, Colombo 3', 'PLACED', 2300.00, NOW(), NOW());

INSERT INTO order_items (id, order_id, menu_item_id, menu_item_name, quantity, unit_price)
VALUES
  ('a0b2c3d4-e5f6-7890-abcd-ef1234567801', 'f1b2c3d4-e5f6-7890-abcd-ef1234567801', 'b1b2c3d4-e5f6-7890-abcd-ef1234567801', 'Rice and Curry', 1, 850.00),
  ('a0b2c3d4-e5f6-7890-abcd-ef1234567802', 'f1b2c3d4-e5f6-7890-abcd-ef1234567801', 'b1b2c3d4-e5f6-7890-abcd-ef1234567802', 'Kottu Roti', 1, 750.00),
  ('a0b2c3d4-e5f6-7890-abcd-ef1234567803', 'f1b2c3d4-e5f6-7890-abcd-ef1234567802', 'b1b2c3d4-e5f6-7890-abcd-ef1234567806', 'Margherita Pizza', 1, 1500.00),
  ('a0b2c3d4-e5f6-7890-abcd-ef1234567804', 'f1b2c3d4-e5f6-7890-abcd-ef1234567802', 'b1b2c3d4-e5f6-7890-abcd-ef1234567807', 'Pepperoni Pizza', 1, 1800.00),
  ('a0b2c3d4-e5f6-7890-abcd-ef1234567805', 'f1b2c3d4-e5f6-7890-abcd-ef1234567803', 'b1b2c3d4-e5f6-7890-abcd-ef1234567811', 'Fried Rice', 1, 900.00),
  ('a0b2c3d4-e5f6-7890-abcd-ef1234567806', 'f1b2c3d4-e5f6-7890-abcd-ef1234567803', 'b1b2c3d4-e5f6-7890-abcd-ef1234567814', 'Kung Pao Prawns', 1, 1400.00);
