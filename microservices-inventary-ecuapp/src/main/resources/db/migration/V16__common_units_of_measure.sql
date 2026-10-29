-- Common units so non-technical users can pick how a product is sold instead of creating units by hand.
-- Existing units with the same code (any case) are left untouched.
INSERT INTO unit_of_measure(id,code,name,dimension,decimal_scale)
SELECT v.id::uuid,v.code,v.name,v.dimension,v.decimal_scale
FROM (VALUES
 ('00000000-0000-0000-0000-000000000101','CAJA','Caja','COUNT',0),
 ('00000000-0000-0000-0000-000000000102','PAQUETE','Paquete','COUNT',0),
 ('00000000-0000-0000-0000-000000000103','DOCENA','Docena','COUNT',0),
 ('00000000-0000-0000-0000-000000000104','PAR','Par','COUNT',0),
 ('00000000-0000-0000-0000-000000000105','KG','Kilogramo','MASS',3),
 ('00000000-0000-0000-0000-000000000106','G','Gramo','MASS',0),
 ('00000000-0000-0000-0000-000000000107','LB','Libra','MASS',3),
 ('00000000-0000-0000-0000-000000000108','L','Litro','VOLUME',3),
 ('00000000-0000-0000-0000-000000000109','ML','Mililitro','VOLUME',0),
 ('00000000-0000-0000-0000-000000000110','GAL','Galón','VOLUME',3),
 ('00000000-0000-0000-0000-000000000111','M','Metro','LENGTH',2),
 ('00000000-0000-0000-0000-000000000112','CM','Centímetro','LENGTH',0),
 ('00000000-0000-0000-0000-000000000113','M2','Metro cuadrado','AREA',2),
 ('00000000-0000-0000-0000-000000000114','HORA','Hora','TIME',2)
) AS v(id,code,name,dimension,decimal_scale)
WHERE NOT EXISTS (SELECT 1 FROM unit_of_measure u WHERE lower(u.code)=lower(v.code))
  AND NOT EXISTS (SELECT 1 FROM unit_of_measure u WHERE u.id=v.id::uuid);

-- "EA" is the international code for "each"; show the base unit with a self-explanatory name.
UPDATE unit_of_measure SET name='Unidad (pieza)' WHERE id='00000000-0000-0000-0000-000000000001' AND name='Unidad';
