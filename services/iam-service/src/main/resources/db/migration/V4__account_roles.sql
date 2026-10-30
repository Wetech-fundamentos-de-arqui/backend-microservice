ALTER TABLE accounts ADD COLUMN role VARCHAR(20) NOT NULL DEFAULT 'USER';
-- Catalog admin for local runs: admin@weride.test / Admin1234!
INSERT INTO accounts (user_name, password, status, role)
VALUES ('admin@weride.test', '$2a$10$tH4y32gegJD4qT8wmMphK.NLcpN.O7a2VjMa/.ySv/p7EqphfPpAy', 'ACTIVE', 'ADMIN');
