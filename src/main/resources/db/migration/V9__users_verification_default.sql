-- V2 dejó 'PENDIENTE' como default, pero el enum VerificationState usa PENDING.
UPDATE users SET verification_state = 'PENDING' WHERE verification_state = 'PENDIENTE';

ALTER TABLE users ALTER COLUMN verification_state SET DEFAULT 'PENDING';
