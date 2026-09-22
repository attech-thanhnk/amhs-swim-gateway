-- ============================================================
-- Migration: remove the legacy message-type routing field
-- Date: 2026-09-22
-- ============================================================
-- Routing is resolved by recipient address. The legacy message_type field
-- was only used by the removed diagnostic endpoint and had no runtime role.

START TRANSACTION;

ALTER TABLE `routing`
    DROP COLUMN `message_type`;

COMMIT;
