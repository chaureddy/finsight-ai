import { useState, type FormEvent } from 'react';
import { NavLink } from 'react-router-dom';
import { CheckCircle2, KeyRound, LogOut, Settings, ShieldCheck } from 'lucide-react';
import { apiErrorMessage, authApi, type UserProfile } from './api';

export default function ProfilePage({
  user,
  onProfileUpdated,
  onSignOut,
}: {
  user: UserProfile | null;
  onProfileUpdated: (updated: UserProfile) => void;
  onSignOut: () => void;
}) {
  const [name, setName] = useState(user?.displayName || '');
  const [saving, setSaving] = useState(false);
  const [toast, setToast] = useState('');
  const [error, setError] = useState('');

  async function handleSaveProfile(e: FormEvent) {
    e.preventDefault();
    if (!name.trim()) return;
    setSaving(true);
    setError('');
    try {
      const updated = await authApi.updateProfile(name.trim());
      onProfileUpdated(updated);
      setToast('Profile updated successfully');
      setTimeout(() => setToast(''), 3000);
    } catch (err) {
      setError(apiErrorMessage(err));
    } finally {
      setSaving(false);
    }
  }

  const initials = (name || user?.displayName || 'User')
    .split(/\s+/)
    .slice(0, 2)
    .map(p => p[0]?.toUpperCase())
    .join('');

  return (
    <div className="content profile-page">
      <div className="welcome-row">
        <div>
          <div className="eyebrow">USER ACCOUNT & IDENTITY</div>
          <h1>User Profile</h1>
          <p className="subheading">Manage your personal credentials, display name, and active session.</p>
        </div>
        <div className="profile-top-actions">
          <NavLink to="/settings" className="btn-secondary">
            <Settings size={15} /> Account Settings
          </NavLink>
          <button className="btn-secondary btn-danger" onClick={onSignOut}>
            <LogOut size={15} /> Sign out
          </button>
        </div>
      </div>

      {toast && (
        <div className="toast">
          <ShieldCheck size={16} />
          {toast}
        </div>
      )}
      {error && (
        <div className="api-error" role="alert">
          {error}
        </div>
      )}

      <div className="profile-layout-grid">
        {/* Left Column: User Card & Display Name Edit */}
        <section className="panel profile-card">
          <div className="profile-header-banner">
            <div className="profile-avatar-large">{initials}</div>
            <div className="profile-banner-info">
              <h2>{user?.displayName || 'FinSight User'}</h2>
              <span className="profile-email-badge">{user?.email || 'demo@finsight.ai'}</span>
              <span className="profile-status-pill">
                <CheckCircle2 size={12} /> {user ? 'Verified Account' : 'Demo Workspace'}
              </span>
            </div>
          </div>

          <form onSubmit={handleSaveProfile} className="profile-form">
            <label>
              Display Name
              <input
                type="text"
                required
                maxLength={100}
                value={name}
                onChange={e => setName(e.target.value)}
                placeholder="Your full name"
                disabled={saving}
              />
            </label>

            <label>
              Registered Email (Primary Identity)
              <input
                type="email"
                disabled
                value={user?.email || 'demo@finsight.ai'}
                readOnly
              />
              <small className="field-note">
                Your email is used for authenticated session sign-in and cannot be changed without security verification.
              </small>
            </label>

            <div className="profile-form-actions">
              <button
                className="btn-primary"
                type="submit"
                disabled={saving || !name.trim() || name.trim() === user?.displayName}
              >
                {saving ? 'Saving changes…' : 'Save profile changes'}
              </button>
            </div>
          </form>
        </section>

        {/* Right Column: Account & Security Overview */}
        <div className="profile-side-column">
          <section className="panel profile-security-card">
            <div className="settings-card-header">
              <ShieldCheck size={20} />
              <div>
                <h2>Account Information</h2>
                <p>System status and session security attributes.</p>
              </div>
            </div>

            <div className="account-info-grid">
              <div className="account-info-item">
                <span>Account Status</span>
                <strong>{user ? 'Active · Verified' : 'Demo Preview'}</strong>
              </div>
              <div className="account-info-item">
                <span>Session Type</span>
                <strong>HttpOnly Cookie (JSESSIONID)</strong>
              </div>
              <div className="account-info-item">
                <span>CSRF Defense</span>
                <strong>Double-Submit Token (X-XSRF-TOKEN)</strong>
              </div>
              <div className="account-info-item">
                <span>Password Security</span>
                <strong>BCrypt 12 Rounds Salted Hash</strong>
              </div>
              <div className="account-info-item">
                <span>Credential Encryption</span>
                <strong>AES-256-GCM at rest</strong>
              </div>
              <div className="account-info-item">
                <span>Accounting Base Currency</span>
                <strong>INR (₹) · Indian Rupee</strong>
              </div>
            </div>
          </section>

          <section className="panel profile-shortcuts-card">
            <div className="settings-card-header">
              <KeyRound size={20} />
              <div>
                <h2>Quick Actions</h2>
                <p>Manage integrations and download transaction history.</p>
              </div>
            </div>

            <div className="profile-quick-links">
              <NavLink to="/accounts" className="quick-link-item">
                <div>
                  <strong>Connected Accounts</strong>
                  <small>Manage PayPal Sandbox and bank statements</small>
                </div>
                <span>→</span>
              </NavLink>
              <NavLink to="/settings" className="quick-link-item">
                <div>
                  <strong>Data Export & Preferences</strong>
                  <small>Download transactions CSV and inspect conventions</small>
                </div>
                <span>→</span>
              </NavLink>
            </div>
          </section>
        </div>
      </div>
    </div>
  );
}
