import { useEffect, useMemo, useRef, useState } from 'react';
import { NavLink, Route, Routes, useLocation } from 'react-router-dom';
import { Activity, ArrowDownLeft, ArrowUpRight, Bell, Bot, BriefcaseBusiness, CarFront, ChevronDown, CircleHelp, CloudDownload, CreditCard, Download, FileText, Goal, Home, Landmark, LayoutDashboard, Lightbulb, LogOut, Menu, Pencil, Plus, Repeat2, Search, Settings, ShieldCheck, Sparkles, TrendingUp, Trash2, UploadCloud, User, Wallet, X } from 'lucide-react';
import { Area, AreaChart, CartesianGrid, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts';
import AuthScreen from './AuthScreen';
import { apiErrorMessage, authApi, financeApi, type ApiTransaction, type CashFlowSummary, type FinancialOverview, type SmartInsight, type TransactionInput, type UserProfile } from './api';
import { FinancialOverviewPage, FinancialRecordsPage } from './FinancialPages';
import { FinancialHealthPage, ForecastPage, IntelligenceResourcePage, SpendingIntelligencePage } from './IntelligencePages';
import PayPalPage from './PayPalPage';
import AccountsPage from './AccountsPage';
import AiAssistantPage from './AiAssistantPage';
import SettingsPage from './SettingsPage';
import ProfilePage from './ProfilePage';
import './profile.css';

type Tx = { id: string; name: string; category: string; date: string; amount: number; kind: 'income' | 'expense' };
const initialTx: Tx[] = [
  { id: 'demo-1', name: 'Monthly salary', category: 'Income', date: 'Today', amount: 85000, kind: 'income' },
  { id: 'demo-2', name: 'Rent payment', category: 'Housing', date: 'Yesterday', amount: 22000, kind: 'expense' },
  { id: 'demo-3', name: 'Grocery store', category: 'Groceries', date: 'Oct 29', amount: 3240, kind: 'expense' },
  { id: 'demo-4', name: 'Freelance project', category: 'Freelance', date: 'Oct 27', amount: 18000, kind: 'income' },
  { id: 'demo-5', name: 'Electricity bill', category: 'Utilities', date: 'Oct 25', amount: 1680, kind: 'expense' },
];
const chartData = [{ month: 'May', income: 72000, expense: 51000 }, { month: 'Jun', income: 76000, expense: 49000 }, { month: 'Jul', income: 81000, expense: 56000 }, { month: 'Aug', income: 78000, expense: 52000 }, { month: 'Sep', income: 89000, expense: 60000 }, { month: 'Oct', income: 103000, expense: 62700 }];
const nav = [
  { label: 'Overview', path: '/', icon: LayoutDashboard }, { label: 'Financial overview', path: '/financial-overview', icon: LayoutDashboard }, { label: 'Transactions', path: '/transactions', icon: Activity },
  { label: 'Accounts', path: '/accounts', icon: Wallet }, { label: 'Assets', path: '/assets', icon: Landmark }, { label: 'Liabilities', path: '/liabilities', icon: CreditCard },
  { label: 'Budgets', path: '/budgets', icon: Wallet }, { label: 'Financial goals', path: '/goals', icon: Goal }, { label: 'Recurring payments', path: '/recurring-payments', icon: Repeat2 },
  { label: 'Cash flow forecast', path: '/forecast', icon: TrendingUp }, { label: 'Financial health', path: '/financial-health', icon: Activity }, { label: 'Spending intelligence', path: '/spending-intelligence', icon: CircleHelp },
  { label: 'Investments', path: '/investments', icon: TrendingUp }, { label: 'Insurance', path: '/insurance', icon: ShieldCheck }, { label: 'PayPal Sandbox', path: '/paypal', icon: CloudDownload },
  { label: 'AI assistant', path: '/assistant', icon: Bot },
];
const money = (n: number) => new Intl.NumberFormat('en-IN', { style: 'currency', currency: 'INR', maximumFractionDigits: 0 }).format(n);
const toTransaction = (tx: ApiTransaction): Tx => ({id:tx.id,name:tx.description,category:tx.category,date:tx.occurredOn,amount:Number(tx.amount),kind:tx.direction==='INCOME'?'income':'expense'});

export default function App() {
  const [authState, setAuthState] = useState<'loading' | 'guest' | 'authenticated' | 'demo'>('loading');
  const [user, setUser] = useState<UserProfile | null>(null);
  const [transactions, setTransactions] = useState<Tx[]>([]);
  const [cashFlow, setCashFlow] = useState<CashFlowSummary | null>(null);
  const [financialOverview, setFinancialOverview] = useState<FinancialOverview | null>(null);
  const [pageLoading, setPageLoading] = useState(false);
  const [authLoading, setAuthLoading] = useState(false);
  const [authError, setAuthError] = useState('');
  const [pageError, setPageError] = useState('');
  const [editing, setEditing] = useState<Tx | null>(null);
  const [modal, setModal] = useState(false);
  const [mobileOpen, setMobileOpen] = useState(false);
  const [query, setQuery] = useState('');
  const [toast, setToast] = useState('');
  const [saving, setSaving] = useState(false);
  const location = useLocation();
  const totals = useMemo(() => ({ income: transactions.filter(t => t.kind === 'income').reduce((s, t) => s + t.amount, 0), expense: transactions.filter(t => t.kind === 'expense').reduce((s, t) => s + t.amount, 0) }), [transactions]);
  const page = nav.find(n => n.path === location.pathname)?.label || 'Overview';
  const displayName = user?.displayName || 'Alex';
  const initials = (user?.displayName || 'Alex Smith').split(/\s+/).slice(0,2).map(part => part[0]?.toUpperCase()).join('');

  const [authMode, setAuthMode] = useState<'login' | 'register'>('login');
  const [topMenuOpen, setTopMenuOpen] = useState(false);
  const topMenuRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    function handleClickOutside(event: MouseEvent) {
      if (topMenuRef.current && !topMenuRef.current.contains(event.target as Node)) {
        setTopMenuOpen(false);
      }
    }
    function handleKeyDown(event: KeyboardEvent) {
      if (event.key === 'Escape') {
        setTopMenuOpen(false);
      }
    }
    document.addEventListener('mousedown', handleClickOutside);
    document.addEventListener('keydown', handleKeyDown);
    return () => {
      document.removeEventListener('mousedown', handleClickOutside);
      document.removeEventListener('keydown', handleKeyDown);
    };
  }, []);

  async function refreshFinancialData() {
    setPageLoading(true); setPageError('');
    try {
      const [apiTransactions, summary, overview] = await Promise.all([financeApi.transactions(), financeApi.cashFlow(), financeApi.financialOverview()]);
      setTransactions(apiTransactions.map(toTransaction)); setCashFlow(summary); setFinancialOverview(overview);
    } catch (error) { setPageError(apiErrorMessage(error)); }
    finally { setPageLoading(false); }
  }

  useEffect(() => {
    let mounted = true;
    authApi.currentUser().then(async current => {
      if (!mounted) return;
      setUser(current); setAuthState('authenticated');
      try {
        const [apiTransactions, summary, overview] = await Promise.all([financeApi.transactions(), financeApi.cashFlow(), financeApi.financialOverview()]);
        if (mounted) { setTransactions(apiTransactions.map(toTransaction)); setCashFlow(summary); setFinancialOverview(overview); }
      } catch (error) { if (mounted) setPageError(apiErrorMessage(error)); }
    }).catch(error => {
      if (!mounted) return;
      setAuthState('guest');
      if (!error || !('response' in (error as object))) setAuthError(apiErrorMessage(error));
    });
    const onExpired = () => { setUser(null); setTransactions([]); setCashFlow(null); setFinancialOverview(null); setAuthState('guest'); setAuthError('Your session has ended. Please sign in again.'); };
    const onFinancialChange = () => { financeApi.financialOverview().then(setFinancialOverview).catch(error => { if (mounted) setPageError(apiErrorMessage(error)); }); };
    window.addEventListener('finsight:session-expired', onExpired);
    window.addEventListener('finsight:financial-data-changed', onFinancialChange);
    return () => { mounted = false; window.removeEventListener('finsight:session-expired', onExpired); window.removeEventListener('finsight:financial-data-changed', onFinancialChange); };
  }, []);

  async function signIn(email: string, password: string) {
    setAuthLoading(true); setAuthError('');
    try { const current = await authApi.login(email,password); setUser(current); setAuthState('authenticated'); await refreshFinancialData(); }
    catch (error) { setAuthError(apiErrorMessage(error)); }
    finally { setAuthLoading(false); }
  }

  async function register(input: {email:string;password:string;displayName:string}) {
    setAuthLoading(true); setAuthError('');
    try { await authApi.register(input); await signIn(input.email,input.password); }
    catch (error) { setAuthError(apiErrorMessage(error)); setAuthLoading(false); }
  }

  function enterDemo() { setAuthState('demo'); setUser(null); setTransactions(initialTx); setCashFlow(null); setPageError(''); }

  async function signOut() {
    setTopMenuOpen(false);
    try { if (authState === 'authenticated') await authApi.logout(); }
    catch (error) { setToast(apiErrorMessage(error)); setTimeout(()=>setToast(''),3000); return; }
    setUser(null); setTransactions([]); setCashFlow(null); setFinancialOverview(null); setAuthState('guest'); setAuthMode('login'); setToast('Signed out'); setTimeout(()=>setToast(''),2200);
  }

  async function saveTransaction(e: React.FormEvent<HTMLFormElement>) {
    e.preventDefault(); const data = new FormData(e.currentTarget); const amount=Number(data.get('amount'));
    if (!Number.isFinite(amount) || amount <= 0) return;
    const input: TransactionInput={description:String(data.get('name')).trim(),category:String(data.get('category')).trim(),amount,direction:data.get('kind')==='income'?'INCOME':'EXPENSE',occurredOn:String(data.get('date'))};
    setSaving(true); setPageError('');
    try {
      if(authState==='authenticated') {
        if(editing) await financeApi.updateTransaction(editing.id,input); else await financeApi.createTransaction(input);
        await refreshFinancialData();
      } else {
        const tx:Tx={id:editing?.id||`demo-${Date.now()}`,name:input.description,category:input.category,date:'Today',amount,kind:input.direction==='INCOME'?'income':'expense'};
        setTransactions(current=>editing?current.map(item=>item.id===editing.id?tx:item):[tx,...current]);
      }
      setModal(false); setEditing(null); setToast(editing?'Transaction updated':'Transaction saved'); setTimeout(()=>setToast(''),2500);
    } catch(error) { setPageError(apiErrorMessage(error)); }
    finally { setSaving(false); }
  }

  function editTransaction(tx:Tx) { setEditing(tx); setModal(true); }

  async function deleteTransaction(tx:Tx) {
    if(authState==='authenticated') {
      try { await financeApi.deleteTransaction(tx.id); await refreshFinancialData(); setToast('Transaction deleted'); setTimeout(()=>setToast(''),2200); }
      catch(error) { setPageError(apiErrorMessage(error)); }
    } else {
      setTransactions(current=>current.filter(item=>item.id!==tx.id)); setToast('Transaction deleted'); setTimeout(()=>setToast(''),2200);
    }
  }

  if(authState==='loading') return <div className="auth-loading">Loading your FinSight workspace…</div>;
  if(authState==='guest') return <AuthScreen onLogin={signIn} onRegister={register} onDemo={enterDemo} loading={authLoading} error={authError} initialMode={authMode} />;

  return <div className="app-shell">
    <aside className={`sidebar ${mobileOpen ? 'open' : ''}`}>
      <div className="brand"><div className="brand-mark"><Activity size={20}/></div><span>finsight<span className="brand-ai">.ai</span></span><button className="mobile-close icon-btn" onClick={() => setMobileOpen(false)} aria-label="Close menu"><X size={19}/></button></div>
      <div className="workspace-label">WORKSPACE</div>
      <nav>{nav.map(({label,path,icon:Icon}) => <NavLink key={path} to={path} end={path === '/'} onClick={() => setMobileOpen(false)} className={({isActive}) => `nav-item ${isActive?'active':''}`}><Icon size={18}/><span>{label}</span>{label === 'AI assistant' && <span className="nav-new">AI</span>}</NavLink>)}</nav>
      <div className="sidebar-bottom">
        <div className="upgrade-card">
          <div className="upgrade-icon"><Sparkles size={17}/></div>
          <strong>FinSight Intelligence</strong>
          <p>Real-time AI analysis grounded in actual financial data.</p>
          <NavLink to="/assistant" className="text-link">Ask AI Assistant <ArrowUpRight size={14}/></NavLink>
        </div>
        <NavLink to="/profile" className="nav-item"><User size={18}/><span>Profile</span></NavLink>
        <NavLink to="/settings" className="nav-item"><Settings size={18}/><span>Settings</span></NavLink>
        <button className="profile-row" onClick={signOut} aria-label={`Sign out ${displayName}`}>
          <div className="avatar">{initials}</div>
          <span className="profile-copy"><strong>{displayName}</strong><small>{authState==='demo'?'Demo workspace':user?.email}</small></span>
          {authState==='authenticated'?<LogOut size={15}/>:<ChevronDown size={16}/>}
        </button>
      </div>
    </aside>
    {mobileOpen && <button className="scrim" aria-label="Close menu" onClick={() => setMobileOpen(false)}/>}
    <main className="main-area">
      <header className="topbar">
        <div className="topbar-left">
          <button className="icon-btn mobile-menu" onClick={() => setMobileOpen(true)} aria-label="Open menu"><Menu size={20}/></button>
          <div className="breadcrumb">Workspace <span>/</span> <strong>{page}</strong></div>
        </div>
        <div className="top-actions">
          <label className="search-box"><Search size={16}/><input value={query} onChange={e => setQuery(e.target.value)} placeholder="Search anything..."/><kbd>⌘ K</kbd></label>
          <button className="icon-btn notification" aria-label="Notifications" onClick={() => { setToast('You’re all caught up'); setTimeout(() => setToast(''), 2200); }}><Bell size={18}/><i/></button>

          {authState === 'demo' ? (
            <div className="demo-topbar-actions">
              <span className="demo-pill-badge">
                <Sparkles size={13} /> Demo Mode
              </span>
              <button
                className="btn-secondary btn-sm"
                type="button"
                onClick={() => {
                  setAuthMode('login');
                  setAuthState('guest');
                }}
              >
                Sign in
              </button>
              <button
                className="btn-primary btn-sm"
                type="button"
                onClick={() => {
                  setAuthMode('register');
                  setAuthState('guest');
                }}
              >
                Register
              </button>
            </div>
          ) : (
            <div className="top-profile-container" ref={topMenuRef}>
              <button
                type="button"
                className="top-avatar-btn"
                aria-label="Open profile menu"
                aria-haspopup="true"
                aria-expanded={topMenuOpen}
                onClick={() => setTopMenuOpen(o => !o)}
              >
                <div className="top-avatar">{initials}</div>
                <span className="top-user-name">{displayName}</span>
                <ChevronDown size={14} className={`dropdown-chevron ${topMenuOpen ? 'open' : ''}`} />
              </button>

              {topMenuOpen && (
                <div className="profile-dropdown-menu" role="menu" aria-label="User profile options">
                  <div className="dropdown-header">
                    <div className="dropdown-avatar">{initials}</div>
                    <div className="dropdown-user-details">
                      <strong className="dropdown-display-name">{displayName}</strong>
                      <small className="dropdown-email">{user?.email || 'demo@finsight.ai'}</small>
                      <span className="dropdown-status-pill">
                        <ShieldCheck size={11} /> {authState === 'authenticated' ? 'Verified Session' : 'Demo Workspace'}
                      </span>
                    </div>
                  </div>
                  <div className="dropdown-divider" />
                  <NavLink
                    to="/profile"
                    role="menuitem"
                    className="dropdown-item"
                    onClick={() => setTopMenuOpen(false)}
                  >
                    <User size={15} />
                    <span>Profile</span>
                  </NavLink>
                  <NavLink
                    to="/settings"
                    role="menuitem"
                    className="dropdown-item"
                    onClick={() => setTopMenuOpen(false)}
                  >
                    <Settings size={15} />
                    <span>Account Settings</span>
                  </NavLink>
                  <div className="dropdown-divider" />
                  <button
                    type="button"
                    role="menuitem"
                    className="dropdown-item dropdown-logout-btn"
                    onClick={() => {
                      setTopMenuOpen(false);
                      void signOut();
                    }}
                  >
                    <LogOut size={15} />
                    <span>Sign out</span>
                  </button>
                </div>
              )}
            </div>
          )}
        </div>
      </header>

      {pageError && <div className="api-error" role="alert"><span>{pageError}</span><button className="text-link" onClick={refreshFinancialData}>Retry</button></div>}
      {pageLoading && <div className="loading-strip">Refreshing your financial data…</div>}

      <Routes>
        <Route path="/" element={<Dashboard totals={totals} summary={cashFlow} netWorth={financialOverview?.netWorth??null} transactions={transactions} onAdd={() => {setEditing(null);setModal(true);}} demoMode={authState==='demo'} displayName={displayName}/>} />
        <Route path="/profile" element={<ProfilePage user={user} onProfileUpdated={u => setUser(u)} onSignOut={signOut}/>} />
        <Route path="/settings" element={<SettingsPage user={user} onProfileUpdated={u => setUser(u)} onSignOut={signOut}/>} />
        <Route path="/financial-overview" element={<FinancialOverviewPage enabled={authState==='authenticated'}/>} />
        <Route path="/transactions" element={<Transactions transactions={transactions} query={query} onAdd={() => {setEditing(null);setModal(true);}} onEdit={editTransaction} onDelete={deleteTransaction} demoMode={authState==='demo'} />} />
        <Route path="/accounts" element={<AccountsPage enabled={authState==='authenticated'}/>} />
        <Route path="/assets" element={<FinancialRecordsPage kind="assets" enabled={authState==='authenticated'}/>} />
        <Route path="/liabilities" element={<FinancialRecordsPage kind="liabilities" enabled={authState==='authenticated'}/>} />
        <Route path="/investments" element={<FinancialRecordsPage kind="investments" enabled={authState==='authenticated'}/>} />
        <Route path="/insurance" element={<FinancialRecordsPage kind="insurance" enabled={authState==='authenticated'}/>} />
        <Route path="/budgets" element={<IntelligenceResourcePage kind="budgets" enabled={authState==='authenticated'}/>} />
        <Route path="/goals" element={<IntelligenceResourcePage kind="goals" enabled={authState==='authenticated'}/>} />
        <Route path="/recurring-payments" element={<IntelligenceResourcePage kind="recurring" enabled={authState==='authenticated'}/>} />
        <Route path="/forecast" element={<ForecastPage enabled={authState==='authenticated'}/>} />
        <Route path="/financial-health" element={<FinancialHealthPage enabled={authState==='authenticated'}/>} />
        <Route path="/spending-intelligence" element={<SpendingIntelligencePage enabled={authState==='authenticated'}/>} />
        <Route path="/paypal" element={<PayPalPage enabled={authState==='authenticated'}/>} />
        <Route path="/assistant" element={<AiAssistantPage enabled={authState==='authenticated'} displayName={displayName}/>} />
        <Route path="/settings" element={<SettingsPage user={user} onProfileUpdated={u => setUser(u)} onSignOut={signOut}/>} />
      </Routes>
    </main>

    {modal && <div className="modal-backdrop" onMouseDown={e => e.target === e.currentTarget && setModal(false)}><form className="modal" onSubmit={saveTransaction}><div className="modal-head"><div><span className="eyebrow">YOUR FINANCES</span><h2>{editing?'Edit transaction':'Add transaction'}</h2></div><button type="button" className="icon-btn" onClick={() => {setModal(false);setEditing(null);}} aria-label="Close"><X size={19}/></button></div><label>Description<input name="name" required maxLength={120} defaultValue={editing?.name} placeholder="e.g. Monthly salary"/></label><div className="form-row"><label>Amount (₹)<input name="amount" required type="number" min="0.01" step="0.01" defaultValue={editing?.amount} placeholder="0.00"/></label><label>Type<select name="kind" defaultValue={editing?.kind||'expense'}><option value="expense">Expense</option><option value="income">Income</option></select></label></div><label>Category<input name="category" required maxLength={60} defaultValue={editing?.category} placeholder="e.g. Housing"/></label><label>Date<input name="date" type="date" required defaultValue={editing?.date.match(/^\d{4}-\d{2}-\d{2}$/)?editing.date:new Date().toISOString().slice(0,10)}/></label><div className="modal-actions"><button type="button" className="btn-secondary" onClick={() => {setModal(false);setEditing(null);}}>Cancel</button><button className="btn-primary" disabled={saving}><Plus size={16}/>{saving?'Saving…':editing?'Save changes':'Save transaction'}</button></div></form></div>}
    {toast && <div className="toast"><ShieldCheck size={17}/>{toast}</div>}
  </div>;
}

function Dashboard({ totals, summary, netWorth, transactions, onAdd, demoMode, displayName }: { totals: {income:number;expense:number}; summary:CashFlowSummary|null; netWorth:number|null; transactions: Tx[]; onAdd:()=>void; demoMode:boolean; displayName:string }) {
  const [insights, setInsights] = useState<SmartInsight[]>([]);
  const income=demoMode?totals.income:(summary?.income||0), expenses=demoMode?totals.expense:(summary?.expenses||0);
  const savingsRate=demoMode?(income?Math.round((income-expenses)/income*100):0):(summary?.savingsRatePercent||0);
  const chart=demoMode?chartData:[{month:new Intl.DateTimeFormat('en',{month:'short'}).format(new Date()),income,expense:expenses}];
  const greeting=displayName.trim().split(/\s+/)[0];

  useEffect(() => {
    if (!demoMode && typeof financeApi?.aiInsights === 'function') {
      financeApi.aiInsights().then(setInsights).catch(() => {});
    }
  }, [demoMode]);

  return <div className="content"><div className="welcome-row"><div><div className="eyebrow">{new Intl.DateTimeFormat('en-IN',{weekday:'long',month:'long',day:'numeric',year:'numeric'}).format(new Date()).toUpperCase()}</div><h1>Good morning, {greeting} <span>✦</span></h1><p className="subheading">Here’s your {demoMode?'demo preview':'financial intelligence overview'} at a glance.</p></div><div className="dashboard-top-btns"><NavLink to="/assistant" className="btn-secondary"><Bot size={16}/> Ask AI</NavLink><button className="btn-primary" onClick={onAdd}><Plus size={17}/> Add transaction</button></div></div>
    <section className="stats-grid">
      <StatCard title="Net worth" amount={demoMode?money(1245800):netWorth===null?'—':money(netWorth)} change={demoMode?'6.8%':netWorth===null?'—':'Actual'} icon={Wallet} tone="green" caption={demoMode?'demo data':netWorth===null?'Add assets and liabilities':'from your entered records'}/>
      <StatCard title="Monthly income" amount={money(income)} change={demoMode?'12.4%':'Actual'} icon={ArrowDownLeft} tone="blue" caption={demoMode?'demo data':'current month'}/>
      <StatCard title="Monthly expenses" amount={money(expenses)} change={demoMode?'3.2%':'Actual'} icon={ArrowUpRight} tone="amber" caption={demoMode?'demo data':'current month'}/>
      <StatCard title="Savings rate" amount={`${savingsRate}%`} change={demoMode?'Demo':'Actual'} icon={TrendingUp} tone="purple" caption="of current month income"/>
    </section>

    {insights.length > 0 && !demoMode && (
      <div className="panel dashboard-insights-panel">
        <div className="insights-panel-header">
          <div className="insights-panel-title">
            <Lightbulb size={18} />
            <h2>Proactive AI Recommendations</h2>
          </div>
          <NavLink to="/assistant" className="text-link">Chat with Assistant <ArrowUpRight size={13}/></NavLink>
        </div>
        <div className="insights-cards-grid">
          {insights.slice(0, 3).map(i => (
            <div className={`dash-insight-card ${i.tone}`} key={i.id}>
              <strong>{i.title}</strong>
              <p>{i.message}</p>
              <NavLink to={i.actionLink} className="dash-insight-link">{i.actionLabel} →</NavLink>
            </div>
          ))}
        </div>
      </div>
    )}

    <section className="dashboard-grid">
      <div className="panel chart-panel">
        <div className="panel-heading"><div><h2>Cash flow</h2><p>{demoMode?'Illustrative sample trend':'Current calendar month, from your saved transactions'}</p></div>{demoMode&&<select className="period-select" aria-label="Chart period"><option>Last 6 months</option><option>Last 12 months</option></select>}</div>
        <div className="chart-legend"><span><i className="legend-dot income-dot"/> Income</span><span><i className="legend-dot expense-dot"/> Expenses</span></div>
        <div className="chart-wrap">
          <ResponsiveContainer width="100%" height="100%">
            <AreaChart data={chart} margin={{top:10,right:8,left:0,bottom:0}}>
              <defs>
                <linearGradient id="incomeFill" x1="0" y1="0" x2="0" y2="1"><stop offset="0%" stopColor="#10b981" stopOpacity={.17}/><stop offset="100%" stopColor="#10b981" stopOpacity={0}/></linearGradient>
                <linearGradient id="expenseFill" x1="0" y1="0" x2="0" y2="1"><stop offset="0%" stopColor="#7c91b3" stopOpacity={.13}/><stop offset="100%" stopColor="#7c91b3" stopOpacity={0}/></linearGradient>
              </defs>
              <CartesianGrid stroke="#edf1f5" vertical={false}/>
              <XAxis dataKey="month" axisLine={false} tickLine={false} tick={{fontSize:11,fill:'#98a2b3'}} dy={10}/>
              <YAxis axisLine={false} tickLine={false} tick={{fontSize:11,fill:'#98a2b3'}} tickFormatter={v=>`₹${v/1000}k`} width={48}/>
              <Tooltip formatter={(v)=>money(Number(v))} contentStyle={{borderRadius:12,border:'1px solid #e9edf2',fontSize:12}}/>
              <Area type="monotone" dataKey="income" stroke="#10b981" strokeWidth={2.5} fill="url(#incomeFill)"/>
              <Area type="monotone" dataKey="expense" stroke="#8ea0ba" strokeWidth={2.5} fill="url(#expenseFill)"/>
            </AreaChart>
          </ResponsiveContainer>
        </div>
      </div>

      <div className="panel health-panel">
        <div className="panel-heading">
          <div><h2>Financial health</h2><p>{demoMode?'Illustrative demo overview':'Based on entered accounts & records'}</p></div>
          <NavLink to="/financial-health" className="more-btn" aria-label="More information"><CircleHelp size={17}/></NavLink>
        </div>
        {demoMode ? (
          <>
            <div className="health-score">
              <div className="score-ring"><div><strong>78</strong><small>/ 100</small></div></div>
              <div><span className="health-good">Demo overview</span><p>Illustrative sample financial wellness figures.</p></div>
            </div>
            <div className="health-metric"><span>Emergency fund</span><strong>4.2 months</strong><div className="progress"><i style={{width:'70%'}}/></div><small>Sample goal: 6 months of expenses</small></div>
            <div className="health-metric"><span>Debt-to-income</span><strong>18%</strong><div className="progress"><i className="progress-blue" style={{width:'18%'}}/></div><small>Sample figure only</small></div>
          </>
        ) : (
          <div className="module-mini-empty">
            <p>Calculate your transparent health score across emergency fund, DTI, savings rate, and budgets.</p>
            <NavLink to="/financial-health" className="btn-secondary btn-sm">View Health Analysis →</NavLink>
          </div>
        )}
      </div>
    </section>

    <section className="lower-grid">
      <div className="panel transactions-panel">
        <div className="panel-heading">
          <div><h2>Recent transactions</h2><p>{demoMode?'Illustrative sample activity':'Latest saved financial activity'}</p></div>
          <NavLink className="text-link" to="/transactions">View all <ArrowUpRight size={14}/></NavLink>
        </div>
        <div className="transaction-list">{transactions.slice(0,5).map(t=><TransactionRow key={t.id} tx={t}/>)}</div>
        {!transactions.length&&<div className="empty-state">No transactions yet. Add one to start your cash-flow picture.</div>}
      </div>

      <div className="panel goals-panel">
        <div className="panel-heading">
          <div><h2>Savings goals</h2><p>{demoMode?'Illustrative sample goals':'Track savings milestones'}</p></div>
          <NavLink to="/goals" className="more-btn" aria-label="Manage goals"><Plus size={17}/></NavLink>
        </div>
        {demoMode ? (
          <>
            <div className="goal-card"><div className="goal-icon"><Home size={18}/></div><div className="goal-info"><div className="goal-title"><strong>Emergency fund</strong><span>70%</span></div><div className="progress"><i style={{width:'70%'}}/></div><small>{money(210000)} <span>of {money(300000)}</span></small></div></div>
            <div className="goal-card"><div className="goal-icon goal-icon-blue"><CarFront size={18}/></div><div className="goal-info"><div className="goal-title"><strong>New car</strong><span>32%</span></div><div className="progress"><i className="progress-blue" style={{width:'32%'}}/></div><small>{money(160000)} <span>of {money(500000)}</span></small></div></div>
          </>
        ) : (
          <div className="module-mini-empty">
            <p>Set targets for an emergency fund, home deposit, or vacation.</p>
            <NavLink to="/goals" className="btn-secondary btn-sm">Set a Goal →</NavLink>
          </div>
        )}
      </div>
    </section>
    <div className="demo-note"><Sparkles size={14}/><span>{demoMode?'DEMO DATA · All amounts and charts are illustrative and not linked to an account.':'Real data · Figures use transactions saved to your FinSight account.'}</span></div>
  </div>;
}

function StatCard({title,amount,change,icon:Icon,tone,caption}:{title:string;amount:string;change:string;icon:typeof Wallet;tone:string;caption:string}) { return <div className="stat-card"><div className="stat-top"><span>{title}</span><span className={`stat-icon ${tone}`}><Icon size={18}/></span></div><strong className="stat-amount">{amount}</strong><div className="stat-foot"><span className="change-pill"><TrendingUp size={12}/>{change}</span><span>{caption}</span></div></div> }

function TransactionRow({tx,onEdit,onDelete}:{tx:Tx;onEdit?:()=>void;onDelete?:()=>void}) { const icons:Record<string,typeof BriefcaseBusiness>={Income:BriefcaseBusiness,Housing:Home,Groceries:Wallet,Freelance:FileText,Utilities:CreditCard}; const Icon=icons[tx.category]||Wallet; return <div className="transaction-row"><div className={`tx-icon ${tx.kind}`}><Icon size={17}/></div><div className="tx-name"><strong>{tx.name}</strong><small>{tx.category}</small></div><span className="tx-date">{tx.date}</span><strong className={`tx-amount ${tx.kind}`}>{tx.kind==='income'?'+':'−'}{money(tx.amount)}</strong>{onEdit&&<div className="tx-actions"><button aria-label={`Edit ${tx.name}`} onClick={onEdit}><Pencil size={14}/></button><button aria-label={`Delete ${tx.name}`} onClick={onDelete}><Trash2 size={14}/></button></div>}</div> }

function Transactions({transactions,query,onAdd,onEdit,onDelete,demoMode}:{transactions:Tx[];query:string;onAdd:()=>void;onEdit:(tx:Tx)=>void;onDelete:(tx:Tx)=>void;demoMode:boolean}) {
  const [exporting, setExporting] = useState(false);
  const [filterType, setFilterType] = useState<'ALL' | 'INCOME' | 'EXPENSE'>('ALL');

  const filtered = transactions.filter(t => {
    const matches = `${t.name} ${t.category}`.toLowerCase().includes(query.toLowerCase());
    if (filterType === 'INCOME') return matches && t.kind === 'income';
    if (filterType === 'EXPENSE') return matches && t.kind === 'expense';
    return matches;
  });

  async function handleExport() {
    if (demoMode) {
      alert('Demo mode: In signed-in mode, this downloads a complete RFC-4180 CSV archive of your actual transactions.');
      return;
    }
    setExporting(true);
    try {
      const csv = await financeApi.exportCsv();
      const blob = new Blob([csv], { type: 'text/csv;charset=utf-8;' });
      const url = URL.createObjectURL(blob);
      const link = document.createElement('a');
      link.href = url;
      link.download = 'finsight-transactions.csv';
      document.body.appendChild(link);
      link.click();
      document.body.removeChild(link);
    } catch(e) {
      alert(apiErrorMessage(e));
    } finally {
      setExporting(false);
    }
  }

  return (
    <div className="content">
      <div className="welcome-row">
        <div>
          <div className="eyebrow">YOUR MONEY, IN MOTION</div>
          <h1>Transactions</h1>
          <p className="subheading">Review, filter, and manage your income, spending, and imports.</p>
        </div>
        <div className="dashboard-top-btns">
          <NavLink to="/accounts" className="btn-secondary">
            <UploadCloud size={16}/> Import CSV
          </NavLink>
          <button className="btn-secondary" onClick={handleExport} disabled={exporting}>
            <Download size={16}/> {exporting ? 'Exporting…' : 'Export CSV'}
          </button>
          <button className="btn-primary" onClick={onAdd}>
            <Plus size={17}/> Add transaction
          </button>
        </div>
      </div>

      <div className="tx-filter-bar">
        <button className={`filter-pill ${filterType === 'ALL' ? 'active' : ''}`} onClick={() => setFilterType('ALL')}>All ({transactions.length})</button>
        <button className={`filter-pill ${filterType === 'INCOME' ? 'active' : ''}`} onClick={() => setFilterType('INCOME')}>Income</button>
        <button className={`filter-pill ${filterType === 'EXPENSE' ? 'active' : ''}`} onClick={() => setFilterType('EXPENSE')}>Expenses</button>
      </div>

      <div className="panel full-transactions">
        <div className="panel-heading">
          <div>
            <h2>All activity</h2>
            <p>{filtered.length} transactions · {demoMode ? 'browser demo only' : 'saved to your account'}</p>
          </div>
        </div>
        <div className="transaction-list">
          {filtered.length ? (
            filtered.map(t => <TransactionRow key={t.id} tx={t} onEdit={() => onEdit(t)} onDelete={() => onDelete(t)} />)
          ) : (
            <div className="empty-state">
              {transactions.length ? 'No transactions match your current search/filter.' : 'No saved transactions yet. Add your first transaction or import a statement.'}
            </div>
          )}
        </div>
      </div>
      {demoMode && <div className="demo-note"><Sparkles size={14}/> Sample data is illustrative and stored only in this browser session.</div>}
    </div>
  );
}
