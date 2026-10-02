-- 005_catalog_seed.sql
-- Full 48-product seed catalog (Worker F, 2026-10-02).
-- SUPERSEDES 003_seed.sql: this migration deletes the 12 provisional
-- 003 rows (ids 11111111-0000-0000-0000-00000000000N) and inserts the full
-- 48-item catalog. 003_seed.sql is left untouched for history.
-- Deterministic ids: 22222222-0000-0000-0000-<NNN> map to product_NNN,
-- matching the bundled drawables (asset://product_NNN). Currency: INR.

-- 'ethnic' was not in the 001 product_category enum; add it (guarded).
do $$ begin
  if not exists (select 1 from pg_enum e join pg_type t on t.oid = e.enumtypid
                 where t.typname = 'product_category' and e.enumlabel = 'ethnic') then
    alter type product_category add value 'ethnic';
  end if;
end $$;

-- Remove the 12 provisional 003 rows.
delete from public.products where id in (
  '11111111-0000-0000-0000-000000000001',
  '11111111-0000-0000-0000-000000000002',
  '11111111-0000-0000-0000-000000000003',
  '11111111-0000-0000-0000-000000000004',
  '11111111-0000-0000-0000-000000000005',
  '11111111-0000-0000-0000-000000000006',
  '11111111-0000-0000-0000-000000000007',
  '11111111-0000-0000-0000-000000000008',
  '11111111-0000-0000-0000-000000000009',
  '11111111-0000-0000-0000-00000000000a',
  '11111111-0000-0000-0000-00000000000b',
  '11111111-0000-0000-0000-00000000000c'
);

insert into public.products
(id, brand, name, category, price, currency, images, description, sizes, tags, is_active)
values
  ('22222222-0000-0000-0000-000000000001', 'Urbanco', 'Indigo Denim Shirt', 'casual'::product_category, 1499, 'INR', array['asset://product_001'], 'Urbanco Indigo Denim Shirt — style preview, not a fit guarantee.', array['S','M','L','XL','XXL'], array['denim','shirt'], true),
  ('22222222-0000-0000-0000-000000000002', 'Threadline', 'White Essential Tee', 'casual'::product_category, 499, 'INR', array['asset://product_002'], 'Threadline White Essential Tee — style preview, not a fit guarantee.', array['S','M','L','XL','XXL'], array['tee','basics'], true),
  ('22222222-0000-0000-0000-000000000003', 'Kardo', 'Beige Linen Shirt', 'casual'::product_category, 1299, 'INR', array['asset://product_003'], 'Kardo Beige Linen Shirt — style preview, not a fit guarantee.', array['S','M','L','XL','XXL'], array['linen','summer'], true),
  ('22222222-0000-0000-0000-000000000004', 'Urbanco', 'Navy Polo T-Shirt', 'casual'::product_category, 799, 'INR', array['asset://product_004'], 'Urbanco Navy Polo T-Shirt — style preview, not a fit guarantee.', array['S','M','L','XL','XXL'], array['polo'], true),
  ('22222222-0000-0000-0000-000000000005', 'Threadline', 'Olive Cargo Pants', 'casual'::product_category, 1599, 'INR', array['asset://product_005'], 'Threadline Olive Cargo Pants — style preview, not a fit guarantee.', array['28','30','32','34','36'], array['cargo'], true),
  ('22222222-0000-0000-0000-000000000006', 'Kardo', 'Dark Blue Slim Jeans', 'casual'::product_category, 1799, 'INR', array['asset://product_006'], 'Kardo Dark Blue Slim Jeans — style preview, not a fit guarantee.', array['28','30','32','34','36'], array['jeans','denim'], true),
  ('22222222-0000-0000-0000-000000000007', 'Urbanco', 'Khaki Chino Shorts', 'casual'::product_category, 899, 'INR', array['asset://product_007'], 'Urbanco Khaki Chino Shorts — style preview, not a fit guarantee.', array['28','30','32','34','36'], array['shorts'], true),
  ('22222222-0000-0000-0000-000000000008', 'Threadline', 'Gray Pullover Hoodie', 'casual'::product_category, 1399, 'INR', array['asset://product_008'], 'Threadline Gray Pullover Hoodie — style preview, not a fit guarantee.', array['S','M','L','XL','XXL'], array['hoodie'], true),
  ('22222222-0000-0000-0000-000000000009', 'Kardo', 'Cream Sweatshirt', 'casual'::product_category, 1199, 'INR', array['asset://product_009'], 'Kardo Cream Sweatshirt — style preview, not a fit guarantee.', array['S','M','L','XL','XXL'], array['sweatshirt'], true),
  ('22222222-0000-0000-0000-000000000010', 'Urbanco', 'Red-Black Flannel Shirt', 'casual'::product_category, 1299, 'INR', array['asset://product_010'], 'Urbanco Red-Black Flannel Shirt — style preview, not a fit guarantee.', array['S','M','L','XL','XXL'], array['flannel'], true),
  ('22222222-0000-0000-0000-000000000011', 'Threadline', 'Tan Chinos', 'casual'::product_category, 1499, 'INR', array['asset://product_011'], 'Threadline Tan Chinos — style preview, not a fit guarantee.', array['28','30','32','34','36'], array['chinos'], true),
  ('22222222-0000-0000-0000-000000000012', 'Kardo', 'Black Overshirt Jacket', 'jackets'::product_category, 1999, 'INR', array['asset://product_012'], 'Kardo Black Overshirt Jacket — style preview, not a fit guarantee.', array['S','M','L','XL','XXL'], array['overshirt'], true),
  ('22222222-0000-0000-0000-000000000013', 'Urbanco', 'Classic Denim Jacket', 'jackets'::product_category, 2299, 'INR', array['asset://product_013'], 'Urbanco Classic Denim Jacket — style preview, not a fit guarantee.', array['S','M','L','XL','XXL'], array['denim jacket'], true),
  ('22222222-0000-0000-0000-000000000014', 'Threadline', 'Olive Bomber Jacket', 'jackets'::product_category, 2499, 'INR', array['asset://product_014'], 'Threadline Olive Bomber Jacket — style preview, not a fit guarantee.', array['S','M','L','XL','XXL'], array['bomber'], true),
  ('22222222-0000-0000-0000-000000000015', 'Kardo', 'Charcoal Formal Blazer', 'jackets'::product_category, 3999, 'INR', array['asset://product_015'], 'Kardo Charcoal Formal Blazer — style preview, not a fit guarantee.', array['S','M','L','XL','XXL'], array['blazer','formal'], true),
  ('22222222-0000-0000-0000-000000000016', 'Urbanco', 'Black Puffer Jacket', 'jackets'::product_category, 2999, 'INR', array['asset://product_016'], 'Urbanco Black Puffer Jacket — style preview, not a fit guarantee.', array['S','M','L','XL','XXL'], array['puffer','winter'], true),
  ('22222222-0000-0000-0000-000000000017', 'Threadline', 'Brown Faux-Leather Jacket', 'jackets'::product_category, 3499, 'INR', array['asset://product_017'], 'Threadline Brown Faux-Leather Jacket — style preview, not a fit guarantee.', array['S','M','L','XL','XXL'], array['leather'], true),
  ('22222222-0000-0000-0000-000000000018', 'Kardo', 'Navy Windbreaker', 'jackets'::product_category, 1799, 'INR', array['asset://product_018'], 'Kardo Navy Windbreaker — style preview, not a fit guarantee.', array['S','M','L','XL','XXL'], array['windbreaker','sport'], true),
  ('22222222-0000-0000-0000-000000000019', 'Nilaya', 'Floral Summer Crop Top', 'tops'::product_category, 899, 'INR', array['asset://product_019'], 'Nilaya Floral Summer Crop Top — style preview, not a fit guarantee.', array['XS','S','M','L','XL'], array['crop top'], true),
  ('22222222-0000-0000-0000-000000000020', 'Nilaya', 'White Cotton Tank Top', 'tops'::product_category, 499, 'INR', array['asset://product_020'], 'Nilaya White Cotton Tank Top — style preview, not a fit guarantee.', array['XS','S','M','L','XL'], array['tank'], true),
  ('22222222-0000-0000-0000-000000000021', 'Mehra & Co.', 'Pastel Pink Blouse', 'tops'::product_category, 1299, 'INR', array['asset://product_021'], 'Mehra & Co. Pastel Pink Blouse — style preview, not a fit guarantee.', array['XS','S','M','L','XL'], array['blouse'], true),
  ('22222222-0000-0000-0000-000000000022', 'Threadline', 'Mustard Knit Sweater', 'tops'::product_category, 1599, 'INR', array['asset://product_022'], 'Threadline Mustard Knit Sweater — style preview, not a fit guarantee.', array['S','M','L','XL','XXL'], array['sweater','knit'], true),
  ('22222222-0000-0000-0000-000000000023', 'Nilaya', 'Lavender Cardigan', 'tops'::product_category, 1399, 'INR', array['asset://product_023'], 'Nilaya Lavender Cardigan — style preview, not a fit guarantee.', array['XS','S','M','L','XL'], array['cardigan'], true),
  ('22222222-0000-0000-0000-000000000024', 'Mehra & Co.', 'Teal Tunic Top', 'tops'::product_category, 1199, 'INR', array['asset://product_024'], 'Mehra & Co. Teal Tunic Top — style preview, not a fit guarantee.', array['XS','S','M','L','XL'], array['tunic'], true),
  ('22222222-0000-0000-0000-000000000025', 'Urbanco', 'White Street Sneakers', 'shoes'::product_category, 1999, 'INR', array['asset://product_025'], 'Urbanco White Street Sneakers — style preview, not a fit guarantee.', array['UK 6','UK 7','UK 8','UK 9','UK 10','UK 11'], array['sneakers'], true),
  ('22222222-0000-0000-0000-000000000026', 'Threadline', 'Tan Chelsea Boots', 'shoes'::product_category, 2999, 'INR', array['asset://product_026'], 'Threadline Tan Chelsea Boots — style preview, not a fit guarantee.', array['UK 6','UK 7','UK 8','UK 9','UK 10','UK 11'], array['boots'], true),
  ('22222222-0000-0000-0000-000000000027', 'Kardo', 'Brown Leather Loafers', 'shoes'::product_category, 2499, 'INR', array['asset://product_027'], 'Kardo Brown Leather Loafers — style preview, not a fit guarantee.', array['UK 6','UK 7','UK 8','UK 9','UK 10','UK 11'], array['loafers'], true),
  ('22222222-0000-0000-0000-000000000028', 'Nilaya', 'Beige Strappy Sandals', 'shoes'::product_category, 1299, 'INR', array['asset://product_028'], 'Nilaya Beige Strappy Sandals — style preview, not a fit guarantee.', array['UK 3','UK 4','UK 5','UK 6','UK 7','UK 8'], array['sandals'], true),
  ('22222222-0000-0000-0000-000000000029', 'Urbanco', 'Red Running Shoes', 'shoes'::product_category, 2299, 'INR', array['asset://product_029'], 'Urbanco Red Running Shoes — style preview, not a fit guarantee.', array['UK 6','UK 7','UK 8','UK 9','UK 10','UK 11'], array['running','sport'], true),
  ('22222222-0000-0000-0000-000000000030', 'Mehra & Co.', 'Black Block Heels', 'shoes'::product_category, 1799, 'INR', array['asset://product_030'], 'Mehra & Co. Black Block Heels — style preview, not a fit guarantee.', array['UK 3','UK 4','UK 5','UK 6','UK 7','UK 8'], array['heels'], true),
  ('22222222-0000-0000-0000-000000000031', 'Threadline', 'Gray Slides', 'shoes'::product_category, 699, 'INR', array['asset://product_031'], 'Threadline Gray Slides — style preview, not a fit guarantee.', array['UK 6','UK 7','UK 8','UK 9','UK 10','UK 11'], array['slides'], true),
  ('22222222-0000-0000-0000-000000000032', 'Kardo', 'Tan Formal Oxford Shoes', 'shoes'::product_category, 2799, 'INR', array['asset://product_032'], 'Kardo Tan Formal Oxford Shoes — style preview, not a fit guarantee.', array['UK 6','UK 7','UK 8','UK 9','UK 10','UK 11'], array['formal'], true),
  ('22222222-0000-0000-0000-000000000033', 'Nilaya', 'Canvas Tote Bag', 'bags'::product_category, 999, 'INR', array['asset://product_033'], 'Nilaya Canvas Tote Bag — style preview, not a fit guarantee.', array['One Size'], array['tote'], true),
  ('22222222-0000-0000-0000-000000000034', 'Urbanco', 'Black Laptop Backpack', 'bags'::product_category, 1899, 'INR', array['asset://product_034'], 'Urbanco Black Laptop Backpack — style preview, not a fit guarantee.', array['One Size'], array['backpack'], true),
  ('22222222-0000-0000-0000-000000000035', 'Threadline', 'Brown Crossbody Bag', 'bags'::product_category, 1499, 'INR', array['asset://product_035'], 'Threadline Brown Crossbody Bag — style preview, not a fit guarantee.', array['One Size'], array['crossbody'], true),
  ('22222222-0000-0000-0000-000000000036', 'Mehra & Co.', 'Black Quilted Handbag', 'bags'::product_category, 2199, 'INR', array['asset://product_036'], 'Mehra & Co. Black Quilted Handbag — style preview, not a fit guarantee.', array['One Size'], array['handbag'], true),
  ('22222222-0000-0000-0000-000000000037', 'Kardo', 'Navy Travel Duffel', 'bags'::product_category, 1699, 'INR', array['asset://product_037'], 'Kardo Navy Travel Duffel — style preview, not a fit guarantee.', array['One Size'], array['duffel'], true),
  ('22222222-0000-0000-0000-000000000038', 'Nilaya', 'Gold Evening Clutch', 'bags'::product_category, 1299, 'INR', array['asset://product_038'], 'Nilaya Gold Evening Clutch — style preview, not a fit guarantee.', array['One Size'], array['clutch'], true),
  ('22222222-0000-0000-0000-000000000039', 'Nilaya', 'Maroon Embroidered Kurti', 'ethnic'::product_category, 1399, 'INR', array['asset://product_039'], 'Nilaya Maroon Embroidered Kurti — style preview, not a fit guarantee.', array['XS','S','M','L','XL'], array['kurti','ethnic'], true),
  ('22222222-0000-0000-0000-000000000040', 'Mehra & Co.', 'Cream Kurta Set', 'ethnic'::product_category, 2299, 'INR', array['asset://product_040'], 'Mehra & Co. Cream Kurta Set — style preview, not a fit guarantee.', array['S','M','L','XL','XXL'], array['kurta set','ethnic'], true),
  ('22222222-0000-0000-0000-000000000041', 'Arqive', 'Ivory Sherwani', 'ethnic'::product_category, 4999, 'INR', array['asset://product_041'], 'Arqive Ivory Sherwani — style preview, not a fit guarantee.', array['S','M','L','XL','XXL'], array['sherwani','wedding'], true),
  ('22222222-0000-0000-0000-000000000042', 'Nilaya', 'Teal Palazzo Set', 'ethnic'::product_category, 1799, 'INR', array['asset://product_042'], 'Nilaya Teal Palazzo Set — style preview, not a fit guarantee.', array['XS','S','M','L','XL'], array['palazzo','ethnic'], true),
  ('22222222-0000-0000-0000-000000000043', 'Arqive', 'Maroon Nehru Jacket', 'ethnic'::product_category, 2499, 'INR', array['asset://product_043'], 'Arqive Maroon Nehru Jacket — style preview, not a fit guarantee.', array['S','M','L','XL','XXL'], array['nehru jacket','ethnic'], true),
  ('22222222-0000-0000-0000-000000000044', 'Mehra & Co.', 'Pink Anarkali Kurti', 'ethnic'::product_category, 1599, 'INR', array['asset://product_044'], 'Mehra & Co. Pink Anarkali Kurti — style preview, not a fit guarantee.', array['XS','S','M','L','XL'], array['anarkali','ethnic'], true),
  ('22222222-0000-0000-0000-000000000045', 'Urbanco', 'Sage Green Co-ord Set', 'casual'::product_category, 1999, 'INR', array['asset://product_045'], 'Urbanco Sage Green Co-ord Set — style preview, not a fit guarantee.', array['S','M','L','XL','XXL'], array['co-ord'], true),
  ('22222222-0000-0000-0000-000000000046', 'Threadline', 'Black Joggers', 'casual'::product_category, 1099, 'INR', array['asset://product_046'], 'Threadline Black Joggers — style preview, not a fit guarantee.', array['S','M','L','XL','XXL'], array['joggers'], true),
  ('22222222-0000-0000-0000-000000000047', 'Kardo', 'Light-Wash Denim Skirt', 'casual'::product_category, 1199, 'INR', array['asset://product_047'], 'Kardo Light-Wash Denim Skirt — style preview, not a fit guarantee.', array['XS','S','M','L','XL'], array['skirt','denim'], true),
  ('22222222-0000-0000-0000-000000000048', 'Nilaya', 'Yellow Summer Dress', 'casual'::product_category, 1499, 'INR', array['asset://product_048'], 'Nilaya Yellow Summer Dress — style preview, not a fit guarantee.', array['XS','S','M','L','XL'], array['dress','summer'], true)
on conflict (id) do update set
  brand = excluded.brand, name = excluded.name, category = excluded.category,
  price = excluded.price, images = excluded.images, sizes = excluded.sizes,
  tags = excluded.tags, is_active = excluded.is_active;
