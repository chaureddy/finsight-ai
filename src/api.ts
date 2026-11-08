import axios, { type AxiosError, type InternalAxiosRequestConfig } from 'axios';

const api = axios.create({
  baseURL: import.meta.env.VITE_API_URL || '/api',
  withCredentials: true,
  headers: { Accept: 'application/json' },
});

type CsrfResponse = { headerName: string; token: string };
export type UserProfile = { id: string; email: string; displayName: string };
export type CashFlowSummary = { income: number; expenses: number; netCashFlow: number; savingsRatePercent: number };
export type ApiTransaction = { id: string; description: string; category: string; amount: number; direction: 'INCOME' | 'EXPENSE'; occurredOn: string };
export type TransactionInput = Omit<ApiTransaction, 'id'>;
export type FinancialKind = 'assets' | 'liabilities' | 'investments' | 'insurance';
export type FinancialRecord = Record<string, string | number | null> & { id: string };
export type FinancialOverview = { totalAssets:number; totalInvestments:number; totalLiabilities:number; netWorth:number; monthlyEmiCommitment:number; totalInsuranceCoverage:number; monthlyIncome:number; monthlyExpenses:number; monthlySavings:number; savingsRatePercent:number };
export type BudgetRecord = {id:string;name:string;category:string;monthlyLimit:number;startDate:string;endDate:string;alertThresholdPercentage:number;amountSpent:number;remainingAmount:number;percentageConsumed:number;alertTriggered:boolean;exceeded:boolean;status:string};
export type GoalRecord = {id:string;name:string;category:string;targetAmount:number;currentAmount:number;targetDate:string;priority:'LOW'|'MEDIUM'|'HIGH';description:string|null;progressPercentage:number;remainingAmount:number;requiredMonthlySavings:number;assumedMonthlySavings:number;projectedCompletionDate:string|null;status:'ON_TRACK'|'AT_RISK'|'COMPLETED'};
export type RecurringRecord = {id:string;title:string;type:'INCOME'|'EXPENSE';amount:number;category:string;frequency:'DAILY'|'WEEKLY'|'MONTHLY'|'YEARLY';nextDueDate:string;endDate:string|null;active:boolean};
export type ForecastData = {months:{month:string;projectedIncome:number;projectedExpenses:number;projectedSavings:number;expectedClosingCashBalance:number}[];upcomingCommitments:{title:string;category:string;amount:number;type:string;dueDate:string;frequency:string}[];historicalMonthsUsed:number;confidence:string;averageHistoricalIncome:number;averageHistoricalExpenses:number;recurringMonthlyIncome:number;recurringMonthlyExpenses:number;assumptions:string[]};
export type HealthData = {score:number|null;confidenceWeight:number;confidence:string;components:{key:string;label:string;weight:number;points:number;observedValue:number|null;available:boolean;explanation:string}[];limitations:string[];disclaimer:string};
export type SpendingData = {topCategories:{category:string;amount:number}[];monthlyTrends:{month:string;income:number;expenses:number;savings:number;partialMonth:boolean}[];categoryObservations:{category:string;currentMonthSpend:number;previousThreeMonthAverage:number;increase:number;elevated:boolean;interpretation:string}[];budgetOverruns:{name:string;category:string;limit:number;spent:number;remaining:number;status:string}[];recurringPaymentConcentration:{category:string;monthlyEquivalent:number}[];notes:string[]};
export type PayPalStatus = {configured:boolean;connected:boolean;state:'CONNECTED'|'NOT_CONNECTED'|'NOT_CONFIGURED';connectedAt:string|null;lastVerifiedAt:string|null};
export type PayPalReviewItem = {id:string;paypalTransactionId:string|null;occurredOn:string|null;amount:number|null;currencyCode:string|null;eventCode:string|null;reason:string};
export type PayPalImportResult = {importedCount:number;duplicateCount:number;reviewCount:number;environment:'SANDBOX';state:'CONNECTED'};
export type PayPalOrderResponse = { orderId: string; status: string; approveUrl: string };
export type CreateOrderParams = { amount: number; currency: string; description?: string };
export type CaptureOrderParams = { description?: string; category?: string; direction?: 'INCOME' | 'EXPENSE' };
export type ConnectedAccount = { id: string; provider: string; accountName: string; accountType: string; status: string; lastSync: string | null; transactionCount: number; balance: number | null };
export type AccountsOverview = { accounts: ConnectedAccount[]; totalConnected: number; totalTransactionsImported: number };
export type CsvImportResult = { importedCount: number; duplicateCount: number; skippedCount: number; warnings: string[] };
export type AiChatResponse = { reply: string; suggestedQuestions: string[]; groundingData: Record<string, unknown>; provider: string };
export type SmartInsight = { id: string; title: string; category: string; tone: string; message: string; actionLink: string; actionLabel: string };
export type ComprehensiveIntelligence = {
  topMerchants: { merchant: string; totalAmount: number; transactionCount: number; topCategory: string }[];
  detectedSubscriptions: { description: string; averageAmount: number; category: string; frequency: string; occurrences: number; lastSeen: string }[];
  refunds: { description: string; amount: number; date: string; sourceProvider: string }[];
  recommendations: { title: string; category: string; impact: string; advice: string; potentialSavings: number | null }[];
  totalMonthlySubscriptions: number;
  highestCategorySpend: number;
  highestSpendCategoryName: string;
};
let csrf: CsrfResponse | undefined;

async function getCsrf(): Promise<CsrfResponse> {
  if (!csrf) csrf = (await api.get<CsrfResponse>('/auth/csrf')).data;
  return csrf;
}

api.interceptors.request.use(async (config: InternalAxiosRequestConfig) => {
  if (['post', 'put', 'patch', 'delete'].includes((config.method || 'get').toLowerCase())) {
    const token = await getCsrf();
    config.headers.set(token.headerName, token.token);
  }
  return config;
});

api.interceptors.response.use(response => response, async (error: AxiosError) => {
  const url = error.config?.url || '';
  const config = error.config as (InternalAxiosRequestConfig & { _csrfRetry?: boolean }) | undefined;
  if (error.response?.status === 403 && config && !config._csrfRetry && ['post','put','patch','delete'].includes((config.method||'').toLowerCase())) {
    csrf=undefined; config._csrfRetry=true;
    const refreshed=await getCsrf(); config.headers.set(refreshed.headerName,refreshed.token);
    return api.request(config);
  }
  if (error.response?.status === 401 && !url.includes('/auth/login') && !url.includes('/auth/register') && !url.includes('/auth/me')) {
    window.dispatchEvent(new CustomEvent('finsight:session-expired'));
  }
  return Promise.reject(error);
});

export const authApi = {
  async currentUser() { return (await api.get<UserProfile>('/auth/me')).data; },
  async register(input: { email: string; password: string; displayName: string }) {
    return (await api.post<UserProfile>('/auth/register', input)).data;
  },
  async login(email: string, password: string) {
    const body = new URLSearchParams({ email, password });
    await api.post('/auth/login', body, { headers: { 'Content-Type': 'application/x-www-form-urlencoded' } });
    return this.currentUser();
  },
  async updateProfile(displayName: string) {
    return (await api.put<UserProfile>('/auth/profile', { displayName })).data;
  },
  async logout() { await api.post('/auth/logout'); csrf = undefined; },
};

export const financeApi = {
  async transactions() { return (await api.get<ApiTransaction[]>('/transactions')).data; },
  async createTransaction(input: TransactionInput) { return (await api.post<ApiTransaction>('/transactions', input)).data; },
  async updateTransaction(id: string, input: TransactionInput) { return (await api.put<ApiTransaction>(`/transactions/${id}`, input)).data; },
  async deleteTransaction(id: string) { await api.delete(`/transactions/${id}`); },
  async importCsv(csvData: string, accountLabel?: string) { return (await api.post<CsvImportResult>('/transactions/import-csv', { csvData, accountLabel })).data; },
  async exportCsv(): Promise<string> { return (await api.get<string>('/transactions/export', { responseType: 'text' })).data; },
  async accounts() { return (await api.get<AccountsOverview>('/accounts')).data; },
  async deleteAccount(id: string) { await api.delete(`/accounts/${id}`); },
  async cashFlow() { return (await api.get<CashFlowSummary>('/analytics/cash-flow')).data; },
  async financialRecords(kind: FinancialKind) { return (await api.get<FinancialRecord[]>(`/${kind}`)).data; },
  async createFinancialRecord(kind: FinancialKind, input: Record<string, string | number | null>) { return (await api.post<FinancialRecord>(`/${kind}`, input)).data; },
  async updateFinancialRecord(kind: FinancialKind, id: string, input: Record<string, string | number | null>) { return (await api.put<FinancialRecord>(`/${kind}/${id}`, input)).data; },
  async deleteFinancialRecord(kind: FinancialKind, id: string) { await api.delete(`/${kind}/${id}`); },
  async financialOverview() { return (await api.get<FinancialOverview>('/financial-overview')).data; },
  async budgets() { return (await api.get<BudgetRecord[]>('/budgets')).data; },
  async createBudget(input:Record<string,string|number|null>) { return (await api.post<BudgetRecord>('/budgets',input)).data; },
  async updateBudget(id:string,input:Record<string,string|number|null>) { return (await api.put<BudgetRecord>(`/budgets/${id}`,input)).data; },
  async deleteBudget(id:string) { await api.delete(`/budgets/${id}`); },
  async goals() { return (await api.get<GoalRecord[]>('/goals')).data; },
  async createGoal(input:Record<string,string|number|null>) { return (await api.post<GoalRecord>('/goals',input)).data; },
  async updateGoal(id:string,input:Record<string,string|number|null>) { return (await api.put<GoalRecord>(`/goals/${id}`,input)).data; },
  async deleteGoal(id:string) { await api.delete(`/goals/${id}`); },
  async recurring() { return (await api.get<RecurringRecord[]>('/recurring-transactions')).data; },
  async upcomingRecurring() { return (await api.get<RecurringRecord[]>('/recurring-transactions/upcoming')).data; },
  async createRecurring(input:Record<string,string|number|boolean|null>) { return (await api.post<RecurringRecord>('/recurring-transactions',input)).data; },
  async updateRecurring(id:string,input:Record<string,string|number|boolean|null>) { return (await api.put<RecurringRecord>(`/recurring-transactions/${id}`,input)).data; },
  async setRecurringActive(id:string,active:boolean) { return (await api.patch<RecurringRecord>(`/recurring-transactions/${id}/active`,{active})).data; },
  async deleteRecurring(id:string) { await api.delete(`/recurring-transactions/${id}`); },
  async recordOccurrence(id:string,occurrenceDate:string) { return (await api.post(`/recurring-transactions/${id}/occurrences`,{occurrenceDate})).data; },
  async forecast() { return (await api.get<ForecastData>('/forecast')).data; },
  async financialHealth() { return (await api.get<HealthData>('/financial-health')).data; },
  async spendingIntelligence() { return (await api.get<SpendingData>('/intelligence/spending')).data; },
  async paypalStatus() { return (await api.get<PayPalStatus>('/integrations/paypal/status')).data; },
  async paypalDisconnect() { await api.post('/integrations/paypal/disconnect'); },
  async paypalReviews() { return (await api.get<PayPalReviewItem[]>('/integrations/paypal/review')).data; },
  async paypalDismissReview(id: string) { await api.post(`/integrations/paypal/review/${id}/dismiss`); },
  async paypalApproveReview(id: string, data?: { description?: string; category?: string; direction?: 'INCOME'|'EXPENSE' }) { return (await api.post<ApiTransaction>(`/integrations/paypal/review/${id}/approve`, data || {})).data; },
  async paypalOAuthAuthorize() { return (await api.get<{ authorizationUrl: string; state: string }>('/integrations/paypal/oauth/authorize')).data; },
  async paypalCreateOrder(params: CreateOrderParams) { return (await api.post<PayPalOrderResponse>('/integrations/paypal/orders', params)).data; },
  async paypalCaptureOrder(orderId: string, params?: CaptureOrderParams) { return (await api.post<ApiTransaction>(`/integrations/paypal/orders/${orderId}/capture`, params || {})).data; },
  async importPayPal(startDate:string,endDate:string) { return (await api.post<PayPalImportResult>('/integrations/paypal/import',{startDate,endDate})).data; },
  async aiChat(message: string) { return (await api.post<AiChatResponse>('/assistant/chat', { message })).data; },
  async aiInsights() { return (await api.get<SmartInsight[]>('/assistant/insights')).data; },
  async advancedIntelligence() { return (await api.get<ComprehensiveIntelligence>('/intelligence/advanced')).data; },
};

export function apiErrorMessage(error: unknown): string {
  if (axios.isAxiosError(error)) {
    const body=error.response?.data;
    const message = body && typeof body === 'object' && ('message' in body || 'detail' in body || 'error' in body)
      ? String('message' in body ? body.message : 'detail' in body ? body.detail : body.error) : '';
    if (error.response?.status === 401) return 'Your session has ended. Please sign in again.';
    if (error.response?.status === 409) return message || 'This record conflicts with existing data or has already been recorded.';
    if (error.response?.status === 400) return message || 'Please check the information and try again.';
    if (!error.response) return 'Cannot reach the FinSight API. Check that the backend is running.';
  }
  return 'Something went wrong. Please try again.';
}
