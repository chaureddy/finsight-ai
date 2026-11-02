ALTER TABLE financial_transaction
  ADD COLUMN source_provider VARCHAR(20);
ALTER TABLE financial_transaction ADD COLUMN external_reference VARCHAR(64);
ALTER TABLE financial_transaction ADD COLUMN currency_code VARCHAR(3) NOT NULL DEFAULT 'INR';

ALTER TABLE financial_transaction
  ADD CONSTRAINT financial_transaction_external_pair_check
  CHECK ((source_provider IS NULL AND external_reference IS NULL) OR
         (source_provider = 'PAYPAL' AND external_reference IS NOT NULL));

CREATE TABLE paypal_sandbox_connection (
  id UUID PRIMARY KEY,
  user_id UUID NOT NULL UNIQUE REFERENCES app_user(id) ON DELETE CASCADE,
  environment VARCHAR(12) NOT NULL CHECK (environment = 'SANDBOX'),
  created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
  last_verified_at TIMESTAMP WITH TIME ZONE NOT NULL,
  CONSTRAINT paypal_sandbox_single_owner UNIQUE(environment)
);

CREATE TABLE paypal_import_record (
  id UUID PRIMARY KEY,
  connection_id UUID NOT NULL REFERENCES paypal_sandbox_connection(id) ON DELETE CASCADE,
  paypal_transaction_id VARCHAR(24),
  source_fingerprint VARCHAR(64) NOT NULL,
  occurred_on DATE,
  amount NUMERIC(19,2),
  currency_code VARCHAR(3),
  event_code VARCHAR(5),
  paypal_status VARCHAR(1),
  import_state VARCHAR(12) NOT NULL CHECK (import_state IN ('IMPORTED','REVIEW')),
  review_reason VARCHAR(40),
  financial_transaction_id UUID UNIQUE REFERENCES financial_transaction(id) ON DELETE CASCADE,
  created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
  CONSTRAINT paypal_import_fingerprint_uq UNIQUE(connection_id, source_fingerprint),
  CONSTRAINT paypal_import_state_check CHECK (
    (import_state = 'IMPORTED' AND financial_transaction_id IS NOT NULL AND review_reason IS NULL) OR
    (import_state = 'REVIEW' AND financial_transaction_id IS NULL AND review_reason IS NOT NULL)
  )
);

CREATE INDEX paypal_import_connection_created_idx ON paypal_import_record(connection_id, created_at DESC);
