CREATE TABLE financial_asset (
  id UUID PRIMARY KEY,
  user_id UUID NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
  name VARCHAR(120) NOT NULL,
  category VARCHAR(30) NOT NULL CHECK (category IN ('Cash','Bank Account','Property','Vehicle','Gold','Other')),
  current_value NUMERIC(19,2) NOT NULL CHECK (current_value >= 0),
  purchase_value NUMERIC(19,2) NOT NULL CHECK (purchase_value >= 0),
  purchase_date DATE NOT NULL,
  description VARCHAR(1000),
  created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
  updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()
);
CREATE INDEX financial_asset_owner_idx ON financial_asset(user_id, created_at DESC);

CREATE TABLE financial_liability (
  id UUID PRIMARY KEY,
  user_id UUID NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
  name VARCHAR(120) NOT NULL,
  category VARCHAR(30) NOT NULL CHECK (category IN ('Personal Loan','Home Loan','Vehicle Loan','Education Loan','Credit Card','Other')),
  principal_amount NUMERIC(19,2) NOT NULL CHECK (principal_amount >= 0),
  outstanding_amount NUMERIC(19,2) NOT NULL CHECK (outstanding_amount >= 0),
  interest_rate NUMERIC(7,4) NOT NULL CHECK (interest_rate >= 0 AND interest_rate <= 100),
  monthly_emi NUMERIC(19,2) NOT NULL CHECK (monthly_emi >= 0),
  start_date DATE NOT NULL,
  due_date DATE,
  description VARCHAR(1000),
  created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
  updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()
);
CREATE INDEX financial_liability_owner_idx ON financial_liability(user_id, created_at DESC);

CREATE TABLE financial_investment (
  id UUID PRIMARY KEY,
  user_id UUID NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
  name VARCHAR(120) NOT NULL,
  category VARCHAR(30) NOT NULL CHECK (category IN ('Stocks','Mutual Funds','Fixed Deposits','Bonds','Gold','Other')),
  invested_amount NUMERIC(19,2) NOT NULL CHECK (invested_amount >= 0),
  current_value NUMERIC(19,2) NOT NULL CHECK (current_value >= 0),
  purchase_date DATE NOT NULL,
  description VARCHAR(1000),
  created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
  updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()
);
CREATE INDEX financial_investment_owner_idx ON financial_investment(user_id, created_at DESC);

CREATE TABLE insurance_policy (
  id UUID PRIMARY KEY,
  user_id UUID NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
  provider VARCHAR(120) NOT NULL,
  policy_name VARCHAR(120) NOT NULL,
  category VARCHAR(20) NOT NULL CHECK (category IN ('Health','Life','Vehicle','Property','Other')),
  coverage_amount NUMERIC(19,2) NOT NULL CHECK (coverage_amount >= 0),
  premium_amount NUMERIC(19,2) NOT NULL CHECK (premium_amount >= 0),
  premium_frequency VARCHAR(20) NOT NULL CHECK (premium_frequency IN ('Monthly','Quarterly','Semiannual','Annual')),
  start_date DATE NOT NULL,
  renewal_date DATE,
  description VARCHAR(1000),
  created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
  updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()
);
CREATE INDEX insurance_policy_owner_idx ON insurance_policy(user_id, renewal_date);
