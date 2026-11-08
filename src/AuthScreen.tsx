import { useEffect, useState } from 'react';
import { Activity, ArrowRight, ShieldCheck } from 'lucide-react';

export default function AuthScreen({
  onLogin,
  onRegister,
  onDemo,
  loading,
  error,
  initialMode = 'login',
}: {
  onLogin: (email: string, password: string) => Promise<void>;
  onRegister: (input: { email: string; password: string; displayName: string }) => Promise<void>;
  onDemo: () => void;
  loading: boolean;
  error: string;
  initialMode?: 'login' | 'register';
}) {
  const [registerMode, setRegisterMode] = useState(initialMode === 'register');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [displayName, setDisplayName] = useState('');
  const [clientError, setClientError] = useState('');

  useEffect(() => {
    setRegisterMode(initialMode === 'register');
  }, [initialMode]);

  async function submit(event: React.FormEvent) {
    event.preventDefault();
    setClientError('');

    const cleanEmail = email.trim();
    const cleanPassword = password;
    const cleanName = displayName.trim();

    if (!cleanEmail) {
      setClientError('Please enter your email address.');
      return;
    }

    if (registerMode) {
      if (!cleanName) {
        setClientError('Please enter your display name.');
        return;
      }
      if (cleanPassword.length < 12) {
        setClientError('Password must be at least 12 characters long.');
        return;
      }
      await onRegister({ email: cleanEmail, password: cleanPassword, displayName: cleanName });
    } else {
      if (!cleanPassword) {
        setClientError('Please enter your password.');
        return;
      }
      await onLogin(cleanEmail, cleanPassword);
    }
  }

  function handleModeChange(isRegister: boolean) {
    setRegisterMode(isRegister);
    setClientError('');
  }

  const displayError = clientError || error;

  return (
    <div className="auth-screen">
      <div className="auth-card">
        <div className="auth-brand">
          <span className="brand-mark">
            <Activity size={20} />
          </span>
          <span>
            finsight<span className="brand-ai">.ai</span>
          </span>
        </div>

        {/* Tab switch at top of card */}
        <div className="auth-tabs" role="tablist" aria-label="Authentication modes">
          <button
            type="button"
            role="tab"
            aria-selected={!registerMode}
            className={`auth-tab ${!registerMode ? 'active' : ''}`}
            onClick={() => handleModeChange(false)}
          >
            Sign in
          </button>
          <button
            type="button"
            role="tab"
            aria-selected={registerMode}
            className={`auth-tab ${registerMode ? 'active' : ''}`}
            onClick={() => handleModeChange(true)}
          >
            Create account
          </button>
        </div>

        <div className="eyebrow">YOUR FINANCIAL WORKSPACE</div>
        <h1>{registerMode ? 'Create your account' : 'Welcome back'}</h1>
        <p className="auth-subtitle">
          {registerMode
            ? 'Start building a clearer picture of your finances.'
            : 'Sign in to see your personal financial picture.'}
        </p>

        <form onSubmit={submit} className="auth-form" noValidate={false}>
          {registerMode && (
            <label>
              Your name
              <input
                type="text"
                autoComplete="name"
                required
                maxLength={100}
                value={displayName}
                onChange={e => setDisplayName(e.target.value)}
                placeholder="Alex Smith"
                disabled={loading}
              />
            </label>
          )}

          <label>
            Email address
            <input
              type="email"
              autoComplete="email"
              required
              maxLength={254}
              value={email}
              onChange={e => setEmail(e.target.value)}
              placeholder="you@example.com"
              disabled={loading}
            />
          </label>

          <label>
            Password
            <input
              type="password"
              autoComplete={registerMode ? 'new-password' : 'current-password'}
              required
              minLength={registerMode ? 12 : 1}
              maxLength={72}
              value={password}
              onChange={e => setPassword(e.target.value)}
              placeholder={registerMode ? 'At least 12 characters' : 'Your password'}
              disabled={loading}
            />
            {registerMode && (
              <small className={password.length >= 12 ? 'text-success' : ''}>
                {password.length >= 12 ? '✓ Meets minimum 12-character requirement.' : 'Use at least 12 characters.'}
              </small>
            )}
          </label>

          {displayError && (
            <div role="alert" className="auth-error">
              {displayError}
            </div>
          )}

          <button className="btn-primary auth-submit" type="submit" disabled={loading}>
            {loading ? 'Please wait…' : registerMode ? 'Create account' : 'Sign in'}
            <ArrowRight size={16} />
          </button>
        </form>

        <div className="auth-switch">
          {registerMode ? 'Already have an account?' : 'New to FinSight?'}{' '}
          <button type="button" onClick={() => handleModeChange(!registerMode)}>
            {registerMode ? 'Sign in' : 'Create an account'}
          </button>
        </div>

        <div className="auth-divider">
          <span>OR</span>
        </div>

        <button className="btn-secondary demo-button" type="button" onClick={onDemo}>
          Explore labelled demo data
        </button>

        <p className="auth-security">
          <ShieldCheck size={14} /> Your account is protected with a private server session.
        </p>
      </div>

      <p className="auth-footer">FinSight AI · See Your Money. Understand Your Future.</p>
    </div>
  );
}
