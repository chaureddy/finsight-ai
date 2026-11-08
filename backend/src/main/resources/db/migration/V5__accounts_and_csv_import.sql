ALTER TABLE financial_transaction DROP CONSTRAINT financial_transaction_external_pair_check;

ALTER TABLE financial_transaction
  ADD CONSTRAINT financial_transaction_external_pair_check
  CHECK ((source_provider IS NULL AND external_reference IS NULL) OR
         (source_provider IN ('PAYPAL', 'CSV_IMPORT') AND external_reference IS NOT NULL));

ALTER TABLE paypal_import_record DROP CONSTRAINT paypal_import_state_check;

ALTER TABLE paypal_import_record
  ADD CONSTRAINT paypal_import_state_check
  CHECK (
    (import_state = 'IMPORTED' AND financial_transaction_id IS NOT NULL) OR
    (import_state = 'REVIEW' AND financial_transaction_id IS NULL AND review_reason IS NOT NULL) OR
    (import_state = 'DISMISSED')
  );

CREATE TABLE user_account_connection (
  id UUID PRIMARY KEY,
  user_id UUID NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
  provider VARCHAR(32) NOT NULL,
  account_name VARCHAR(120) NOT NULL,
  account_identifier VARCHAR(120),
  status VARCHAR(20) NOT NULL CHECK (status IN ('CONNECTED', 'DISCONNECTED', 'ERROR')),
  encrypted_credentials TEXT,
  metadata TEXT,
  created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
  updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
  CONSTRAINT user_account_provider_uq UNIQUE(user_id, provider, account_name)
);

CREATE INDEX user_account_connection_user_idx ON user_account_connection(user_id);

