-- Disable foreign key checks to allow dropping tables in any order
SET FOREIGN_KEY_CHECKS=0;

-- Drop existing tables if they exist
DROP TABLE IF EXISTS user_roles;
DROP TABLE IF EXISTS roles;
DROP TABLE IF EXISTS users;
DROP TABLE IF EXISTS delivery_guys;
DROP TABLE IF EXISTS owners;

SET FOREIGN_KEY_CHECKS=1;

-- Create users table
CREATE TABLE IF NOT EXISTS users (
    id VARCHAR(36) PRIMARY KEY,
    username VARCHAR(50) UNIQUE NOT NULL,
    full_name VARCHAR(255) NOT NULL,
    phone_number VARCHAR(20) UNIQUE NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
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

-- Insert initial roles
-- Explicitly specific IDs to ensure they match user_roles inserts
INSERT INTO roles (id, role_name) VALUES (1, 'CUSTOMER') ON DUPLICATE KEY UPDATE role_name=role_name;
INSERT INTO roles (id, role_name) VALUES (2, 'COURIER') ON DUPLICATE KEY UPDATE role_name=role_name;
INSERT INTO roles (id, role_name) VALUES (3, 'MERCHANT') ON DUPLICATE KEY UPDATE role_name=role_name;
INSERT INTO roles (id, role_name) VALUES (4, 'ADMIN') ON DUPLICATE KEY UPDATE role_name=role_name;

-- Insert sample users
-- Password for 'password123': $2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy
-- Password for 'admin123': $2a$10$oTU1JKYkyCQSfOS0Rm0XZuaFYgb8bWwgJlbdjsOH/fSLlp6EFbmgy
-- Insert sample users
-- Password for 'password123': $2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy
-- Password for 'admin123': $2a$10$oTU1JKYkyCQSfOS0Rm0XZuaFYgb8bWwgJlbdjsOH/fSLlp6EFbmgy
INSERT INTO users (id, username, full_name, phone_number, password_hash, is_active) 
VALUES 
    ('a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11', 'johndoe', 'John Doe', '+1234567890', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', true),
    ('b1ffcd99-9c0b-4ef8-bb6d-6bb9bd380a22', 'janesmith', 'Jane Smith', '+1234567891', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', true),
    ('c2eece99-9c0b-4ef8-bb6d-6bb9bd380a33', 'bobjohnson', 'Bob Johnson', '+1234567892', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', true),
    ('d3eede99-9c0b-4ef8-bb6d-6bb9bd380a44', 'admin', 'System Admin', '+1234567893', '$2a$10$oTU1JKYkyCQSfOS0Rm0XZuaFYgb8bWwgJlbdjsOH/fSLlp6EFbmgy', true)
ON DUPLICATE KEY UPDATE 
    id=VALUES(id),
    full_name=VALUES(full_name),
    password_hash=VALUES(password_hash);

-- Assign roles to users
DELETE FROM user_roles; -- Clear roles to reassign

-- John Doe is a CUSTOMER
INSERT INTO user_roles (user_id, role_id) VALUES ('a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11', 1);

-- Jane Smith is a COURIER
INSERT INTO user_roles (user_id, role_id) VALUES ('b1ffcd99-9c0b-4ef8-bb6d-6bb9bd380a22', 2);

-- Bob Johnson is a MERCHANT
INSERT INTO user_roles (user_id, role_id) VALUES ('c2eece99-9c0b-4ef8-bb6d-6bb9bd380a33', 3);

-- Alice Admin is an ADMIN
INSERT INTO user_roles (user_id, role_id) VALUES ('d3eede99-9c0b-4ef8-bb6d-6bb9bd380a44', 4);

-- Create delivery_guys table
CREATE TABLE IF NOT EXISTS delivery_guys (
    phone_number VARCHAR(20) PRIMARY KEY,
    username VARCHAR(50) UNIQUE NOT NULL,
    full_name VARCHAR(255) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    profile_picture_url VARCHAR(500),
    is_active BOOLEAN DEFAULT TRUE,
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
    -- SRID 4326 is common for lat/lng, but MySQL defaults to SRID 0 if not specified.
    -- Hibernate Spatial might expect SRID 0 or 4326 depending on config.
    -- For simplicity with JTS default factory, we'll use default SRID.
);

-- Create restaurants table
CREATE TABLE IF NOT EXISTS restaurants (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    logo_url VARCHAR(500),
    location POINT NOT NULL,
    owner_id VARCHAR(20) NOT NULL,
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
