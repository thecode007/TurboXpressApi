-- SQL Script to generate Mock Drivers around a specific location
-- You can run this in your DB client (DBeaver, DataGrip, pgAdmin, etc.)
-- Modify the 'base_lat', 'base_lng', and 'num_drivers' to your needs.

DO $$
DECLARE
    -- Configure your parameters here:
    base_lat DOUBLE PRECISION := 40.7128; -- Latitude
    base_lng DOUBLE PRECISION := -74.0060; -- Longitude
    num_drivers INT := 5;                 -- Number of drivers to generate
    
    -- Variables for the loop
    i INT;
    new_user_id VARCHAR(36);
    driver_lat DOUBLE PRECISION;
    driver_lng DOUBLE PRECISION;
    rand_suffix TEXT;
BEGIN
    FOR i IN 1..num_drivers LOOP
        new_user_id := gen_random_uuid()::VARCHAR(36);
        rand_suffix := floor(random() * 1000000)::text;
        
        -- Generate random offsets for location (approx up to 1-2 km)
        -- 0.01 degrees is roughly 1km
        driver_lat := base_lat + (random() * 0.02 - 0.01);
        driver_lng := base_lng + (random() * 0.02 - 0.01);

        -- 1. Insert user
        INSERT INTO users (id, username, full_name, phone_number, password_hash, is_active)
        VALUES (
            new_user_id,
            'mockdriver_' || rand_suffix,
            'Mock Driver ' || rand_suffix,
            '+1000' || rand_suffix,
            '$2a$10$FsdEtG8vyB4Nqs5u.usi6uCi50X4G4CXQSplld7blUohfnzZP2O0e', -- Default password: 'admin123'
            TRUE
        );

        -- 2. Insert user role (COURIER = 2)
        INSERT INTO user_roles (user_id, role_id)
        VALUES (new_user_id, 2)
        ON CONFLICT DO NOTHING;

        -- 3. Insert driver profile
        INSERT INTO driver_profiles (
            user_id, display_name, is_available, status, verification_status, current_location, rating
        ) VALUES (
            new_user_id,
            'Mock Driver ' || rand_suffix,
            TRUE,
            'IDLE',
            'APPROVED',
            ST_SetSRID(ST_MakePoint(driver_lng, driver_lat), 4326),
            5.0
        );
    END LOOP;
END $$;
