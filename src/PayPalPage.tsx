import { useEffect, useState } from 'react';
import { AlertCircle, CheckCircle2, CloudDownload, ExternalLink, LoaderCircle, ShieldCheck } from 'lucide-react';
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
  const [error,setError]=useState('');
  const [result,setResult]=useState<PayPalImportResult|null>(null);

  async function refresh() {
    setPageLoading(true); setError('');
    try { const [nextStatus,nextReviews]=await Promise.all([financeApi.paypalStatus(),financeApi.paypalReviews()]); setStatus(nextStatus); setReviews(nextReviews); }
    catch(e) { setError(apiErrorMessage(e)); }
    finally { setPageLoading(false); }
  }
  useEffect(()=>{ if(enabled) void refresh(); },[enabled]);

  async function runImport(event:React.FormEvent<HTMLFormElement>) {
    event.preventDefault(); setLoading(true); setError(''); setResult(null);
    try { const imported=await financeApi.importPayPal(startDate,endDate); setResult(imported); await refresh(); window.dispatchEvent(new CustomEvent('finsight:financial-data-changed')); }
    catch(e) { setError(apiErrorMessage(e)); }
    finally { setLoading(false); }
  }

  if(!enabled) return <div className="content"><div className="welcome-row"><div><div className="eyebrow">SANDBOX DATA SOURCE</div><h1>PayPal Sandbox</h1><p className="subheading">Sign in to view integration status for your FinSight account.</p></div></div><div className="paypal-card"><p>Demo Mode does not connect to or import PayPal data.</p><span className="paypal-demo-label">No PayPal records are shown in Demo Mode.</span></div></div>;

  return <div className="content paypal-page">
    <div className="welcome-row"><div><div className="eyebrow">SANDBOX DATA SOURCE</div><h1>PayPal Sandbox</h1><p className="subheading">Import eligible Sandbox reporting records into your FinSight transactions.</p></div><span className="paypal-sandbox-badge">SANDBOX ONLY</span></div>
    <div className="paypal-notice"><ShieldCheck size={19}/><div><strong>Private, read-only reporting integration</strong><p>This integration only reads the configured PayPal Sandbox reporting account. It does not link a personal PayPal account, move money, or support live PayPal.</p></div></div>
    <section className="paypal-card paypal-status-card">
      <div><div className="paypal-section-title">Connection status</div><p className="paypal-muted">Status updates only after a successful Sandbox Transaction Search response.</p></div>
      {pageLoading?<span className="paypal-inline-loading"><LoaderCircle size={16} className="spin"/> Checking server configuration…</span>:<div className={`paypal-status ${status?.connected?'connected':''}`}><i/>{status?.connected?'Connected to Sandbox reporting':status?.configured?'Ready to connect':'Server credentials not configured'}</div>}
      {!status?.configured&&!pageLoading&&<p className="paypal-muted">Ask the application operator to set <code>PAYPAL_CLIENT_ID</code> and <code>PAYPAL_CLIENT_SECRET</code> on the backend. Secrets must never be set in frontend variables.</p>}
      <p className="paypal-limit"><strong>Access limitation:</strong> PayPal Transaction Search uses app credentials. PayPal requires Partner Network authorization to search on behalf of third parties. This deployment binds its one configured Sandbox reporting source to one FinSight user; it cannot link each user’s personal PayPal account.</p>
      <a className="paypal-doc-link" href="https://developer.paypal.com/api/transaction-search/v1/" target="_blank" rel="noreferrer">PayPal Transaction Search API requirements <ExternalLink size={13}/></a>
    </section>
    <section className="paypal-card">
      <div className="paypal-section-title">Import transactions</div><p className="paypal-muted">Choose a maximum 31-day date range. PayPal data may take several hours to appear. Only successful INR records with unambiguous signed amounts and no separate fee are imported automatically.</p>
      <form className="paypal-import-form" onSubmit={runImport}>
        <label>Start date<input type="date" required max={endDate||initialEnd} value={startDate} onChange={e=>setStartDate(e.target.value)}/></label>
        <label>End date<input type="date" required max={initialEnd} min={startDate} value={endDate} onChange={e=>setEndDate(e.target.value)}/></label>
        <button className="btn-primary" type="submit" disabled={loading||pageLoading||!status?.configured}>{loading?<LoaderCircle size={16} className="spin"/>:<CloudDownload size={16}/>} {loading?'Importing…':'Verify & import'}</button>
      </form>
      {error&&<div className="paypal-alert error" role="alert"><AlertCircle size={16}/>{error}</div>}
      {result&&<div className="paypal-alert success" role="status"><CheckCircle2 size={16}/><span>Sandbox report completed: <strong>{result.importedCount}</strong> imported, <strong>{result.duplicateCount}</strong> duplicates skipped, <strong>{result.reviewCount}</strong> records queued for review.</span></div>}
    </section>
    <section className="paypal-card">
      <div className="paypal-review-heading"><div><div className="paypal-section-title">Needs review</div><p className="paypal-muted">Refunds, fees, unsettled activity, unsupported currencies, and ambiguous records are not added to cash flow.</p></div><span className="paypal-review-count">{reviews.length} records</span></div>
      {pageLoading?<div className="paypal-empty">Loading review records…</div>:reviews.length===0?<div className="paypal-empty">No PayPal records are waiting for review.</div>:<div className="paypal-review-list">{reviews.map(item=><div className="paypal-review-row" key={item.id}><strong>{item.paypalTransactionId?`PayPal reference · ${item.paypalTransactionId}`:'PayPal record without a transaction reference'}</strong><span>{item.occurredOn||'Date unavailable'} · {item.eventCode||'Unknown event'} · {item.reason.replace(/_/g,' ').toLowerCase()}</span></div>)}</div>}
    </section>
  </div>;
}
