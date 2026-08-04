-- Stan magazynowy: jeden aktualny wiersz na produkt
CREATE TABLE product_stock (
    product_id BIGINT PRIMARY KEY REFERENCES products(id),
    quantity INTEGER NOT NULL DEFAULT 0,
    updated_at TIMESTAMP NOT NULL DEFAULT now()
);

-- Historia cen: wiele wierszy na produkt, aktualna cena = najnowszy effective_from
CREATE TABLE product_pricing (
    id BIGSERIAL PRIMARY KEY,
    product_id BIGINT NOT NULL REFERENCES products(id),
    price NUMERIC(10, 2) NOT NULL,
    currency VARCHAR(3) NOT NULL DEFAULT 'PLN',
    effective_from TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX idx_product_pricing_product_id_effective_from
ON product_pricing (product_id, effective_from DESC);

-- Migracja istniejących danych
INSERT INTO product_stock (product_id, quantity, updated_at)
SELECT id, stock_quantity, now() FROM products;

INSERT INTO product_pricing (product_id, price, currency, effective_from)
SELECT id, price, 'PLN', created_at FROM products;

-- Usunięcie starych kolumn z products
ALTER TABLE products DROP COLUMN price;
ALTER TABLE products DROP COLUMN stock_quantity;