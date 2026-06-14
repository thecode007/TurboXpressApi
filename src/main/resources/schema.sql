-- Schema initialization script
-- NOTE: This script uses IF NOT EXISTS on all CREATE TABLE statements
-- so it is safe to run multiple times without losing data.
-- To fully reset the schema, manually drop the tables in your DB client first.
-- Seed data (roles, admin user, etc.) is in data.sql which runs after this file.

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
    id INT AUTO_INCREMENT PRIMARY KEY,
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

-- Create delivery_guys table
CREATE TABLE IF NOT EXISTS delivery_guys (
    phone_number VARCHAR(20) PRIMARY KEY,
    username VARCHAR(50) UNIQUE NOT NULL,
    full_name VARCHAR(255) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    profile_picture_url VARCHAR(500),
    is_active BOOLEAN DEFAULT TRUE,
    monthly_sub_fee DOUBLE NOT NULL DEFAULT 0.0,
    billing_cycle VARCHAR(20) NOT NULL DEFAULT 'MONTHLY',
    next_billing_date DATE NOT NULL DEFAULT '2026-05-08',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
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
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(255) NOT NULL UNIQUE,
    base_fee DOUBLE NOT NULL,
    is_active BOOLEAN DEFAULT TRUE,
    polygon POLYGON NOT NULL
);

-- Create restaurants table
CREATE TABLE IF NOT EXISTS restaurants (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    logo_url VARCHAR(500),
    location POINT NOT NULL,
    owner_id VARCHAR(20) NOT NULL,
    monthly_sub_fee DOUBLE NOT NULL DEFAULT 0.0,
    commission_rate DOUBLE NOT NULL DEFAULT 0.0,
    next_billing_date DATE NOT NULL,
    FOREIGN KEY (owner_id) REFERENCES owners(phone_number) ON DELETE CASCADE
);

-- Create restaurant_items table
CREATE TABLE IF NOT EXISTS restaurant_items (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    title VARCHAR(255) NOT NULL,
    description TEXT,
    price DOUBLE NOT NULL,
    restaurant_id BIGINT NOT NULL,
    FOREIGN KEY (restaurant_id) REFERENCES restaurants(id) ON DELETE CASCADE
);

-- Create restaurant_item_photos table for element collection
CREATE TABLE IF NOT EXISTS restaurant_item_photos (
    item_id BIGINT NOT NULL,
    photo_url VARCHAR(500) NOT NULL,
    FOREIGN KEY (item_id) REFERENCES restaurant_items(id) ON DELETE CASCADE
);

-- Create orders table
CREATE TABLE IF NOT EXISTS orders (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    restaurant_id BIGINT NOT NULL,
    driver_phone_number VARCHAR(20),
    status VARCHAR(50) NOT NULL,
    total_amount DOUBLE NOT NULL,
    platform_commission_amount DOUBLE NOT NULL DEFAULT 0.0,
    is_settled BOOLEAN NOT NULL DEFAULT FALSE,
    delivery_fee DOUBLE NOT NULL DEFAULT 0.0,
    is_settled_driver BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (restaurant_id) REFERENCES restaurants(id) ON DELETE CASCADE,
    FOREIGN KEY (driver_phone_number) REFERENCES delivery_guys(phone_number) ON DELETE SET NULL
);

-- Create order_items table
CREATE TABLE IF NOT EXISTS order_items (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_id BIGINT NOT NULL,
    menu_item_id BIGINT NOT NULL,
    quantity INT NOT NULL,
    price_at_order DOUBLE NOT NULL,
    FOREIGN KEY (order_id) REFERENCES orders(id) ON DELETE CASCADE,
    FOREIGN KEY (menu_item_id) REFERENCES restaurant_items(id) ON DELETE CASCADE
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
    default_address_latitude DOUBLE,
    default_address_longitude DOUBLE,
    verification_status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    admin_note TEXT,
    approved_by VARCHAR(36),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

-- Driver Profiles
CREATE TABLE IF NOT EXISTS driver_profiles (
    user_id VARCHAR(36) PRIMARY KEY,
    display_name VARCHAR(255),
    id_document_url VARCHAR(500),
    criminal_record_url VARCHAR(500),
    license_number VARCHAR(100),
    vehicle_type VARCHAR(50),
    vehicle_plate VARCHAR(50),
    is_available BOOLEAN NOT NULL DEFAULT TRUE,
    rating DOUBLE NOT NULL DEFAULT 0.0,
    monthly_sub_fee DOUBLE NOT NULL DEFAULT 0.0,
    billing_cycle VARCHAR(20) NOT NULL DEFAULT 'MONTHLY',
    next_billing_date DATE,
    carried_over_balance DOUBLE NOT NULL DEFAULT 0.0,
    admin_debt_balance DECIMAL(19,4) NOT NULL DEFAULT 0.0,
    collected_cash_balance DECIMAL(19,4) NOT NULL DEFAULT 0.0,
    daily_rate DECIMAL(19,4) NOT NULL DEFAULT 0.0,
    verification_status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    admin_note TEXT,
    approved_by VARCHAR(36),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

-- Owner Profiles
CREATE TABLE IF NOT EXISTS owner_profiles (
    user_id VARCHAR(36) PRIMARY KEY,
    business_name VARCHAR(255),
    profile_picture_url VARCHAR(500),
    id_document_url VARCHAR(500),
    criminal_record_url VARCHAR(500),
    verification_status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    admin_note TEXT,
    approved_by VARCHAR(36),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

-- System Admin Profiles
CREATE TABLE IF NOT EXISTS system_admin_profiles (
    user_id VARCHAR(36) PRIMARY KEY,
    admin_level VARCHAR(50) NOT NULL DEFAULT 'ADMIN',
    access_scope VARCHAR(50) NOT NULL DEFAULT 'GLOBAL',
    verification_status VARCHAR(20) NOT NULL DEFAULT 'APPROVED',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);
