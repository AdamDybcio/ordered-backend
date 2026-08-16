ALTER TABLE orders
    ADD COLUMN delivery_recipient_name  VARCHAR(255),
    ADD COLUMN delivery_phone           VARCHAR(30),
    ADD COLUMN delivery_street          VARCHAR(255),
    ADD COLUMN delivery_building_number VARCHAR(20),
    ADD COLUMN delivery_apartment_number VARCHAR(20),
    ADD COLUMN delivery_city            VARCHAR(100),
    ADD COLUMN delivery_postal_code     VARCHAR(20),
    ADD COLUMN delivery_country         VARCHAR(2);