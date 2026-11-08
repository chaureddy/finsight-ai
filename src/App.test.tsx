// @vitest-environment jsdom
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { BrowserRouter } from 'react-router-dom';
import App from './App';

const apiMocks = vi.hoisted(() => ({
  currentUser: vi.fn(), register: vi.fn(), login: vi.fn(), logout: vi.fn(), updateProfile: vi.fn(),
  transactions: vi.fn(), createTransaction: vi.fn(), updateTransaction: vi.fn(), deleteTransaction: vi.fn(), cashFlow: vi.fn(), financialOverview: vi.fn(), financialRecords: vi.fn(), createFinancialRecord: vi.fn(), updateFinancialRecord: vi.fn(), deleteFinancialRecord: vi.fn(), budgets:vi.fn(),createBudget:vi.fn(),updateBudget:vi.fn(),deleteBudget:vi.fn(),goals:vi.fn(),createGoal:vi.fn(),updateGoal:vi.fn(),deleteGoal:vi.fn(),recurring:vi.fn(),upcomingRecurring:vi.fn(),createRecurring:vi.fn(),updateRecurring:vi.fn(),setRecurringActive:vi.fn(),deleteRecurring:vi.fn(),recordOccurrence:vi.fn(),forecast:vi.fn(),financialHealth:vi.fn(),spendingIntelligence:vi.fn(),aiInsights:vi.fn(),
}));
vi.mock('./api', () => ({
  authApi: { currentUser: apiMocks.currentUser, register: apiMocks.register, login: apiMocks.login, logout: apiMocks.logout, updateProfile: apiMocks.updateProfile },
  financeApi: { transactions: apiMocks.transactions, createTransaction: apiMocks.createTransaction, updateTransaction: apiMocks.updateTransaction, deleteTransaction: apiMocks.deleteTransaction, cashFlow: apiMocks.cashFlow, financialOverview: apiMocks.financialOverview, financialRecords: apiMocks.financialRecords, createFinancialRecord: apiMocks.createFinancialRecord, updateFinancialRecord: apiMocks.updateFinancialRecord, deleteFinancialRecord: apiMocks.deleteFinancialRecord, budgets:apiMocks.budgets,createBudget:apiMocks.createBudget,updateBudget:apiMocks.updateBudget,deleteBudget:apiMocks.deleteBudget,goals:apiMocks.goals,createGoal:apiMocks.createGoal,updateGoal:apiMocks.updateGoal,deleteGoal:apiMocks.deleteGoal,recurring:apiMocks.recurring,upcomingRecurring:apiMocks.upcomingRecurring,createRecurring:apiMocks.createRecurring,updateRecurring:apiMocks.updateRecurring,setRecurringActive:apiMocks.setRecurringActive,deleteRecurring:apiMocks.deleteRecurring,recordOccurrence:apiMocks.recordOccurrence,forecast:apiMocks.forecast,financialHealth:apiMocks.financialHealth,spendingIntelligence:apiMocks.spendingIntelligence,aiInsights:apiMocks.aiInsights },
  apiErrorMessage: () => 'API request failed',
}));

class TestResizeObserver { observe() {} unobserve() {} disconnect() {} }
Object.defineProperty(globalThis, 'ResizeObserver', { writable: true, value: TestResizeObserver });
const user = { id: 'user-1', displayName: 'Alex Smith', email: 'alex@example.com' };
const savedTx = { id: 'tx-1', description: 'Paycheck', category: 'Salary', amount: 85000, direction: 'INCOME', occurredOn: '2026-11-02' };
function renderApp() { window.history.replaceState({},'', '/'); return render(<BrowserRouter><App /></BrowserRouter>); }
async function enterDemo() { await screen.findByRole('heading', { name: /welcome back/i }); fireEvent.click(screen.getByRole('button', { name: /explore labelled demo data/i })); }

beforeEach(() => {
  vi.clearAllMocks();
  apiMocks.currentUser.mockRejectedValue({ response: { status: 401 } });
  apiMocks.register.mockResolvedValue(user); apiMocks.login.mockResolvedValue(user); apiMocks.logout.mockResolvedValue(undefined);
  apiMocks.transactions.mockResolvedValue([]); apiMocks.createTransaction.mockResolvedValue(savedTx); apiMocks.updateTransaction.mockResolvedValue(savedTx); apiMocks.deleteTransaction.mockResolvedValue(undefined);
  apiMocks.cashFlow.mockResolvedValue({ income: 85000, expenses: 0, netCashFlow: 85000, savingsRatePercent: 100 });
  apiMocks.financialOverview.mockResolvedValue({ totalAssets:0,totalInvestments:0,totalLiabilities:0,netWorth:0,monthlyEmiCommitment:0,totalInsuranceCoverage:0,monthlyIncome:85000,monthlyExpenses:0,monthlySavings:85000,savingsRatePercent:100 });
  apiMocks.financialRecords.mockResolvedValue([]); apiMocks.createFinancialRecord.mockResolvedValue({id:'asset-1',name:'Emergency cash',category:'Cash',currentValue:500});
  apiMocks.budgets.mockResolvedValue([]);apiMocks.createBudget.mockResolvedValue({id:'budget-1',name:'Food plan',category:'Food',monthlyLimit:1200,amountSpent:0,remainingAmount:1200,percentageConsumed:0,alertTriggered:false,exceeded:false,status:'ON_TRACK'});
  apiMocks.aiInsights.mockResolvedValue([]);
  apiMocks.updateProfile.mockResolvedValue(user);
});
afterEach(cleanup);

describe('FinSight workspace', () => {
  it('keeps demo figures labelled as illustrative data', async () => {
    renderApp(); await screen.findByRole('heading',{name:/welcome back/i});
    expect(screen.queryByRole('alert')).toBeNull();
    fireEvent.click(screen.getByRole('button',{name:/explore labelled demo data/i}));
    expect(screen.getByRole('heading', { name: /good morning, alex/i })).toBeTruthy();
    expect(screen.getByText(/demo data.*illustrative/i)).toBeTruthy();
  });

  it('creates a demo transaction in preview mode only', async () => {
    renderApp(); await enterDemo();
    fireEvent.click(screen.getAllByRole('button', { name: /add transaction/i })[0]);
    const form = document.querySelector('form'); if (!form) throw new Error('Transaction form did not open');
    fireEvent.change(within(form).getByLabelText(/description/i), { target: { value: 'Test income' } });
    fireEvent.change(within(form).getByLabelText(/amount/i), { target: { value: '1200' } });
    fireEvent.change(within(form).getByLabelText(/category/i), { target: { value: 'Salary' } });
    fireEvent.change(within(form).getByLabelText(/type/i), { target: { value: 'income' } });
    fireEvent.click(within(form).getByRole('button', { name: /save transaction/i }));
    expect(await screen.findByText('Test income')).toBeTruthy();
    expect(apiMocks.createTransaction).not.toHaveBeenCalled();
  });

  it('loads real signed-in dashboard and transaction data from the API', async () => {
    apiMocks.currentUser.mockResolvedValue(user); apiMocks.transactions.mockResolvedValue([savedTx]);
    renderApp();
    expect(await screen.findByRole('heading', { name: /good morning, alex/i })).toBeTruthy();
    expect(await screen.findByText('Paycheck')).toBeTruthy();
    expect(screen.getByText(/real data · figures use transactions saved/i)).toBeTruthy();
    expect(screen.getAllByText(/₹0/).length).toBeGreaterThan(0);
    expect(apiMocks.cashFlow).toHaveBeenCalled();
  });

  it('updates and deletes persisted transactions and signs out', async () => {
    apiMocks.currentUser.mockResolvedValue(user); apiMocks.transactions.mockResolvedValue([savedTx]);
    renderApp(); await screen.findByText('Paycheck');
    fireEvent.click(screen.getByRole('link', { name: /transactions/i }));
    fireEvent.click(await screen.findByRole('button', { name: /edit paycheck/i }));
    const form=document.querySelector('form'); if(!form) throw new Error('Edit form did not open');
    fireEvent.change(within(form).getByLabelText(/description/i),{target:{value:'Updated paycheck'}});
    fireEvent.click(within(form).getByRole('button',{name:/save changes/i}));
    await waitFor(()=>expect(apiMocks.updateTransaction).toHaveBeenCalledWith('tx-1',expect.objectContaining({description:'Updated paycheck'})));
    fireEvent.click(await screen.findByRole('button',{name:/delete paycheck/i}));
    await waitFor(()=>expect(apiMocks.deleteTransaction).toHaveBeenCalledWith('tx-1'));
    fireEvent.click(screen.getByRole('button',{name:/alex smith/i}));
    expect(await screen.findByRole('heading',{name:/welcome back/i})).toBeTruthy();
    expect(apiMocks.logout).toHaveBeenCalled();
  });

  it('registers, signs in, and sends a transaction to the API', async () => {
    apiMocks.register.mockResolvedValue(user); apiMocks.login.mockResolvedValue(user);
    apiMocks.transactions.mockResolvedValueOnce([]).mockResolvedValueOnce([savedTx]);
    renderApp(); await screen.findByRole('heading', { name: /welcome back/i });
    fireEvent.click(screen.getByRole('button', { name: /create an account/i }));
    fireEvent.change(screen.getByLabelText(/your name/i), { target: { value: 'Alex Smith' } });
    fireEvent.change(screen.getByLabelText(/email address/i), { target: { value: 'alex@example.com' } });
    fireEvent.change(screen.getByLabelText(/password/i), { target: { value: 'long-and-safe-password' } });
    fireEvent.click(screen.getByRole('button', { name: /create account/i }));
    await screen.findByText(/real data · figures use transactions saved/i);
    expect(apiMocks.register).toHaveBeenCalledWith({email:'alex@example.com',password:'long-and-safe-password',displayName:'Alex Smith'});
    expect(apiMocks.login).toHaveBeenCalledWith('alex@example.com','long-and-safe-password');
    fireEvent.click(screen.getAllByRole('button', { name: /add transaction/i })[0]);
    const form = document.querySelector('form'); if (!form) throw new Error('Transaction form did not open');
    fireEvent.change(within(form).getByLabelText(/description/i), { target: { value: 'Paycheck' } });
    fireEvent.change(within(form).getByLabelText(/amount/i), { target: { value: '85000' } });
    fireEvent.change(within(form).getByLabelText(/category/i), { target: { value: 'Salary' } });
    fireEvent.change(within(form).getByLabelText(/type/i), { target: { value: 'income' } });
    fireEvent.click(within(form).getByRole('button', { name: /save transaction/i }));
    await waitFor(() => expect(apiMocks.createTransaction).toHaveBeenCalled());
    expect(apiMocks.createTransaction.mock.calls[0][0]).toMatchObject({description:'Paycheck',category:'Salary',amount:85000,direction:'INCOME'});
  });

  it('loads owner financial records and submits an asset through the API client', async () => {
    apiMocks.currentUser.mockResolvedValue(user);
    apiMocks.financialRecords.mockResolvedValueOnce([]).mockResolvedValueOnce([{id:'asset-1',name:'Emergency cash',category:'Cash',currentValue:500,purchaseValue:500,purchaseDate:'2026-11-01',description:null}]);
    renderApp();
    await screen.findByRole('heading', {name:/good morning, alex/i});
    fireEvent.click(screen.getByRole('link',{name:/assets/i}));
    await screen.findByText(/no assets recorded/i);
    fireEvent.click(screen.getAllByRole('button',{name:/add asset/i})[0]);
    const modal=document.querySelector('.financial-form'); if(!modal)throw new Error('Asset form did not open');
    fireEvent.change(within(modal as HTMLElement).getByLabelText(/asset name/i),{target:{value:'Emergency cash'}});
    fireEvent.change(within(modal as HTMLElement).getByLabelText(/category/i),{target:{value:'Cash'}});
    fireEvent.change(within(modal as HTMLElement).getByLabelText(/current value/i),{target:{value:'500'}});
    fireEvent.change(within(modal as HTMLElement).getByLabelText(/purchase value/i),{target:{value:'500'}});
    fireEvent.change(within(modal as HTMLElement).getByLabelText(/purchase date/i),{target:{value:'2026-11-01'}});
    fireEvent.click(within(modal as HTMLElement).getByRole('button',{name:/save record/i}));
    await waitFor(()=>expect(apiMocks.createFinancialRecord).toHaveBeenCalledWith('assets',expect.objectContaining({name:'Emergency cash',currentValue:500,purchaseValue:500})));
    await screen.findByText('Emergency cash');
  });

  it('loads actual budget records and posts a budget through the API client', async () => {
    apiMocks.currentUser.mockResolvedValue(user);
    apiMocks.budgets.mockResolvedValueOnce([]).mockResolvedValueOnce([{
      id: 'budget-1', name: 'Food plan', category: 'Food', monthlyLimit: 1200,
      amountSpent: 0, remainingAmount: 1200, percentageConsumed: 0,
      alertThresholdPercentage: 80, alertTriggered: false, exceeded: false, status: 'ON_TRACK',
    }]);
    renderApp();
    await screen.findByRole('heading', { name: /good morning, alex/i });
    fireEvent.click(screen.getByRole('link', { name: /budgets/i }));
    await screen.findByText(/no budgets yet/i);
    fireEvent.click(screen.getAllByRole('button', { name: /add budget/i })[0]);
    const form = document.querySelector('.financial-form');
    if (!(form instanceof HTMLElement)) throw new Error('Budget form did not open');
    fireEvent.change(within(form).getByLabelText(/budget name/i), { target: { value: 'Food plan' } });
    fireEvent.change(within(form).getByLabelText(/expense category/i), { target: { value: 'Food' } });
    fireEvent.change(within(form).getByLabelText(/monthly limit/i), { target: { value: '1200' } });
    fireEvent.change(within(form).getByLabelText(/alert at/i), { target: { value: '80' } });
    fireEvent.click(within(form).getByRole('button', { name: /save/i }));
    await waitFor(() => expect(apiMocks.createBudget).toHaveBeenCalledWith(expect.objectContaining({
      name: 'Food plan', category: 'Food', monthlyLimit: 1200,
    })));
  });

  it('guest user sees Login and Register tabs and switches between them', async () => {
    renderApp();
    await screen.findByRole('heading', { name: /welcome back/i });
    expect(screen.getByRole('tab', { name: /sign in/i })).toBeTruthy();
    expect(screen.getByRole('tab', { name: /create account/i })).toBeTruthy();

    fireEvent.click(screen.getByRole('tab', { name: /create account/i }));
    expect(await screen.findByRole('heading', { name: /create your account/i })).toBeTruthy();

    fireEvent.click(screen.getByRole('tab', { name: /sign in/i }));
    expect(await screen.findByRole('heading', { name: /welcome back/i })).toBeTruthy();
  });

  it('displays an error when login fails', async () => {
    apiMocks.login.mockRejectedValue({ response: { status: 401, data: { message: 'Invalid credentials' } } });
    renderApp();
    await screen.findByRole('heading', { name: /welcome back/i });

    fireEvent.change(screen.getByLabelText(/email address/i), { target: { value: 'bad@example.com' } });
    fireEvent.change(screen.getByLabelText(/password/i), { target: { value: 'wrongpassword' } });
    fireEvent.click(screen.getByRole('button', { name: /sign in/i }));

    expect(await screen.findByRole('alert')).toBeTruthy();
  });

  it('authenticated user sees profile avatar, opens menu, and navigates to profile', async () => {
    apiMocks.currentUser.mockResolvedValue(user);
    renderApp();
    await screen.findByRole('heading', { name: /good morning, alex/i });

    const avatarBtn = screen.getByRole('button', { name: /open profile menu/i });
    expect(avatarBtn).toBeTruthy();
    fireEvent.click(avatarBtn);

    const menu = screen.getByRole('menu', { name: /user profile options/i });
    expect(menu).toBeTruthy();
    expect(within(menu).getByText('alex@example.com')).toBeTruthy();
    expect(within(menu).getByRole('menuitem', { name: /account settings/i })).toBeTruthy();

    const profileLink = within(menu).getByRole('menuitem', { name: /profile/i });
    fireEvent.click(profileLink);

    expect(await screen.findByRole('heading', { name: /user profile/i })).toBeTruthy();
    expect(screen.getByDisplayValue('alex@example.com')).toBeTruthy();
  });

  it('profile page updates display name and reflects in header', async () => {
    apiMocks.currentUser.mockResolvedValue(user);
    apiMocks.updateProfile.mockResolvedValue({ id: 'user-1', displayName: 'Alexandre Great', email: 'alex@example.com' });
    renderApp();
    await screen.findByRole('heading', { name: /good morning, alex/i });

    fireEvent.click(screen.getByRole('button', { name: /open profile menu/i }));
    fireEvent.click(screen.getByRole('menuitem', { name: /profile/i }));
    await screen.findByRole('heading', { name: /user profile/i });

    const input = screen.getByLabelText(/display name/i);
    fireEvent.change(input, { target: { value: 'Alexandre Great' } });
    fireEvent.click(screen.getByRole('button', { name: /save profile changes/i }));

    await waitFor(() => {
      expect(apiMocks.updateProfile).toHaveBeenCalledWith('Alexandre Great');
    });
    expect(await screen.findByText(/profile updated successfully/i)).toBeTruthy();
  });

  it('logout from topbar profile menu terminates session and redirects to guest landing screen', async () => {
    apiMocks.currentUser.mockResolvedValue(user);
    renderApp();
    await screen.findByRole('heading', { name: /good morning, alex/i });

    fireEvent.click(screen.getByRole('button', { name: /open profile menu/i }));
    fireEvent.click(screen.getByRole('menuitem', { name: /sign out/i }));

    await waitFor(() => {
      expect(apiMocks.logout).toHaveBeenCalled();
    });
    expect(await screen.findByRole('heading', { name: /welcome back/i })).toBeTruthy();
  });

  it('handles session expiration event and returns to guest screen', async () => {
    apiMocks.currentUser.mockResolvedValue(user);
    renderApp();
    await screen.findByRole('heading', { name: /good morning, alex/i });

    window.dispatchEvent(new CustomEvent('finsight:session-expired'));

    expect(await screen.findByRole('heading', { name: /welcome back/i })).toBeTruthy();
    expect(screen.getByRole('alert').textContent).toContain('Your session has ended');
  });
});
