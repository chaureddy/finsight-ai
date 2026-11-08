import { useEffect, useState } from 'react';
import { AlertCircle, CheckCircle2, CloudDownload, CreditCard, ExternalLink, KeyRound, LoaderCircle, ShieldCheck, Sparkles, Trash2, Unplug } from 'lucide-react';
import { apiErrorMessage, financeApi, type PayPalImportResult, type PayPalReviewItem, type PayPalStatus } from './api';
import './paypal.css';

const dateValue = (date:Date) => `${date.getFullYear()}-${String(date.getMonth()+1).padStart(2,'0')}-${String(date.getDate()).padStart(2,'0')}`;
const initialEnd = dateValue(new Date());
const firstDate = new Date(); firstDate.setDate(firstDate.getDate()-7);

export default function PayPalPage({enabled}:{enabled:boolean}) {
  const [status,setStatus]=useState<PayPalStatus|null>(null);
  const [reviews,setReviews]=useState<PayPalReviewItem[]>([]);
  const [startDate,setStartDate]=useState(dateValue(firstDate));
  const [endDate,setEndDate]=useState(initialEnd);
  const [loading,setLoading]=useState(false);
  const [pageLoading,setPageLoading]=useState(enabled);
  const [actionLoading,setActionLoading]=useState(false);
  const [error,setError]=useState('');
  const [toast,setToast]=useState('');
  const [result,setResult]=useState<PayPalImportResult|null>(null);

  // Sandbox Checkout Simulator state
  const [simAmount, setSimAmount] = useState('50.00');
  const [simCurrency, setSimCurrency] = useState('USD');
  const [simCategory, setSimCategory] = useState('Software');
  const [simDesc, setSimDesc] = useState('Cloud Server Subscription');
  const [simLoading, setSimLoading] = useState(false);
  const [simResult, setSimResult] = useState<{ orderId: string; status: string; amount: number; category: string } | null>(null);
  const [simError, setSimError] = useState('');

  async function refresh() {
    setPageLoading(true); setError('');
    try {
      const [nextStatus,nextReviews]=await Promise.all([financeApi.paypalStatus(),financeApi.paypalReviews()]);
      setStatus(nextStatus); setReviews(nextReviews);
    }
    catch(e) { setError(apiErrorMessage(e)); }
    finally { setPageLoading(false); }
  }
  useEffect(()=>{ if(enabled) void refresh(); },[enabled]);

  async function runImport(event:React.FormEvent<HTMLFormElement>) {
    event.preventDefault(); setLoading(true); setError(''); setResult(null);
    try {
      const imported=await financeApi.importPayPal(startDate,endDate);
      setResult(imported);
      await refresh();
      window.dispatchEvent(new CustomEvent('finsight:financial-data-changed'));
    }
    catch(e) { setError(apiErrorMessage(e)); }
    finally { setLoading(false); }
  }

  async function handleDisconnect() {
    if (!window.confirm('Disconnect PayPal Sandbox reporting? You can reconnect at any time.')) return;
    setActionLoading(true); setError('');
    try {
      await financeApi.paypalDisconnect();
      setToast('PayPal Sandbox connection removed.');
      setTimeout(() => setToast(''), 3000);
      await refresh();
      window.dispatchEvent(new CustomEvent('finsight:financial-data-changed'));
    } catch(e) { setError(apiErrorMessage(e)); }
    finally { setActionLoading(false); }
  }

  async function handleDismissReview(id: string) {
    setActionLoading(true); setError('');
    try {
      await financeApi.paypalDismissReview(id);
      setToast('Review record dismissed.');
      setTimeout(() => setToast(''), 2500);
      await refresh();
    } catch(e) { setError(apiErrorMessage(e)); }
    finally { setActionLoading(false); }
  }

  async function handleApproveReview(id: string, defCategory = 'PayPal') {
    const category = window.prompt('Assign category for this transaction:', defCategory) || defCategory;
    setActionLoading(true); setError('');
    try {
      await financeApi.paypalApproveReview(id, { category, description: 'Approved PayPal record' });
      setToast('PayPal record approved and added to your transactions.');
      setTimeout(() => setToast(''), 3000);
      await refresh();
      window.dispatchEvent(new CustomEvent('finsight:financial-data-changed'));
    } catch(e) { setError(apiErrorMessage(e)); }
    finally { setActionLoading(false); }
  }

  async function handleOAuthAuthorize() {
    try {
      const res = await financeApi.paypalOAuthAuthorize();
      window.open(res.authorizationUrl, '_blank', 'noopener,noreferrer');
      setToast('PayPal Sandbox authorization opened.');
      setTimeout(() => setToast(''), 3500);
    } catch(e) { setError(apiErrorMessage(e)); }
  }

  async function handleSimulateCheckout(e: React.FormEvent) {
    e.preventDefault();
    setSimLoading(true);
    setSimError('');
    setSimResult(null);
    try {
      const order = await financeApi.paypalCreateOrder({
        amount: parseFloat(simAmount) || 50,
        currency: simCurrency,
        description: simDesc,
      });
      const captured = await financeApi.paypalCaptureOrder(order.orderId, {
        description: simDesc,
        category: simCategory,
        direction: 'EXPENSE',
      });
      setSimResult({
        orderId: order.orderId,
        status: 'COMPLETED',
        amount: captured.amount,
        category: captured.category,
      });
      setToast('PayPal Sandbox order created and captured in ledger!');
      setTimeout(() => setToast(''), 4000);
      await refresh();
      window.dispatchEvent(new CustomEvent('finsight:financial-data-changed'));
    } catch (err) {
      setSimError(apiErrorMessage(err));
    } finally {
      setSimLoading(false);
    }
  }

  if(!enabled) return <div className="content"><div className="welcome-row"><div><div className="eyebrow">SANDBOX DATA SOURCE</div><h1>PayPal Sandbox</h1><p className="subheading">Sign in to view integration status for your FinSight account.</p></div></div><div className="paypal-card"><p>Demo Mode does not connect to or import PayPal data.</p><span className="paypal-demo-label">No PayPal records are shown in Demo Mode.</span></div></div>;

  return <div className="content paypal-page">
    <div className="welcome-row">
      <div>
        <div className="eyebrow">SANDBOX DATA SOURCE</div>
        <h1>PayPal Sandbox</h1>
        <p className="subheading">Import eligible Sandbox reporting records into your FinSight transactions.</p>
      </div>
      <div className="paypal-header-actions">
        {status?.connected && (
          <button className="btn-secondary btn-danger-subtle" onClick={handleDisconnect} disabled={actionLoading}>
            <Unplug size={15}/> Disconnect Sandbox
          </button>
        )}
        <span className="paypal-sandbox-badge">SANDBOX ONLY</span>
      </div>
    </div>

    {toast && <div className="toast"><ShieldCheck size={16}/>{toast}</div>}

    <div className="paypal-notice">
      <ShieldCheck size={19}/>
      <div>
        <strong>Private, read-only reporting integration</strong>
        <p>This integration connects to the configured PayPal Sandbox reporting account. Token credentials are encrypted at rest with AES-256-GCM. Personal PayPal accounts require Partner Network approvals for third-party transaction queries; use statement CSV import as a universal fallback.</p>
      </div>
    </div>

    <section className="paypal-card paypal-status-card">
      <div className="paypal-status-header">
        <div>
          <div className="paypal-section-title">Connection status</div>
          <p className="paypal-muted">Status updates after a successful Sandbox Transaction Search response.</p>
        </div>
        {!status?.connected && status?.configured && (
          <button className="btn-secondary" onClick={handleOAuthAuthorize}>
            <KeyRound size={14}/> Test OAuth Authorization
          </button>
        )}
      </div>

      {pageLoading?<span className="paypal-inline-loading"><LoaderCircle size={16} className="spin"/> Checking server configuration…</span>:<div className={`paypal-status ${status?.connected?'connected':''}`}><i/>{status?.connected?'Connected to Sandbox reporting':status?.configured?'Ready to connect':'Server credentials not configured'}</div>}
      {!status?.configured&&!pageLoading&&<p className="paypal-muted">Ask the application operator to set <code>PAYPAL_CLIENT_ID</code> and <code>PAYPAL_CLIENT_SECRET</code> on the backend. Secrets must never be set in frontend variables.</p>}
      <p className="paypal-limit"><strong>Access limitation:</strong> PayPal Transaction Search uses app credentials. PayPal requires Partner Network authorization to search on behalf of third parties. This deployment binds its configured Sandbox reporting source to your FinSight user.</p>
      <a className="paypal-doc-link" href="https://developer.paypal.com/api/transaction-search/v1/" target="_blank" rel="noreferrer">PayPal Transaction Search API requirements <ExternalLink size={13}/></a>
    </section>

    <section className="paypal-card paypal-simulator-card">
      <div className="paypal-section-title">
        <CreditCard size={18}/> PayPal Sandbox Checkout Simulator
      </div>
      <p className="paypal-muted">
        Execute a live Sandbox order creation (<code>POST /v2/checkout/orders</code>) and capture (<code>POST /v2/checkout/orders/&#123;id&#125;/capture</code>). The payment is instantly settled and ingested into your FinSight transactions for AI analysis.
      </p>

      <form className="paypal-simulator-form" onSubmit={handleSimulateCheckout}>
        <div className="simulator-inputs">
          <label>
            Amount
            <input
              type="number"
              step="0.01"
              min="0.01"
              required
              value={simAmount}
              onChange={e => setSimAmount(e.target.value)}
              placeholder="50.00"
            />
          </label>
          <label>
            Currency
            <select value={simCurrency} onChange={e => setSimCurrency(e.target.value)}>
              <option value="USD">USD ($)</option>
              <option value="EUR">EUR (€)</option>
              <option value="GBP">GBP (£)</option>
              <option value="INR">INR (₹)</option>
            </select>
          </label>
          <label>
            Category
            <select value={simCategory} onChange={e => setSimCategory(e.target.value)}>
              <option value="Software">Software</option>
              <option value="Cloud Services">Cloud Services</option>
              <option value="Subscriptions">Subscriptions</option>
              <option value="Shopping">Shopping</option>
              <option value="Entertainment">Entertainment</option>
            </select>
          </label>
          <label className="simulator-desc-field">
            Description
            <input
              type="text"
              required
              value={simDesc}
              onChange={e => setSimDesc(e.target.value)}
              placeholder="e.g. Cloud Server Hosting"
            />
          </label>
        </div>

        <button
          className="btn-primary simulator-submit-btn"
          type="submit"
          disabled={simLoading || pageLoading || !status?.configured}
        >
          {simLoading ? <LoaderCircle size={16} className="spin" /> : <CreditCard size={16} />}
          {simLoading ? 'Processing Checkout…' : 'Execute Sandbox Checkout & Capture'}
        </button>
      </form>

      {simError && (
        <div className="paypal-alert error" role="alert">
          <AlertCircle size={16} />
          {simError}
        </div>
      )}

      {simResult && (
        <div className="paypal-simulator-result" role="status">
          <div className="simulator-result-header">
            <CheckCircle2 size={18} className="text-success" />
            <strong>Order Successfully Captured & Recorded</strong>
          </div>
          <div className="simulator-result-details">
            <div><span>Order ID:</span> <code>{simResult.orderId}</code></div>
            <div><span>Status:</span> <span className="status-pill completed">{simResult.status}</span></div>
            <div><span>Amount Recorded:</span> <strong>${simResult.amount}</strong> in <em>{simResult.category}</em></div>
            <div><span>Source Provider:</span> <span className="provider-tag">PAYPAL</span></div>
          </div>
          <div className="simulator-result-actions">
            <a href="/assistant" className="btn-secondary btn-sm ai-bridge-btn">
              <Sparkles size={14} /> Analyze with AI Assistant
            </a>
          </div>
        </div>
      )}
    </section>

    <section className="paypal-card">
      <div className="paypal-section-title">Import transactions</div>
      <p className="paypal-muted">Choose a maximum 31-day date range within the last 3 years. Only successful INR records with unambiguous signed amounts are imported automatically.</p>
      <form className="paypal-import-form" onSubmit={runImport}>
        <label>Start date<input type="date" required max={endDate||initialEnd} value={startDate} onChange={e=>setStartDate(e.target.value)}/></label>
        <label>End date<input type="date" required max={initialEnd} min={startDate} value={endDate} onChange={e=>setEndDate(e.target.value)}/></label>
        <button className="btn-primary" type="submit" disabled={loading||pageLoading||!status?.configured}>{loading?<LoaderCircle size={16} className="spin"/>:<CloudDownload size={16}/>} {loading?'Importing…':'Verify & import'}</button>
      </form>
      {error&&<div className="paypal-alert error" role="alert"><AlertCircle size={16}/>{error}</div>}
      {result&&<div className="paypal-alert success" role="status"><CheckCircle2 size={16}/><span>Sandbox report completed: <strong>{result.importedCount}</strong> imported, <strong>{result.duplicateCount}</strong> duplicates skipped, <strong>{result.reviewCount}</strong> records queued for review.</span></div>}
    </section>

    <section className="paypal-card">
      <div className="paypal-review-heading">
        <div>
          <div className="paypal-section-title">Review Queue</div>
          <p className="paypal-muted">Refunds, fees, unsettled activity, unsupported currencies, and unverified amount-directions stay here until approved or dismissed.</p>
        </div>
        <span className="paypal-review-count">{reviews.length} records</span>
      </div>
      {pageLoading?<div className="paypal-empty">Loading review records…</div>:reviews.length===0?<div className="paypal-empty">No PayPal records are waiting for review.</div>:<div className="paypal-review-list">{reviews.map(item=><div className="paypal-review-row" key={item.id}>
        <div className="review-row-info">
          <strong>{item.paypalTransactionId?`PayPal reference · ${item.paypalTransactionId}`:'PayPal record without a transaction reference'}</strong>
          <span>{item.occurredOn||'Date unavailable'} · {item.amount ? `₹${item.amount}` : 'Amount unavailable'} · {item.reason.replace(/_/g,' ').toLowerCase()}</span>
        </div>
        <div className="review-row-actions">
          <button className="btn-secondary btn-sm" onClick={() => handleApproveReview(item.id)} disabled={actionLoading} title="Convert to approved transaction">
            Approve
          </button>
          <button className="btn-text-danger btn-sm" onClick={() => handleDismissReview(item.id)} disabled={actionLoading} title="Dismiss this record">
            <Trash2 size={13}/> Dismiss
          </button>
        </div>
      </div>)}</div>}
    </section>
  </div>;
}
