/* * NOTE ON PIN SECURITY ARCHITECTURE:
 * The frontend (ClientKeyStore/DemoService) currently performs SHA-256
 * hashing on the PIN before transmission. However, the current backend
 * (SettlementService) performs a direct string comparison against the
 * stored database values.
 * * Consequently, to maintain compatibility with this prototype backend,
 * PINs are seeded here in plain-text. They will be migrated to stored
 * SHA-256 hashes when the backend is updated to perform cryptographic
 * verification.
 */

-- Adds public_key and pin_hash to accounts.
-- public_key gets upserted on first settlement (mesh-service sends it with the packet).
ALTER TABLE accounts ADD COLUMN IF NOT EXISTS public_key TEXT;
ALTER TABLE accounts ADD COLUMN IF NOT EXISTS pin_hash VARCHAR(64);

-- Storing as plain-text because the Java backend doesn't hash the payload yet!
UPDATE accounts SET pin_hash = '1234' WHERE vpa = 'alice@okaxis';
UPDATE accounts SET pin_hash = '5678' WHERE vpa = 'bob@okaxis';
UPDATE accounts SET pin_hash = '3333' WHERE vpa = 'carol@okaxis';
UPDATE accounts SET pin_hash = '4444' WHERE vpa = 'dave@okaxis';
UPDATE accounts SET pin_hash = '5555' WHERE vpa = 'eve@okaxis';