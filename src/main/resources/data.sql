-- ============================================================
-- Seed data - runs only if spring.sql.init.mode is enabled.
-- ============================================================

-- Seed Roles
INSERT INTO roles (id, role_name) VALUES 
(1, 'CUSTOMER'),
(2, 'COURIER'),
(3, 'MERCHANT'),
(4, 'ADMIN')
ON CONFLICT (id) DO NOTHING;

-- Seed Admin User (username: 'admin', password: 'admin123')
-- BCrypt hash for 'admin123'
INSERT INTO users (id, username, full_name, phone_number, password_hash, is_active) VALUES 
('d3eede99-9c0b-4ef8-bb6d-6bb9bd380a44', 'admin', 'System Admin', '+1234567893', '$2a$10$FsdEtG8vyB4Nqs5u.usi6uCi50X4G4CXQSplld7blUohfnzZP2O0e', TRUE)
ON CONFLICT (id) DO NOTHING;

-- Seed User Roles Link
INSERT INTO user_roles (user_id, role_id) VALUES 
('d3eede99-9c0b-4ef8-bb6d-6bb9bd380a44', 4)
ON CONFLICT (user_id, role_id) DO NOTHING;

-- Seed System Admin Profile
INSERT INTO system_admin_profiles (user_id, admin_level, access_scope, verification_status) VALUES 
('d3eede99-9c0b-4ef8-bb6d-6bb9bd380a44', 'ADMIN', 'GLOBAL', 'APPROVED')
ON CONFLICT (user_id) DO NOTHING;
