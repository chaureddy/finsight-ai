CREATE TABLE financial_budget (
  id UUID PRIMARY KEY,
  user_id UUID NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
  name VARCHAR(120) NOT NULL,
  category VARCHAR(60) NOT NULL,
  monthly_limit NUMERIC(19,2) NOT NULL CHECK (monthly_limit > 0),
  start_date DATE NOT NULL,
  end_date DATE NOT NULL,
  alert_threshold_percentage NUMERIC(5,2) NOT NULL DEFAULT 80 CHECK (alert_threshold_percentage > 0 AND alert_threshold_percentage <= 100),
  created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
  updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
  CHECK (end_date >= start_date),
  CONSTRAINT financial_budget_period_unique UNIQUE(user_id, category, start_date, end_date)
);
CREATE INDEX financial_budget_owner_period_idx ON financial_budget(user_id, start_date, end_date);

CREATE TABLE financial_goal (
  id UUID PRIMARY KEY,
  user_id UUID NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
  name VARCHAR(120) NOT NULL,
  category VARCHAR(40) NOT NULL,
  target_amount NUMERIC(19,2) NOT NULL CHECK (target_amount > 0),
  current_amount NUMERIC(19,2) NOT NULL DEFAULT 0 CHECK (current_amount >= 0),
  target_date DATE NOT NULL,
  priority VARCHAR(10) NOT NULL CHECK (priority IN ('LOW','MEDIUM','HIGH')),
  description VARCHAR(1000),
  created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
  updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()
);
CREATE INDEX financial_goal_owner_target_idx ON financial_goal(user_id, target_date);

CREATE TABLE recurring_transaction (
  id UUID PRIMARY KEY,
  user_id UUID NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
  title VARCHAR(120) NOT NULL,
  transaction_type VARCHAR(10) NOT NULL CHECK (transaction_type IN ('INCOME','EXPENSE')),
  amount NUMERIC(19,2) NOT NULL CHECK (amount > 0),
  category VARCHAR(60) NOT NULL,
  frequency VARCHAR(10) NOT NULL CHECK (frequency IN ('DAILY','WEEKLY','MONTHLY','YEARLY')),
  next_due_date DATE NOT NULL,
  end_date DATE,
  active BOOLEAN NOT NULL DEFAULT TRUE,
  created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
  updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()
);
CREATE INDEX recurring_transaction_owner_due_idx ON recurring_transaction(user_id, active, next_due_date);

CREATE TABLE recurring_occurrence (
  id UUID PRIMARY KEY,
  user_id UUID NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
  recurring_transaction_id UUID NOT NULL REFERENCES recurring_transaction(id) ON DELETE CASCADE,
  occurrence_date DATE NOT NULL,
  transaction_id UUID REFERENCES financial_transaction(id) ON DELETE SET NULL,
  created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
  CONSTRAINT recurring_occurrence_idempotency UNIQUE(recurring_transaction_id, occurrence_date)
);
CREATE INDEX recurring_occurrence_owner_idx ON recurring_occurrence(user_id, occurrence_date DESC);
