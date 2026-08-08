DELETE FROM product_pricing;
DELETE FROM product_stock;
DELETE FROM products;

ALTER TABLE products ADD COLUMN seller_id BIGINT NOT NULL;

ALTER TABLE products ADD CONSTRAINT fk_products_seller
    FOREIGN KEY (seller_id) REFERENCES users(id);

CREATE INDEX idx_products_seller_id ON products(seller_id);