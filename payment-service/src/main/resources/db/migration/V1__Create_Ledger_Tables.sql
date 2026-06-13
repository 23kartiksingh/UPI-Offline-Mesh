CREATE TABLE accounts (
                          vpa VARCHAR(255) PRIMARY KEY,
                          holder_name VARCHAR(255) NOT NULL,
                          balance NUMERIC(19, 2) NOT NULL,
                          version BIGINT NOT NULL
);

CREATE TABLE transactions (
                              id BIGSERIAL PRIMARY KEY,
                              packet_hash VARCHAR(64) NOT NULL UNIQUE,
                              sender_vpa VARCHAR(255) NOT NULL,
                              receiver_vpa VARCHAR(255) NOT NULL,
                              amount NUMERIC(19, 2) NOT NULL,
                              signed_at TIMESTAMP WITH TIME ZONE NOT NULL,
                              settled_at TIMESTAMP WITH TIME ZONE NOT NULL,
                              bridge_node_id VARCHAR(255) NOT NULL,
                              hop_count INT NOT NULL
);

-- Unique index to guarantee idempotency at the database layer (defense in depth)
CREATE UNIQUE INDEX idx_packet_hash ON transactions (packet_hash);

-- Seed data for the entire mesh network (Everyone starts with ₹10,000)
INSERT INTO accounts (vpa, holder_name, balance, version) VALUES ('alice@okaxis', 'Alice (Offline)', 10000.00, 0);
INSERT INTO accounts (vpa, holder_name, balance, version) VALUES ('bob@okaxis', 'Bob (Offline)', 10000.00, 0);
INSERT INTO accounts (vpa, holder_name, balance, version) VALUES ('carol@okaxis', 'Carol (Offline)', 10000.00, 0);
INSERT INTO accounts (vpa, holder_name, balance, version) VALUES ('dave@okaxis', 'Dave (4G Bridge)', 10000.00, 0);
INSERT INTO accounts (vpa, holder_name, balance, version) VALUES ('eve@okaxis', 'Eve (4G Bridge)', 10000.00, 0);