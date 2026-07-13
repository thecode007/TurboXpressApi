-- Schema initialization script
-- NOTE: This script uses IF NOT EXISTS on all CREATE TABLE statements
-- so it is safe to run multiple times without losing data.
-- To fully reset the schema, manually drop the tables in your DB client first.
-- Seed data (roles, admin user, etc.) is in data.sql which runs after this file.

CREATE EXTENSION IF NOT EXISTS postgis;

-- Create users table
CREATE TABLE IF NOT EXISTS users (
    id VARCHAR(36) PRIMARY KEY,
    username VARCHAR(50) UNIQUE,
    full_name VARCHAR(255) NOT NULL,
    phone_number VARCHAR(20) UNIQUE NOT NULL,
    password_hash VARCHAR(255),
    firebase_uid VARCHAR(128) UNIQUE,
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Create roles table
CREATE TABLE IF NOT EXISTS roles (
    id SERIAL PRIMARY KEY,
    role_name VARCHAR(50) UNIQUE NOT NULL
);

-- Create user_roles junction table
CREATE TABLE IF NOT EXISTS user_roles (
    user_id VARCHAR(36),
    role_id INT,
    PRIMARY KEY (user_id, role_id),
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    FOREIGN KEY (role_id) REFERENCES roles(id) ON DELETE CASCADE
);



-- Create owners table
CREATE TABLE IF NOT EXISTS owners (
    phone_number VARCHAR(20) PRIMARY KEY,
    full_name VARCHAR(255) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    profile_picture_url VARCHAR(500),
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Create delivery_zones table
CREATE TABLE IF NOT EXISTS delivery_zones (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL UNIQUE,
    is_active BOOLEAN DEFAULT TRUE,
    polygon geometry(Polygon, 4326) NOT NULL
);

-- Create restaurants table
CREATE TABLE IF NOT EXISTS restaurants (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    logo_url VARCHAR(500),
    location geometry(Point, 4326) NOT NULL,
    location_description VARCHAR(500),
    owner_id VARCHAR(20) NOT NULL,
    monthly_sub_fee DOUBLE PRECISION NOT NULL DEFAULT 0.0,
    commission_rate DOUBLE PRECISION NOT NULL DEFAULT 0.0,
    next_billing_date DATE NOT NULL,
    FOREIGN KEY (owner_id) REFERENCES owners(phone_number) ON DELETE CASCADE
);

-- Create restaurant_items table
CREATE TABLE IF NOT EXISTS restaurant_items (
    id BIGSERIAL PRIMARY KEY,
    title VARCHAR(255) NOT NULL,
    description TEXT,
    price DOUBLE PRECISION NOT NULL,
    restaurant_id BIGINT NOT NULL,
    FOREIGN KEY (restaurant_id) REFERENCES restaurants(id) ON DELETE CASCADE
);

-- Create restaurant_item_photos table for element collection
CREATE TABLE IF NOT EXISTS restaurant_item_photos (
    item_id BIGINT NOT NULL,
    photo_url VARCHAR(500) NOT NULL,
    FOREIGN KEY (item_id) REFERENCES restaurant_items(id) ON DELETE CASCADE
);


-- ============================================================
-- Profile Partitioning Pattern — Profile Tables
-- Each table is linked 1:1 to users via user_id FK.
-- A single user can have multiple profiles simultaneously.
-- ============================================================

-- Customer Profiles
CREATE TABLE IF NOT EXISTS customer_profiles (
    user_id VARCHAR(36) PRIMARY KEY,
    display_name VARCHAR(255),
    profile_picture_url VARCHAR(500),
    default_address_latitude DOUBLE PRECISION,
    default_address_longitude DOUBLE PRECISION,
    default_address_label VARCHAR(500),
    verification_status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    admin_note TEXT,
    approved_by VARCHAR(36),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

-- Idempotent migrations: add columns that may be missing on existing databases
ALTER TABLE customer_profiles ADD COLUMN IF NOT EXISTS profile_picture_url VARCHAR(500);
ALTER TABLE customer_profiles ADD COLUMN IF NOT EXISTS default_address_label VARCHAR(500);

-- Driver Profiles
CREATE TABLE IF NOT EXISTS driver_profiles (
    user_id VARCHAR(36) PRIMARY KEY,
    display_name VARCHAR(255),
    profile_picture_url VARCHAR(500),
    id_document_url VARCHAR(500),
    criminal_record_url VARCHAR(500),
    license_number VARCHAR(100),
    vehicle_type VARCHAR(50),
    vehicle_plate VARCHAR(50),
    online_status VARCHAR(20) NOT NULL DEFAULT 'OFFLINE',
    rating DOUBLE PRECISION NOT NULL DEFAULT 0.0,
    monthly_sub_fee DOUBLE PRECISION NOT NULL DEFAULT 0.0,
    billing_cycle VARCHAR(20) NOT NULL DEFAULT 'MONTHLY',
    next_billing_date DATE,
    carried_over_balance DOUBLE PRECISION NOT NULL DEFAULT 0.0,
    admin_debt_balance DECIMAL(19,4) NOT NULL DEFAULT 0.0,
    collected_cash_balance DECIMAL(19,4) NOT NULL DEFAULT 0.0,
    daily_rate DECIMAL(19,4) NOT NULL DEFAULT 0.0,
    verification_status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    admin_note TEXT,
    approved_by VARCHAR(36),
    status VARCHAR(20) NOT NULL DEFAULT 'IDLE',
    current_location geometry(Point, 4326),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_driver_profiles_location ON driver_profiles USING GIST(current_location);

-- Idempotent migrations for driver profiles
ALTER TABLE driver_profiles DROP COLUMN IF EXISTS is_available;
ALTER TABLE driver_profiles ADD COLUMN IF NOT EXISTS online_status VARCHAR(20) NOT NULL DEFAULT 'OFFLINE';
ALTER TABLE driver_profiles ADD COLUMN IF NOT EXISTS profile_picture_url VARCHAR(500);

-- Owner Profiles
CREATE TABLE IF NOT EXISTS owner_profiles (
    user_id VARCHAR(36) PRIMARY KEY,
    business_name VARCHAR(255),
    profile_picture_url VARCHAR(500),
    id_document_url VARCHAR(500),
    criminal_record_url VARCHAR(500),
    location_description VARCHAR(500),
    restaurant_location VARCHAR(500),
    verification_status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    admin_note TEXT,
    approved_by VARCHAR(36),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

-- Idempotent migration: add restaurant_location for existing databases
ALTER TABLE owner_profiles ADD COLUMN IF NOT EXISTS restaurant_location VARCHAR(500);

-- System Admin Profiles
CREATE TABLE IF NOT EXISTS system_admin_profiles (
    user_id VARCHAR(36) PRIMARY KEY,
    admin_level VARCHAR(50) NOT NULL DEFAULT 'ADMIN',
    access_scope VARCHAR(50) NOT NULL DEFAULT 'GLOBAL',
    verification_status VARCHAR(20) NOT NULL DEFAULT 'APPROVED',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

-- Create orders table
CREATE TABLE IF NOT EXISTS orders (
    id BIGSERIAL PRIMARY KEY,
    restaurant_id BIGINT NOT NULL,
    driver_id VARCHAR(36),
    status VARCHAR(50) NOT NULL,
    total_amount DOUBLE PRECISION NOT NULL,
    platform_commission_amount DOUBLE PRECISION NOT NULL DEFAULT 0.0,
    is_settled BOOLEAN NOT NULL DEFAULT FALSE,
    delivery_fee DOUBLE PRECISION NOT NULL DEFAULT 0.0,
    is_settled_driver BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (restaurant_id) REFERENCES restaurants(id) ON DELETE CASCADE,
    FOREIGN KEY (driver_id) REFERENCES driver_profiles(user_id) ON DELETE SET NULL
);

-- Create order_items table
CREATE TABLE IF NOT EXISTS order_items (
    id BIGSERIAL PRIMARY KEY,
    order_id BIGINT NOT NULL,
    menu_item_id BIGINT NOT NULL,
    quantity INT NOT NULL,
    price_at_order DOUBLE PRECISION NOT NULL,
    FOREIGN KEY (order_id) REFERENCES orders(id) ON DELETE CASCADE,
    FOREIGN KEY (menu_item_id) REFERENCES restaurant_items(id) ON DELETE CASCADE
);

-- ============================================================
-- Customers table (desktop-managed customer records)
-- One row per unique phone number. Zone + exact coordinates
-- are stored here so they persist across orders.
-- ============================================================
CREATE TABLE IF NOT EXISTS customers (
    id BIGSERIAL PRIMARY KEY,
    user_id VARCHAR(36),
    full_name VARCHAR(255) NOT NULL,
    phone_number VARCHAR(50) NOT NULL UNIQUE,
    delivery_zone_id BIGINT,
    latitude DOUBLE PRECISION,
    longitude DOUBLE PRECISION,
    detailed_address VARCHAR(500),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE SET NULL,
    FOREIGN KEY (delivery_zone_id) REFERENCES delivery_zones(id) ON DELETE SET NULL
);

-- ============================================================
-- Orders table migration: add customer_id FK
-- Remove old inline customer columns (idempotent)
-- ============================================================
ALTER TABLE orders ADD COLUMN IF NOT EXISTS customer_id BIGINT REFERENCES customers(id) ON DELETE SET NULL;
ALTER TABLE orders ADD COLUMN IF NOT EXISTS route_distance_km DOUBLE PRECISION;

-- Drop old inline customer/location columns if they still exist
ALTER TABLE orders DROP COLUMN IF EXISTS customer_name;
ALTER TABLE orders DROP COLUMN IF EXISTS customer_phone;
ALTER TABLE orders DROP COLUMN IF EXISTS location_method;
ALTER TABLE orders DROP COLUMN IF EXISTS delivery_zone_id;
ALTER TABLE orders DROP COLUMN IF EXISTS whatsapp_map_link;
ALTER TABLE orders DROP COLUMN IF EXISTS detailed_address;
ALTER TABLE orders DROP COLUMN IF EXISTS latitude;
ALTER TABLE orders DROP COLUMN IF EXISTS longitude;

-- New timestamp metrics for performance tracking
ALTER TABLE orders ADD COLUMN IF NOT EXISTS accepted_at TIMESTAMP;
ALTER TABLE orders ADD COLUMN IF NOT EXISTS ready_at TIMESTAMP;
ALTER TABLE orders ADD COLUMN IF NOT EXISTS picked_up_at TIMESTAMP;
ALTER TABLE orders ADD COLUMN IF NOT EXISTS delivered_at TIMESTAMP;
