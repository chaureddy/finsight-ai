import { useEffect, useRef, useState, type FormEvent } from 'react';
import { Bot, Lightbulb, LoaderCircle, Send, ShieldCheck, Sparkles, User } from 'lucide-react';
import { apiErrorMessage, financeApi, type SmartInsight } from './api';
import './assistant.css';

type Message = {
  id: string;
  sender: 'user' | 'assistant';
  text: string;
  time: string;
  suggestions?: string[];
  grounding?: Record<string, unknown>;
};

const defaultSuggestions = [
  'What is my current net worth?',
  'Where is my money going this month?',
  'What subscriptions am I paying for?',
  'How can I save money?',
  'What is my financial health score?',
  'Show my recent transactions',
];

export default function AiAssistantPage({ enabled, displayName }: { enabled: boolean; displayName: string }) {
  const [messages, setMessages] = useState<Message[]>([
    {
      id: 'welcome',
      sender: 'assistant',
      text: `Hello ${displayName}! I am your **FinSight AI Financial Intelligence Assistant**.\n\nI have direct, privacy-preserving access to your actual financial transactions, assets, liabilities, and budgets. Ask me anything to get instant, data-grounded insights!`,
      time: new Date().toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' }),
      suggestions: defaultSuggestions,
    },
  ]);
  const [input, setInput] = useState('');
  const [loading, setLoading] = useState(false);
  const [insights, setInsights] = useState<SmartInsight[]>([]);
  const scrollRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (enabled) {
      financeApi.aiInsights().then(setInsights).catch(() => {});
    }
  }, [enabled]);

  useEffect(() => {
    scrollRef.current?.scrollIntoView?.({ behavior: 'smooth' });
  }, [messages, loading]);

  async function sendMessage(textToSend?: string) {
    const text = (textToSend || input).trim();
    if (!text || loading) return;

    const userMsg: Message = {
      id: `u-${Date.now()}`,
      sender: 'user',
      text,
      time: new Date().toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' }),
    };

    setMessages(prev => [...prev, userMsg]);
    setInput('');
    setLoading(true);

    try {
      if (!enabled) {
        // Demo response
        setTimeout(() => {
          setMessages(prev => [
            ...prev,
            {
              id: `a-${Date.now()}`,
              sender: 'assistant',
              text: `In Demo Mode, illustrative data is shown. Sign in to analyze your real accounts and transactions.\n\n- **Sample Monthly Income**: ₹85,000\n- **Sample Monthly Spending**: ₹48,920\n- **Sample Savings Rate**: 42%`,
              time: new Date().toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' }),
              suggestions: defaultSuggestions.slice(0, 3),
            },
          ]);
          setLoading(false);
        }, 500);
        return;
      }

      const res = await financeApi.aiChat(text);
      setMessages(prev => [
        ...prev,
        {
          id: `a-${Date.now()}`,
          sender: 'assistant',
          text: res.reply,
          time: new Date().toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' }),
          suggestions: res.suggestedQuestions,
          grounding: res.groundingData,
        },
      ]);
    } catch (e) {
      setMessages(prev => [
        ...prev,
        {
          id: `a-${Date.now()}`,
          sender: 'assistant',
          text: `⚠️ **Unable to process query**: ${apiErrorMessage(e)}`,
          time: new Date().toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' }),
          suggestions: defaultSuggestions.slice(0, 3),
        },
      ]);
    } finally {
      setLoading(false);
    }
  }

  function handleForm(e: FormEvent) {
    e.preventDefault();
    void sendMessage();
  }

  return (
    <div className="content assistant-page">
      <div className="welcome-row">
        <div>
          <div className="eyebrow">INTELLIGENT FINANCIAL COPILOT</div>
          <h1>AI Financial Assistant</h1>
          <p className="subheading">Ask questions grounded in your actual transactions, budgets, and accounts.</p>
        </div>
        <div className="assistant-badge">
          <Sparkles size={15} /> 100% Data Grounded
        </div>
      </div>

      {insights.length > 0 && (
        <div className="insights-banner panel">
          <div className="insights-banner-title">
            <Lightbulb size={17} />
            <strong>Smart Financial Insights</strong>
          </div>
          <div className="insights-pills-row">
            {insights.map(i => (
              <div className={`insight-pill ${i.tone}`} key={i.id}>
                <strong>{i.title}:</strong> <span>{i.message}</span>
              </div>
            ))}
          </div>
        </div>
      )}

      <div className="chat-container panel">
        <div className="chat-stream">
          {messages.map(m => (
            <div className={`chat-message ${m.sender}`} key={m.id}>
              <div className="message-avatar">
                {m.sender === 'assistant' ? <Bot size={18} /> : <User size={18} />}
              </div>
              <div className="message-content">
                <div className="message-meta">
                  <strong>{m.sender === 'assistant' ? 'FinSight AI' : displayName}</strong>
                  <time>{m.time}</time>
                  {m.sender === 'assistant' && (
                    <span className="grounded-pill">
                      <ShieldCheck size={12} /> Grounded
                    </span>
                  )}
                </div>
                <div className="message-body" dangerouslySetInnerHTML={{ __html: renderSimpleMarkdown(m.text) }} />

                {m.suggestions && m.suggestions.length > 0 && (
                  <div className="suggestions-list">
                    {m.suggestions.map(q => (
                      <button key={q} className="suggestion-btn" onClick={() => void sendMessage(q)}>
                        {q}
                      </button>
                    ))}
                  </div>
                )}
              </div>
            </div>
          ))}

          {loading && (
            <div className="chat-message assistant">
              <div className="message-avatar"><Bot size={18} /></div>
              <div className="message-content">
                <div className="message-meta"><strong>FinSight AI</strong></div>
                <div className="typing-indicator">
                  <LoaderCircle size={16} className="spin" />
                  <span>Analyzing your financial data…</span>
                </div>
              </div>
            </div>
          )}
          <div ref={scrollRef} />
        </div>

        <form className="chat-input-bar" onSubmit={handleForm}>
          <input
            type="text"
            placeholder="Ask about your spending, net worth, budgets, or savings opportunities…"
            value={input}
            onChange={e => setInput(e.target.value)}
            disabled={loading}
          />
          <button className="btn-primary" type="submit" disabled={loading || !input.trim()} aria-label="Send message">
            <Send size={16} />
          </button>
        </form>
      </div>
    </div>
  );
}

function renderSimpleMarkdown(md: string): string {
  if (!md) return '';
  let html = md
    // Bold
    .replace(/\*\*(.*?)\*\*/g, '<strong>$1</strong>')
    // Headings
    .replace(/^### (.*$)/gim, '<h4>$1</h4>')
    .replace(/^## (.*$)/gim, '<h3>$1</h3>')
    .replace(/^# (.*$)/gim, '<h2>$1</h2>')
    // Blockquotes
    .replace(/^\> (.*$)/gim, '<blockquote>$1</blockquote>')
    // Bullet lists
    .replace(/^\- (.*$)/gim, '<li>$1</li>')
    // Line breaks
    .replace(/\n\n/g, '<br/><br/>');

  // Wrap list items
  if (html.includes('<li>')) {
    html = html.replace(/(<li>.*?<\/li>)+/gs, '<ul>$&</ul>');
  }

  // Handle tables
  if (html.includes('|')) {
    const lines = html.split('<br/><br/>');
    const processed = lines.map(block => {
      if (block.includes('|') && block.includes('---')) {
        const rows = block.split('\n').filter(r => r.trim().startsWith('|'));
        if (rows.length >= 2) {
          let tableHtml = '<div class="table-wrap"><table class="chat-table"><thead><tr>';
          const headerCols = rows[0].split('|').filter(c => c.trim().length > 0);
          headerCols.forEach(c => { tableHtml += `<th>${c.trim()}</th>`; });
          tableHtml += '</tr></thead><tbody>';
          for (let i = 2; i < rows.length; i++) {
            tableHtml += '<tr>';
            const rowCols = rows[i].split('|').filter(c => c.trim().length > 0);
            rowCols.forEach(c => { tableHtml += `<td>${c.trim()}</td>`; });
            tableHtml += '</tr>';
          }
          tableHtml += '</tbody></table></div>';
          return tableHtml;
        }
      }
      return block;
    });
    html = processed.join('<br/><br/>');
  }

  return html;
}

