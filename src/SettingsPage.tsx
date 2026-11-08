import { useState, type FormEvent } from 'react';
import { CheckCircle2, Download, KeyRound, Lock, LogOut, ShieldCheck, User } from 'lucide-react';
import { apiErrorMessage, authApi, financeApi, type UserProfile } from './api';

export default function SettingsPage({
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
  const [exporting, setExporting] = useState(false);

  async function handleSaveProfile(e: FormEvent) {
    e.preventDefault();
    if (!name.trim()) return;
    setSaving(true); setError('');
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

  async function handleExportCsv() {
    setExporting(true); setError('');
    try {
      const csv = await financeApi.exportCsv();
      const blob = new Blob([csv], { type: 'text/csv;charset=utf-8;' });
      const url = URL.createObjectURL(blob);
      const link = document.createElement('a');
      link.setAttribute('href', url);
      link.setAttribute('download', 'finsight-transactions.csv');
      document.body.appendChild(link);
      link.click();
      document.body.removeChild(link);
      setToast('Transaction export downloaded');
      setTimeout(() => setToast(''), 3000);
    } catch (err) {
      setError(apiErrorMessage(err));
    } finally {
      setExporting(false);
    }
  }

  return (
    <div className="content settings-page">
      <div className="welcome-row">
        <div>
          <div className="eyebrow">PREFERENCES & ACCOUNT</div>
          <h1>Settings</h1>
          <p className="subheading">Manage your profile, security posture, and financial data export.</p>
        </div>
      </div>

      {toast && <div className="toast"><ShieldCheck size={16} />{toast}</div>}
      {error && <div className="api-error" role="alert">{error}</div>}

      <div className="settings-sections">
        {/* Profile Card */}
        <section className="panel settings-card">
          <div className="settings-card-header">
            <User size={20} />
            <div>
              <h2>Profile Details</h2>
              <p>Your personal account identifiers in FinSight.</p>
            </div>
          </div>

          <form onSubmit={handleSaveProfile} className="settings-form">
            <label>
              Display Name
              <input
                type="text"
                required
                maxLength={100}
                value={name}
                onChange={e => setName(e.target.value)}
              />
            </label>

            <label>
              Registered Email (Primary Account)
              <input
                type="email"
                disabled
                value={user?.email || 'demo@example.com'}
              />
              <small className="field-note">Email changes require re-authentication.</small>
            </label>

            <div className="settings-actions">
              <button className="btn-primary" type="submit" disabled={saving || !name.trim()}>
                {saving ? 'Saving…' : 'Save profile'}
              </button>
            </div>
          </form>
        </section>

        {/* Security & Cryptography Card */}
        <section className="panel settings-card">
          <div className="settings-card-header">
            <Lock size={20} />
            <div>
              <h2>Security & Encryption</h2>
              <p>Active defenses protecting your financial data.</p>
            </div>
          </div>

          <div className="security-badges-list">
            <div className="security-item">
              <ShieldCheck size={18} className="text-emerald" />
              <div>
                <strong>OAuth Tokens & Credentials</strong>
                <p>Encrypted at rest using AES-256-GCM with secure random 96-bit initialization vectors.</p>
              </div>
            </div>
            <div className="security-item">
              <KeyRound size={18} className="text-emerald" />
              <div>
                <strong>Password Protection</strong>
                <p>Salted and hashed with BCrypt (12 cost factor rounds). Passwords are never stored in plaintext.</p>
              </div>
            </div>
            <div className="security-item">
              <CheckCircle2 size={18} className="text-emerald" />
              <div>
                <strong>Session & CSRF Protection</strong>
                <p>HttpOnly session cookies with SameSite=Lax and rotated per-session double-submit CSRF tokens.</p>
              </div>
            </div>
          </div>
        </section>

        {/* Preferences & Conventions */}
        <section className="panel settings-card">
          <div className="settings-card-header">
            <ShieldCheck size={20} />
            <div>
              <h2>Financial Conventions</h2>
              <p>Accounting rules applied across dashboards and calculations.</p>
            </div>
          </div>

          <div className="conventions-grid">
            <div className="convention-item">
              <span>Primary Currency</span>
              <strong>INR (₹) · Indian Rupee</strong>
            </div>
            <div className="convention-item">
              <span>Accounting Basis</span>
              <strong>Cash Basis (Actual transactions)</strong>
            </div>
            <div className="convention-item">
              <span>Fiscal Period</span>
              <strong>Calendar Month (1st through Month End)</strong>
            </div>
            <div className="convention-item">
              <span>Asset/Investment Separation</span>
              <strong>Strictly Disjoint (No double-counting)</strong>
            </div>
          </div>
        </section>

        {/* Data Portability Card */}
        <section className="panel settings-card">
          <div className="settings-card-header">
            <Download size={20} />
            <div>
              <h2>Data Portability & Export</h2>
              <p>Download a complete archive of your financial transaction history.</p>
            </div>
          </div>

          <div className="export-box">
            <p>Export all manual, PayPal Sandbox, and CSV-imported transactions in standard RFC 4180 CSV format.</p>
            <button className="btn-secondary" onClick={handleExportCsv} disabled={exporting}>
              <Download size={15} /> {exporting ? 'Generating export…' : 'Export all transactions (CSV)'}
            </button>
          </div>
        </section>

        {/* Sign Out Card */}
        <section className="panel settings-card danger-card">
          <div className="settings-card-header">
            <LogOut size={20} />
            <div>
              <h2>Session Management</h2>
              <p>Sign out of your FinSight workspace on this device.</p>
            </div>
          </div>
          <button className="btn-secondary btn-danger" onClick={onSignOut}>
            <LogOut size={15} /> Sign out
          </button>
        </section>
      </div>
    </div>
  );
}

