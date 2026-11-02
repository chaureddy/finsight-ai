import { useState } from 'react';
import { Activity, ArrowRight, ShieldCheck } from 'lucide-react';

export default function AuthScreen({ onLogin, onRegister, onDemo, loading, error }: {
  onLogin: (email: string, password: string) => Promise<void>;
  onRegister: (input: { email: string; password: string; displayName: string }) => Promise<void>;
  onDemo: () => void;
  loading: boolean;
  error: string;
}) {
  const [registerMode, setRegisterMode] = useState(false);
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [displayName, setDisplayName] = useState('');
  async function submit(event: React.FormEvent) {
    event.preventDefault();
    if (registerMode) await onRegister({ email, password, displayName });
    else await onLogin(email, password);
  }
  return <div className="auth-screen"><div className="auth-card"><div className="auth-brand"><span className="brand-mark"><Activity size={20}/></span><span>finsight<span className="brand-ai">.ai</span></span></div><div className="eyebrow">YOUR FINANCIAL WORKSPACE</div><h1>{registerMode ? 'Create your account' : 'Welcome back'}</h1><p className="auth-subtitle">{registerMode ? 'Start building a clearer picture of your finances.' : 'Sign in to see your personal financial picture.'}</p>
    <form onSubmit={submit} className="auth-form">{registerMode && <label>Your name<input autoComplete="name" required maxLength={100} value={displayName} onChange={e=>setDisplayName(e.target.value)} placeholder="Alex Smith"/></label>}<label>Email address<input type="email" autoComplete="email" required maxLength={254} value={email} onChange={e=>setEmail(e.target.value)} placeholder="you@example.com"/></label><label>Password<input type="password" autoComplete={registerMode?'new-password':'current-password'} required minLength={registerMode?12:1} maxLength={72} value={password} onChange={e=>setPassword(e.target.value)} placeholder={registerMode?'At least 12 characters':'Your password'}/>{registerMode && <small>Use at least 12 characters.</small>}</label>{error && <div role="alert" className="auth-error">{error}</div>}<button className="btn-primary auth-submit" disabled={loading}>{loading?'Please wait…':registerMode?'Create account':'Sign in'}<ArrowRight size={16}/></button></form>
    <div className="auth-switch">{registerMode?'Already have an account?':'New to FinSight?'} <button onClick={()=>setRegisterMode(!registerMode)}>{registerMode?'Sign in':'Create an account'}</button></div><div className="auth-divider"><span>OR</span></div><button className="btn-secondary demo-button" onClick={onDemo}>Explore labelled demo data</button><p className="auth-security"><ShieldCheck size={14}/> Your account is protected with a private server session.</p></div><p className="auth-footer">FinSight AI · See Your Money. Understand Your Future.</p></div>;
}
