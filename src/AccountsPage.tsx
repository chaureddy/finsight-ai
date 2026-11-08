import { useEffect, useState, type FormEvent } from 'react';
import { NavLink } from 'react-router-dom';
import { ArrowUpRight, CheckCircle2, CloudDownload, FileSpreadsheet, Landmark, LoaderCircle, Plus, ShieldCheck, Trash2, Unplug, UploadCloud, Wallet } from 'lucide-react';
import { apiErrorMessage, financeApi, type AccountsOverview, type CsvImportResult } from './api';
import './accounts.css';

const money = (n: number) => new Intl.NumberFormat('en-IN', { style: 'currency', currency: 'INR', maximumFractionDigits: 0 }).format(n);

export default function AccountsPage({ enabled }: { enabled: boolean }) {
  const [data, setData] = useState<AccountsOverview | null>(null);
  const [loading, setLoading] = useState(enabled);
  const [error, setError] = useState('');
  const [toast, setToast] = useState('');
  const [csvModal, setCsvModal] = useState(false);
  const [csvContent, setCsvContent] = useState('');
  const [accountLabel, setAccountLabel] = useState('');
  const [importing, setImporting] = useState(false);
  const [importResult, setImportResult] = useState<CsvImportResult | null>(null);

  async function load() {
    if (!enabled) return;
    setLoading(true); setError('');
    try {
      const res = await financeApi.accounts();
      setData(res);
    } catch (e) {
      setError(apiErrorMessage(e));
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => { load(); }, [enabled]);

  async function disconnectAccount(id: string, name: string) {
    if (!window.confirm(`Disconnect "${name}"? This removes the sync connection.`)) return;
    try {
      await financeApi.deleteAccount(id);
      setToast(`Disconnected ${name}`);
      setTimeout(() => setToast(''), 3000);
      await load();
      window.dispatchEvent(new CustomEvent('finsight:financial-data-changed'));
    } catch (e) {
      setError(apiErrorMessage(e));
    }
  }

  async function handleCsvUpload(e: FormEvent) {
    e.preventDefault();
    if (!csvContent.trim()) return;
    setImporting(true); setError(''); setImportResult(null);
    try {
      const result = await financeApi.importCsv(csvContent, accountLabel || 'Uploaded Statement');
      setImportResult(result);
      setCsvContent('');
      await load();
      window.dispatchEvent(new CustomEvent('finsight:financial-data-changed'));
    } catch (e) {
      setError(apiErrorMessage(e));
    } finally {
      setImporting(false);
    }
  }

  function handleFileSelect(e: React.ChangeEvent<HTMLInputElement>) {
    const file = e.target.files?.[0];
    if (!file) return;
    if (!accountLabel) setAccountLabel(file.name.replace(/\.[^/.]+$/, ''));
    const reader = new FileReader();
    reader.onload = ev => {
      if (typeof ev.target?.result === 'string') {
        setCsvContent(ev.target.result);
      }
    };
    reader.readAsText(file);
  }

  async function handlePayPalConnect() {
    try {
      const auth = await financeApi.paypalOAuthAuthorize();
      window.open(auth.authorizationUrl, '_blank', 'noopener,noreferrer');
      setToast('PayPal authorization window opened in Sandbox mode.');
      setTimeout(() => setToast(''), 4000);
    } catch (e) {
      setError(apiErrorMessage(e));
    }
  }

  if (!enabled) {
    return (
      <div className="content">
        <div className="welcome-row">
          <div>
            <div className="eyebrow">FINANCIAL INSTITUTIONS</div>
            <h1>Connected Accounts</h1>
            <p className="subheading">Link PayPal and bank statements to aggregate your financial records.</p>
          </div>
        </div>
        <div className="accounts-preview-card panel">
          <ShieldCheck size={28} />
          <h2>Sign in to connect financial sources</h2>
          <p>FinSight connects to authorized PayPal Sandbox sources and supports bank statement CSV imports with automated deduplication.</p>
        </div>
      </div>
    );
  }

  return (
    <div className="content accounts-page">
      <div className="welcome-row">
        <div>
          <div className="eyebrow">FINANCIAL INSTITUTIONS</div>
          <h1>Connected Accounts</h1>
          <p className="subheading">Manage your PayPal Sandbox integration, statement imports, and liquid balances.</p>
        </div>
        <div className="accounts-top-actions">
          <button className="btn-secondary" onClick={() => setCsvModal(true)}>
            <UploadCloud size={16} /> Import CSV statement
          </button>
          <NavLink to="/paypal" className="btn-primary">
            <CloudDownload size={16} /> PayPal Sandbox
          </NavLink>
        </div>
      </div>

      {toast && <div className="toast"><ShieldCheck size={16} />{toast}</div>}
      {error && <div className="api-error" role="alert">{error}<button className="text-link" onClick={load}>Retry</button></div>}

      <section className="stats-grid">
        <div className="stat-card">
          <div className="stat-top"><span>Connected Sources</span><span className="stat-icon blue"><Wallet size={18} /></span></div>
          <strong className="stat-amount">{data?.totalConnected ?? '—'}</strong>
          <div className="stat-foot"><span>Active accounts</span></div>
        </div>
        <div className="stat-card">
          <div className="stat-top"><span>Imported Transactions</span><span className="stat-icon green"><FileSpreadsheet size={18} /></span></div>
          <strong className="stat-amount">{data?.totalTransactionsImported ?? '—'}</strong>
          <div className="stat-foot"><span>PayPal & CSV records</span></div>
        </div>
        <div className="stat-card">
          <div className="stat-top"><span>Data Protection</span><span className="stat-icon purple"><ShieldCheck size={18} /></span></div>
          <strong className="stat-amount">AES-256-GCM</strong>
          <div className="stat-foot"><span>Encrypted at rest</span></div>
        </div>
      </section>

      {loading ? (
        <div className="panel accounts-loading">
          <LoaderCircle size={24} className="spin" />
          <p>Loading your connected financial accounts…</p>
        </div>
      ) : (
        <div className="accounts-grid">
          {data?.accounts.map(acc => (
            <article className="panel account-card" key={acc.id}>
              <div className="account-card-header">
                <div className={`acc-icon ${acc.provider.toLowerCase()}`}>
                  {acc.provider === 'PAYPAL' ? <CloudDownload size={20} /> : acc.provider === 'CSV_STATEMENT' ? <FileSpreadsheet size={20} /> : <Landmark size={20} />}
                </div>
                <div className="acc-title-group">
                  <h3>{acc.accountName}</h3>
                  <small>{acc.accountType}</small>
                </div>
                <span className={`acc-status-pill ${acc.status.toLowerCase()}`}>
                  {acc.status.replace(/_/g, ' ')}
                </span>
              </div>

              <div className="account-details">
                {acc.balance !== null && (
                  <div className="acc-detail-item">
                    <span>Balance</span>
                    <strong>{money(acc.balance)}</strong>
                  </div>
                )}
                {acc.transactionCount > 0 && (
                  <div className="acc-detail-item">
                    <span>Synced Records</span>
                    <strong>{acc.transactionCount} transactions</strong>
                  </div>
                )}
                <div className="acc-detail-item">
                  <span>Last Activity</span>
                  <strong>{acc.lastSync || 'Never'}</strong>
                </div>
              </div>

              <div className="account-actions">
                {acc.provider === 'PAYPAL' && (
                  <>
                    <NavLink to="/paypal" className="btn-secondary btn-sm">
                      Sync & Review <ArrowUpRight size={13} />
                    </NavLink>
                    {acc.status === 'CONNECTED' ? (
                      <button className="btn-text-danger btn-sm" onClick={() => disconnectAccount(acc.id, acc.accountName)}>
                        <Unplug size={13} /> Disconnect
                      </button>
                    ) : (
                      <button className="btn-primary btn-sm" onClick={handlePayPalConnect}>
                        Connect PayPal
                      </button>
                    )}
                  </>
                )}
                {acc.provider === 'CSV_STATEMENT' && (
                  <>
                    <button className="btn-secondary btn-sm" onClick={() => { setAccountLabel(acc.accountName); setCsvModal(true); }}>
                      <UploadCloud size={13} /> Import More
                    </button>
                    <button className="btn-text-danger btn-sm" onClick={() => disconnectAccount(acc.id, acc.accountName)}>
                      <Trash2 size={13} /> Remove
                    </button>
                  </>
                )}
                {acc.provider === 'MANUAL_BANK' && (
                  <NavLink to="/assets" className="btn-secondary btn-sm">
                    Manage Asset <ArrowUpRight size={13} />
                  </NavLink>
                )}
              </div>
            </article>
          ))}
        </div>
      )}

      {/* CSV Import Modal */}
      {csvModal && (
        <div className="modal-backdrop" onMouseDown={e => e.target === e.currentTarget && !importing && setCsvModal(false)}>
          <div className="modal accounts-modal">
            <div className="modal-head">
              <div>
                <span className="eyebrow">FALLBACK IMPORT</span>
                <h2>Import Statement CSV</h2>
              </div>
              <button className="icon-btn" type="button" onClick={() => setCsvModal(false)} aria-label="Close">×</button>
            </div>

            <form onSubmit={handleCsvUpload}>
              <div className="modal-body-scroll">
                <p className="modal-description">
                  Upload a standard bank statement or PayPal CSV export. FinSight automatically extracts dates, amounts, and descriptions, and deduplicates against existing records.
                </p>

                <label>
                  Account / Statement Name
                  <input
                    type="text"
                    placeholder="e.g. PayPal Activity, HDFC Checking"
                    value={accountLabel}
                    onChange={e => setAccountLabel(e.target.value)}
                  />
                </label>

                <div className="file-drop-area">
                  <UploadCloud size={24} />
                  <p>Choose a CSV file or paste content below</p>
                  <input type="file" accept=".csv,text/csv" onChange={handleFileSelect} />
                </div>

                <label>
                  Or Paste Raw CSV Data
                  <textarea
                    rows={6}
                    placeholder="Date,Description,Amount,Type&#10;2026-10-15,Grocery Mart,1500,EXPENSE"
                    value={csvContent}
                    onChange={e => setCsvContent(e.target.value)}
                  />
                </label>

                {importResult && (
                  <div className="paypal-alert success" role="status">
                    <CheckCircle2 size={16} />
                    <span>
                      Import finished: <strong>{importResult.importedCount}</strong> added, <strong>{importResult.duplicateCount}</strong> duplicates skipped, <strong>{importResult.skippedCount}</strong> ignored.
                    </span>
                  </div>
                )}
              </div>

              <div className="modal-actions">
                <button type="button" className="btn-secondary" onClick={() => setCsvModal(false)}>
                  Close
                </button>
                <button type="submit" className="btn-primary" disabled={importing || !csvContent.trim()}>
                  {importing ? <LoaderCircle size={15} className="spin" /> : <Plus size={15} />}
                  {importing ? 'Processing CSV…' : 'Import transactions'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
}
