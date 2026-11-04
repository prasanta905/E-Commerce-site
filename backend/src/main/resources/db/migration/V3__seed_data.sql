-- =====================================================================
-- ShopSphere — V3: Seed data (local dev / demo). Idempotent-safe inserts.
-- Passwords: Admin@123 (admin), User@123 (customers) — BCrypt hashed.
-- =====================================================================

-- ---------------------------------------------------------------- roles
INSERT INTO roles (name) VALUES ('ROLE_USER'), ('ROLE_ADMIN');

-- ---------------------------------------------------------------- users
INSERT INTO users (email, password_hash, full_name, phone, is_active, is_email_verified) VALUES
('admin@shopsphere.local', '$2a$10$LaqWA4t3kv/DTBDy7Dfyde1NdxsJ43G7.dJhnd6KwxA0VnFGMSbmO', 'ShopSphere Admin', NULL, 1, 1),
('customer1@shopsphere.local', '$2a$10$3Rfi1S3unA.LYDEuu0Xo5uuasjKA71ikiL4zqz1kd5kZDgJwMeB5C', 'Prasanta Kumar', '+91-9876500001', 1, 1),
('customer2@shopsphere.local', '$2a$10$3Rfi1S3unA.LYDEuu0Xo5uuasjKA71ikiL4zqz1kd5kZDgJwMeB5C', 'Ananya Sharma', '+91-9876500002', 1, 1),
('customer3@shopsphere.local', '$2a$10$3Rfi1S3unA.LYDEuu0Xo5uuasjKA71ikiL4zqz1kd5kZDgJwMeB5C', 'Rohit Verma', '+91-9876500003', 1, 0);

INSERT INTO user_roles (user_id, role_id)
SELECT u.id, r.id FROM users u, roles r
WHERE (u.email='admin@shopsphere.local' AND r.name='ROLE_ADMIN')
   OR (u.email IN ('customer1@shopsphere.local','customer2@shopsphere.local','customer3@shopsphere.local') AND r.name='ROLE_USER');

-- ---------------------------------------------------------------- addresses
INSERT INTO addresses (user_id, contact_name, phone, line1, line2, city, state, pincode, is_default)
SELECT u.id, 'Prasanta Kumar', '+91-9876500001', '12A.Reverse Halcium Apartments', 'MG Road', 'Bengaluru', 'Karnataka', '560001', 1
FROM users u WHERE u.email='customer1@shopsphere.local';

INSERT INTO addresses (user_id, contact_name, phone, line1, line2, city, state, pincode, is_default)
SELECT u.id, 'Ananya Sharma', '+91-9876500002', '77 Rosewood Enclave', 'Sector 14', 'Noida', 'Uttar Pradesh', '201301', 1
FROM users u WHERE u.email='customer2@shopsphere.local';

-- ---------------------------------------------------------------- categories
INSERT INTO categories (id, parent_category_id, name, slug, image_url) VALUES
(1, NULL, 'Electronics',     'electronics',      NULL),
(2,    1,  'Smartphones',    'smartphones',      NULL),
(3,    1,  'Laptops',        'laptops',          NULL),
(4,    1,  'Audio',          'audio',            NULL),
(5, NULL, 'Fashion',          'fashion',          NULL),
(6,    5,  'Women',          'fashion-women',    NULL),
(7,    5,  'Men',            'fashion-men',      NULL),
(8, NULL, 'Home & Kitchen',   'home-kitchen',     NULL),
(9,    8,  'Cookware',       'cookware',         NULL),
(10,   8,  'Smart Home',     'smart-home',       NULL),
(11,NULL, 'Fitness',          'fitness',          NULL);

-- ---------------------------------------------------------------- brands
INSERT INTO brands (id, name, slug) VALUES
(1, 'Nexora',           'nexora'),
(2, 'Zenava',           'zenava'),
(3, 'Orbitrio',         'orbitrio'),
(4, 'Lumira',           'lumira'),
(5, 'Forge & Flame',    'forge-flame'),
(6, 'Aurora Essentials','aurora-essentials');

-- ---------------------------------------------------------------- products
INSERT INTO products (id, category_id, brand_id, name, slug, description, mrp, selling_price, rating_avg, rating_count, is_featured) VALUES
(1, 2, 1, 'Nexora N-12 Pro 5G (128GB, Cosmic Grey)',   'nexora-n12-pro-5g-cosmic-grey', 'Flagship-grade 5G smartphone with an AMOLED 120Hz display, 50MP stabilised main camera, 5000 mAh battery and 67W fast charge. Dual SIM, IP68 splash resistance and 3 years of security updates.', 42999.00, 35999.00, 4.60, 214, 1),
(2, 2, 1, 'Nexora N-12 Lite (64GB, Ocean Blue)',       'nexora-n12-lite-ocean-blue', 'Lightweight daily driver with 90Hz screen, 48MP camera and 4500 mAh battery. Clean, bloat-free UI and reliable mid-tier chipset.',      21999.00, 16999.00, 4.20, 148, 0),
(3, 3, 1, 'Nexora UltraBook 14" (i7, 16GB/512GB SSD)', 'nexora-ultrabook-14-i7', 'Ultralight aluminium chassis, 14" 2.8K display, 13th Gen i7, 16GB LPDDR5, 512GB NVMe SSD, 1.29kg, all-day 12-hour battery.',              89999.00, 72999.00, 4.70, 96, 1),
(4, 3, 2, 'Zenava Creator 15" (Ryzen 7, 16/1TB)',     'zenava-creator-15-ryzen7', 'Creator-oriented 15.6" 120Hz laptop for editing and design workloads: Ryzen 7, dedicated graphics, 1TB SSD and colorcalibrated panel.',   104999.00, 89999.00, 4.40, 61, 0),
(5, 4, 3, 'Orbitrio Pulse Buds ANC',                  'orbitrio-pulse-buds-anc', 'True-wireless earbuds with hybrid active noise cancellation, 42-hour total playtime, low-latency gaming mode and IPX5 sweat resistance.',   4999.00,  2999.00, 4.30, 402, 1),
(6, 4, 3, 'Orbitrio SoundStage 900 Bluetooth Speaker', 'orbitrio-soundstage-900', 'Portable 60W speaker with 360° sound, party-link pairing of up to 100 speakers and IPX7 waterproofing for outdoor use.',                   7999.00,  5499.00, 4.50, 189, 0),
(7, 1, 1, 'Nexora SmartWatch X2',                      'nexora-smartwatch-x2', '1.85" AMOLED smartwatch with 14-day battery, inbuilt GPS, SpO2 plus ECG-grade heart rate sensing and 100+ sport modes.',                        12999.00,  8499.00, 4.10, 271, 0),
(8, 6, 2, 'Zenava Elegant Chiffon Maxi Dress (Beige)', 'zenava-chiffon-maxi-dress-beige', 'Flowing full-length chiffon dress with subtle pleats, fully lined bodice, hidden zip and machine-washable premium fabric.',          3499.00,  1999.00, 4.20, 88, 0),
(9, 6, 6, 'Aurora Essentials Linen Co-ord Set (Black)','aurora-linen-coord-set-black', 'Breathable pure-linen co-ord set: relaxed crop top and wide-leg pants. Pre-washed for softness, breathable summer fabric.',              3299.00,  2399.00, 4.50, 64, 0),
(10,7, 2, 'Zenava Oxford Casual Shirt (Sky Blue)',    'zenava-oxford-casual-shirt-sky-blue', 'Yarn-dyed Oxford cotton shirt — wrinkle resistant, breathable half-regular fit, mother-of-pearl buttons.',                          1899.00,  1299.00, 4.30, 121, 0),
(11,7, 4, 'Lumira Everyday Sneakers (White)',         'lumira-everyday-sneakers-white', 'Minimalist low-top sneakers with memoryfoam insole and vulcanised rubber sole for all-day comfort and grip.',                             2999.00,  2199.00, 4.10, 210, 0),
(12,9, 5, 'Forge & Flame Cast-Iron Kadai 26 cm',      'forge-flame-cast-iron-kadai-26', 'Pre-seasoned cast-iron wok-style kadai for even searing and slow cooking. Naturally non-stick surface improves with use.',             2999.00,  1749.00, 4.70, 154, 1),
(13,9, 5, 'Forge & Flame Triply Stainless Saucepan 2L','forge-flame-triply-saucepan-2l', 'Triply construction (steel-aluminium-steel) for fast, even heating. Induction compatible, mirror-finish interior.',                     2499.00,  1849.00, 4.40, 77, 0),
(14,10,1, 'Nexora Smart Bulb (16M colours, Pack of 2)','nexora-smart-bulb-pack2', 'WiFi smart bulbs with 16 million colours, scheduled scenes, voice-app pairing and 15,000-hour rated LED lifetime.',                        1999.00,  1299.00, 4.00, 190, 0),
(15,10,6, 'Aurora Essentials Smart Plug 16A (Pack of 2)','aurora-smart-plug-16a-pack2', 'Heavy-load 16A WiFi smart plug for air conditioners, geysers and motors. Automation rules plus energy monitoring in the companion app.', 999.00,  649.00, 4.20, 245, 0),
(16,11,4, 'Lumira Adjustable Dumbbell 12.5 Kg Pair',  'lumira-adjustable-dumbbell-12kg', 'Space-saving adjustable dumbbells with quick-lock plates from 2.5 to 12.5 kg. Anti-slip knurled handle and compact cradle.',           6999.00,  5499.00, 4.30, 58, 0),
(17,11,6, 'Aurora Essentials Yoga Mat 6 mm (TPE)',    'aurora-yoga-mat-6mm', 'Eco-friendly TPE yoga mat: dual-texture, non-slip gear surface, 6 mm cushion and alignment-guiding reference lines.',                             1499.00,  899.00, 4.50, 166, 0),
(18, 1, 3, 'Orbitrio FitBand 3 Fitness Tracker',      'orbitrio-fitband-3', 'Feather-light fitness band with 10-day battery, melanoma-spike-resistant bright AMOLED band, water-resistant core and fast magnetic charging.', 3999.00, 2299.00, 3.90, 132, 0);

-- --------------------------------------------------- product images (1-2 each)
INSERT INTO product_images (product_id, image_url, is_primary, sort_order) VALUES
(1,  '/uploads/products/nexora-n12-pro-5g-cosmic-grey-1.jpg', 1, 1),
(1,  '/uploads/products/nexora-n12-pro-5g-cosmic-grey-2.jpg', 0, 2),
(2,  '/uploads/products/nexora-n12-lite-ocean-blue-1.jpg',    1, 1),
(2,  '/uploads/products/nexora-n12-lite-ocean-blue-2.jpg',    0, 2),
(3,  '/uploads/products/nexora-ultrabook-14-i7-1.jpg',        1, 1),
(3,  '/uploads/products/nexora-ultrabook-14-i7-2.jpg',        0, 2),
(4,  '/uploads/products/zenava-creator-15-ryzen7-1.jpg',      1, 1),
(4,  '/uploads/products/zenava-creator-15-ryzen7-2.jpg',      0, 2),
(5,  '/uploads/products/orbitrio-pulse-buds-anc-1.jpg',       1, 1),
(5,  '/uploads/products/orbitrio-pulse-buds-anc-2.jpg',       0, 2),
(6,  '/uploads/products/orbitrio-soundstage-900-1.jpg',       1, 1),
(6,  '/uploads/products/orbitrio-soundstage-900-2.jpg',       0, 2),
(7,  '/uploads/products/nexora-smartwatch-x2-1.jpg',          1, 1),
(7,  '/uploads/products/nexora-smartwatch-x2-2.jpg',          0, 2),
(8,  '/uploads/products/zenava-chiffon-maxi-dress-beige-1.jpg', 1, 1),
(8,  '/uploads/products/zenava-chiffon-maxi-dress-beige-2.jpg', 0, 2),
(9,  '/uploads/products/aurora-linen-coord-set-black-1.jpg',  1, 1),
(9,  '/uploads/products/aurora-linen-coord-set-black-2.jpg',  0, 2),
(10, '/uploads/products/zenava-oxford-casual-shirt-sky-blue-1.jpg', 1, 1),
(10, '/uploads/products/zenava-oxford-casual-shirt-sky-blue-2.jpg', 0, 2),
(11, '/uploads/products/lumira-everyday-sneakers-white-1.jpg', 1, 1),
(11, '/uploads/products/lumira-everyday-sneakers-white-2.jpg', 0, 2),
(12, '/uploads/products/forge-flame-cast-iron-kadai-26-1.jpg', 1, 1),
(12, '/uploads/products/forge-flame-cast-iron-kadai-26-2.jpg', 0, 2),
(13, '/uploads/products/forge-flame-triply-saucepan-2l-1.jpg', 1, 1),
(13, '/uploads/products/forge-flame-triply-saucepan-2l-2.jpg', 0, 2),
(14, '/uploads/products/nexora-smart-bulb-pack2-1.jpg',       1, 1),
(14, '/uploads/products/nexora-smart-bulb-pack2-2.jpg',       0, 2),
(15, '/uploads/products/aurora-smart-plug-16a-pack2-1.jpg',   1, 1),
(15, '/uploads/products/aurora-smart-plug-16a-pack2-2.jpg',   0, 2),
(16, '/uploads/products/lumira-adjustable-dumbbell-12kg-1.jpg', 1, 1),
(16, '/uploads/products/lumira-adjustable-dumbbell-12kg-2.jpg', 0, 2),
(17, '/uploads/products/aurora-yoga-mat-6mm-1.jpg',           1, 1),
(17, '/uploads/products/aurora-yoga-mat-6mm-2.jpg',           0, 2),
(18, '/uploads/products/orbitrio-fitband-3-1.jpg',            1, 1),
(18, '/uploads/products/orbitrio-fitband-3-2.jpg',            0, 2);

-- ------------------------------------------------- product specifications
INSERT INTO product_specifications (product_id, spec_key, spec_value) VALUES
(1,'Display','6.67" AMOLED, 120Hz'), (1,'Processor','Snapdragon 7-series'), (1,'Battery','5000 mAh / 67W'), (1,'Camera','50MP main + 8MP ultra-wide'), (1,'Warranty','1 year manufacturer'),
(2,'Display','6.56" IPS LCD, 90Hz'), (2,'Processor','Helio G-series'), (2,'Battery','4500 mAh / 33W'), (2,'Camera','48MP triple'), (2,'Warranty','1 year manufacturer'),
(3,'Display','14" 2.8K OLED 90Hz'), (3,'Processor','13th Gen Core i7'), (3,'RAM','16GB LPDDR5'), (3,'Storage','512GB NVMe SSD'), (3,'Weight','1.29 kg'),
(4,'Display','15.6" FHD 120Hz'), (4,'Processor','AMD Ryzen 7'), (4,'RAM','16GB DDR4'), (4,'Storage','1TB NVMe SSD'), (4,'Graphics','Dedicated 6GB'),
(5,'Noise Cancellation','Hybrid ANC up to 35 dB'), (5,'Playtime','42 hours total'), (5,'Latency','50 ms gaming mode'), (5,'Water Resistance','IPX5'),
(6,'Output','60W 360° audio'), (6,'Playtime','24 hours'), (6,'Pairing','Party-link up to 100 units'), (6,'Water Resistance','IPX7'),
(7,'Display','1.85" AMOLED'), (7,'Battery','14 days typical'), (7,'Sensors','GPS, SpO2, HR'), (7,'Water Resistance','IP68'),
(8,'Fabric','Chiffon with cotton lining'), (8,'Care','Machine wash gentle'), (8,'Origin','India'),
(9,'Fabric','Pure linen, pre-washed'), (9,'Fit','Relaxed'), (9,'Care','Cold hand wash'),
(10,'Fabric','Yarn-dyed Oxford cotton'), (10,'Fit','Half-regular'), (10,'Care','Machine wash warm'),
(11,'Upper','Suedette + PU'), (11,'Sole','Vulcanised rubber'), (11,'Insole','Memoryfoam'),
(12,'Material','Pre-seasoned cast iron'), (12,'Size','26 cm diameter'), (12,'Compatible','Induction + gas + oven'),
(13,'Material','Stainless steel triply body'), (13,'Capacity','2 L'), (13,'Compatible','Induction + gas'),
(14,'Lumens','800 lm'), (14,'Colour temp','2700K–6500K'), (14,'Connectivity','2.4GHz WiFi'),
(15,'Load','16 A'), (15,'Connectivity','2.4GHz WiFi'), (15,'Energy monitoring','Yes'),
(16,'Weight range','2.5–12.5 kg per dumbbell'), (16,'Handle','Knurled steel'), (16,'Base','Cradle included'),
(17,'Thickness','6 mm'), (17,'Material','Double-side TPE'), (17,'Size','183 × 61 cm'),
(18,'Display','1.62" AMOLED'), (18,'Battery','10 days typical'), (18,'Sensors','Optical HR + SpO2');

-- ---------------------------------------------------------- inventory
INSERT INTO inventory (product_id, total_quantity, reserved_quantity, low_stock_threshold) VALUES
(1, 80, 0, 10), (2, 120, 0, 12), (3, 40, 0, 6), (4, 25, 0, 5), (5, 200, 0, 20),
(6, 60, 0, 8), (7, 90, 0, 10), (8, 70, 0, 8), (9, 55, 0, 8), (10, 150, 0, 15),
(11, 100, 0, 10), (12, 45, 0, 6), (13, 35, 0, 5), (14, 85, 0, 10), (15, 160, 0, 16),
(16, 30, 0, 5), (17, 140, 0, 14), (18, 75, 0, 8);

-- ---------------------------------------------------------- coupons
INSERT INTO coupons (code, discount_type, discount_value, min_order_amount, max_discount_amount, expires_at, max_global_uses, max_uses_per_user, is_active) VALUES
('WELCOME10', 'PERCENT', 10.00, 999.00,  500.00, '2027-12-31 23:59:59', NULL, 1, 1),
('FLAT200',   'FIXED',  200.00, 1499.00, NULL,   '2027-12-31 23:59:59', 100,  2, 1),
('FESTIVE25', 'PERCENT', 25.00, 2999.00, 1000.00,'2027-06-30 23:59:59', NULL, 1, 1);

-- Initial PURCHASE ledger entries (audit-grade seed)
INSERT INTO inventory_transactions (inventory_id, transaction_type, quantity_delta, note)
SELECT i.id, 'PURCHASE', i.total_quantity, 'Initial stock from seed'
FROM inventory i;
