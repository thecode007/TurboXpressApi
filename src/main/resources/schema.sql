-- Drop existing tables if they exist
DROP TABLE IF EXISTS user_roles;
DROP TABLE IF EXISTS roles;
DROP TABLE IF EXISTS users;

-- Create users table
CREATE TABLE users (
    id VARCHAR(36) PRIMARY KEY,
    username VARCHAR(50) UNIQUE NOT NULL,
    full_name VARCHAR(255) NOT NULL,
    phone_number VARCHAR(20) UNIQUE NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Create roles table
CREATE TABLE roles (
    id INT AUTO_INCREMENT PRIMARY KEY,
    role_name VARCHAR(50) UNIQUE NOT NULL
);

-- Create user_roles junction table
CREATE TABLE user_roles (
    user_id VARCHAR(36),
    role_id INT,
    PRIMARY KEY (user_id, role_id),
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    FOREIGN KEY (role_id) REFERENCES roles(id) ON DELETE CASCADE
);

-- Insert initial roles
INSERT INTO roles (role_name) VALUES ('CUSTOMER');
INSERT INTO roles (role_name) VALUES ('COURIER');
INSERT INTO roles (role_name) VALUES ('MERCHANT');
INSERT INTO roles (role_name) VALUES ('ADMIN');

-- Insert sample users
-- Password for 'password123': $2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy
-- Password for 'admin123': $2a$10$oTU1JKYkyCQSfOS0Rm0XZuaFYgb8bWwgJlbdjsOH/fSLlp6EFbmgy
INSERT INTO users (id, username, full_name, phone_number, password_hash, is_active) 
VALUES 
    ('a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11', 'johndoe', 'John Doe', '+1234567890', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', true),
    ('b1ffcd99-9c0b-4ef8-bb6d-6bb9bd380a22', 'janesmith', 'Jane Smith', '+1234567891', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', true),
    ('c2ggde99-9c0b-4ef8-bb6d-6bb9bd380a33', 'bobjohnson', 'Bob Johnson', '+1234567892', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', true),
    ('d3hhef99-9c0b-4ef8-bb6d-6bb9bd380a44', 'admin', 'System Admin', '+1234567893', '$2a$10$oTU1JKYkyCQSfOS0Rm0XZuaFYgb8bWwgJlbdjsOH/fSLlp6EFbmgy', true);

-- Assign roles to users
-- John Doe is a CUSTOMER
INSERT INTO user_roles (user_id, role_id) VALUES ('a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11', 1);

-- Jane Smith is a COURIER
INSERT INTO user_roles (user_id, role_id) VALUES ('b1ffcd99-9c0b-4ef8-bb6d-6bb9bd380a22', 2);

-- Bob Johnson is a MERCHANT
INSERT INTO user_roles (user_id, role_id) VALUES ('c2ggde99-9c0b-4ef8-bb6d-6bb9bd380a33', 3);

-- Alice Admin is an ADMIN
INSERT INTO user_roles (user_id, role_id) VALUES ('d3hhef99-9c0b-4ef8-bb6d-6bb9bd380a44', 4);
