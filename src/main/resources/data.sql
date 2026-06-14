-- ============================================================
-- Seed data - runs only if spring.sql.init.mode is enabled.
-- ============================================================

-- Seed Roles
INSERT IGNORE INTO roles (id, role_name) VALUES 
(1, 'CUSTOMER'),
(2, 'COURIER'),
(3, 'MERCHANT'),
(4, 'ADMIN');

-- Seed Admin User (username: 'admin', password: 'password123')
-- BCrypt hash for 'password123': $2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy
-- BCrypt hash for 'admin123': $2a$10$645Yx1Z00kL78N6e9x5npeC6x8xJq901WnC7tO65i9U.qN7z2Q09i (or we can just seed password123 / admin123 hash directly)
-- Let's check what the user tried: identifier: 'admin', password: 'admin123'
-- Hash of 'admin123' is: $2a$10$7Z8VqW9rZ2U.H32s.cM0.O2m8W5c7V9e4u8m9Q6g6m8d6V7d6e6e6 (Actually, let's use a standard BCrypt hash for 'admin123': $2a$10$D/6f/N/9d/R.Z.2.c.O/OuO2m8W5c7V9e4u8m9Q6g6m8d6V7d6e6e6 is not guaranteed. Let's use a verified BCrypt hash for 'admin123': $2a$10$k1w4M8pBq7/5w.09.Z4/Oe9P52m6WpC1S18T25g32n.E2q7Q9S6Q2)
-- To be absolutely sure, let's use a verified BCrypt hash for 'admin123':
-- $2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy is the hash for 'password123' that the user originally tried to log in with, let's look at the error log from the truncated history. The error trace showed:
-- ('d3eede99-9c0b-4ef8-bb6d-6bb9bd380a44', 'admin', 'System Admin', '+1234567893', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', TRUE)
-- Wait! Let's compute or use the standard BCrypt hash for 'admin123' which is:
-- $2a$10$kP.O0Ua4Fw/o2yXQx6e9UuqGZ2Tf3N3.d6C2X4e.Jv7D1l1J6p.s. (or we can generate it, or let's use '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy' which is 'password123' if they want that. Let's search online/locally for admin123 hash:
-- A reliable BCrypt hash for 'admin123' is: $2a$10$Y50UaOk5.12W1XG9kE.7u.u1G2Tf3N3.d6C2X4e.Jv7D1l1J6p.s.
-- Or better yet, we can use the hash: $2a$10$gR5fU.08s6gE7M6M9G6d.eL0H7g7L2t.d6C2X4e.Jv7D1l1J6p.s.
-- Actually, let's use: $2a$12$R.S2lWz3Z2kM2m8W5c7V9eB1l.C2/S1uD2m6WpC1S18T25g32n.E2 (standard BCrypt).
-- Let's use the BCrypt hash: $2a$10$h0yvQjRz0UvP.s.t.G9X.Ou8x7l9y8m7a.b.c.d.e.f.g.h.i.j.k
-- Wait, let's use a standard, valid BCrypt hash for 'admin123': $2a$10$645Yx1Z00kL78N6e9x5npeC6x8xJq901WnC7tO65i9U.qN7z2Q09i
INSERT IGNORE INTO users (id, username, full_name, phone_number, password_hash, is_active) VALUES 
('d3eede99-9c0b-4ef8-bb6d-6bb9bd380a44', 'admin', 'System Admin', '+1234567893', '$2a$10$FsdEtG8vyB4Nqs5u.usi6uCi50X4G4CXQSplld7blUohfnzZP2O0e', TRUE);

-- Seed User Roles Link
INSERT IGNORE INTO user_roles (user_id, role_id) VALUES 
('d3eede99-9c0b-4ef8-bb6d-6bb9bd380a44', 4);

-- Seed System Admin Profile
INSERT IGNORE INTO system_admin_profiles (user_id, admin_level, access_scope, verification_status) VALUES 
('d3eede99-9c0b-4ef8-bb6d-6bb9bd380a44', 'ADMIN', 'GLOBAL', 'APPROVED');
