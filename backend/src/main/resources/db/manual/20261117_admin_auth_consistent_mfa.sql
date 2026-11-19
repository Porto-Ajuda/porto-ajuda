-- Authentication-only migration. Safe to re-run; no credentials or OTP values are written.
BEGIN;

ALTER TABLE public.admin_auth_challenges
    ADD COLUMN IF NOT EXISTS email_required BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN IF NOT EXISTS sms_required BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN IF NOT EXISTS resend_attempts INTEGER NOT NULL DEFAULT 0;

ALTER TABLE public.admin_auth_challenges
    DROP CONSTRAINT IF EXISTS ck_admin_auth_resend_attempts;
ALTER TABLE public.admin_auth_challenges
    ADD CONSTRAINT ck_admin_auth_resend_attempts CHECK (resend_attempts BETWEEN 0 AND 3);

ALTER TABLE public.admin_auth_deliveries
    ADD COLUMN IF NOT EXISTS failure_category VARCHAR(40),
    ADD COLUMN IF NOT EXISTS provider_status INTEGER,
    ADD COLUMN IF NOT EXISTS sandbox_message_ciphertext BYTEA;

ALTER TABLE public.admin_auth_deliveries
    DROP CONSTRAINT IF EXISTS ck_admin_auth_provider_status;
ALTER TABLE public.admin_auth_deliveries
    ADD CONSTRAINT ck_admin_auth_provider_status
        CHECK (provider_status IS NULL OR provider_status BETWEEN 100 AND 599);

-- Expire active legacy challenges whose required factors cannot be delivered. Keep verified
-- challenges and existing privileged sessions intact.
UPDATE public.admin_auth_challenges c
   SET state = 'FAILED',
       invalidated_at = COALESCE(invalidated_at, CURRENT_TIMESTAMP),
       delivery_failure_at = COALESCE(delivery_failure_at, CURRENT_TIMESTAMP),
       email_code_hash = NULL,
       sms_code_hash = NULL
 WHERE c.state IN ('ISSUING', 'PENDING')
   AND (NOT EXISTS (SELECT 1 FROM public.admin_auth_deliveries d
                     WHERE d.challenge_id = c.id AND d.channel = 'EMAIL')
        OR NOT EXISTS (SELECT 1 FROM public.admin_auth_deliveries d
                        WHERE d.challenge_id = c.id AND d.channel = 'SMS'));

UPDATE public.admin_auth_deliveries d
   SET state = 'FAILED', encrypted_code = NULL, lease_until = NULL,
       sandbox_message_ciphertext = NULL, updated_at = CURRENT_TIMESTAMP
 WHERE d.state IN ('PENDING', 'PROCESSING')
   AND EXISTS (SELECT 1 FROM public.admin_auth_challenges c
                WHERE c.id = d.challenge_id AND c.state = 'FAILED'
                  AND c.delivery_failure_at IS NOT NULL);

COMMIT;
