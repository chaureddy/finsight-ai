// @vitest-environment jsdom
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { BrowserRouter } from 'react-router-dom';
import AccountsPage from './AccountsPage';
import type { AccountsOverview } from './api';

const mocks = vi.hoisted(() => ({
  accounts: vi.fn(),
  deleteAccount: vi.fn(),
  importCsv: vi.fn(),
  paypalOAuthAuthorize: vi.fn(),
}));

vi.mock('./api', () => ({
  financeApi: {
    accounts: mocks.accounts,
    deleteAccount: mocks.deleteAccount,
    importCsv: mocks.importCsv,
    paypalOAuthAuthorize: mocks.paypalOAuthAuthorize,
  },
  apiErrorMessage: () => 'Request failed',
}));

beforeEach(() => {
  vi.clearAllMocks();
  vi.spyOn(window, 'confirm').mockReturnValue(true);
});

afterEach(() => {
  cleanup();
});

const sampleOverview: AccountsOverview = {
  totalConnected: 2,
  totalTransactionsImported: 56,
  accounts: [
    {
      id: 'conn-1',
      provider: 'CSV_STATEMENT',
      accountName: 'HDFC Statement Nov 2026',
      accountType: 'Bank Statement CSV',
      status: 'CONNECTED',
      balance: null,
      transactionCount: 42,
      lastSync: '2026-11-05',
    },
    {
      id: 'conn-2',
      provider: 'PAYPAL',
      accountName: 'PayPal Sandbox Business',
      accountType: 'PayPal Sandbox Account',
      status: 'CONNECTED',
      balance: null,
      transactionCount: 14,
      lastSync: '2026-11-06',
    },
  ],
};

describe('AccountsPage', () => {
  it('guards demo mode without making live backend requests', () => {
    render(
      <BrowserRouter>
        <AccountsPage enabled={false} />
      </BrowserRouter>
    );
    expect(screen.getByText(/sign in to connect financial sources/i)).toBeTruthy();
    expect(mocks.accounts).not.toHaveBeenCalled();
  });

  it('loads real account overview and displays connected sources', async () => {
    mocks.accounts.mockResolvedValue(sampleOverview);
    render(
      <BrowserRouter>
        <AccountsPage enabled={true} />
      </BrowserRouter>
    );
    expect(await screen.findByText('HDFC Statement Nov 2026')).toBeTruthy();
    expect(screen.getByText('PayPal Sandbox Business')).toBeTruthy();
    expect(screen.getByText('56')).toBeTruthy();
  });

  it('opens CSV import modal and submits CSV content', async () => {
    mocks.accounts.mockResolvedValue(sampleOverview);
    mocks.importCsv.mockResolvedValue({
      importedCount: 5,
      duplicateCount: 2,
      totalRows: 7,
      accountLabel: 'ICICI Credit Card',
      message: 'Successfully imported 5 transactions (2 duplicates skipped).',
    });

    render(
      <BrowserRouter>
        <AccountsPage enabled={true} />
      </BrowserRouter>
    );
    await screen.findByText('HDFC Statement Nov 2026');

    // Click Import CSV statement button
    fireEvent.click(screen.getByRole('button', { name: /import csv statement/i }));
    expect(screen.getByRole('heading', { name: /import statement csv/i })).toBeTruthy();

    // Fill CSV textarea
    const textarea = screen.getByPlaceholderText(/date,description,amount/i);
    fireEvent.change(textarea, {
      target: { value: 'Date,Description,Amount,Type\n2026-11-01,Grocery Store,1500,EXPENSE' },
    });

    // Fill account label
    const labelInput = screen.getByPlaceholderText(/e\.g\. paypal activity/i);
    fireEvent.change(labelInput, { target: { value: 'ICICI Credit Card' } });

    // Click submit
    fireEvent.click(screen.getByRole('button', { name: /import transactions/i }));

    await waitFor(() => {
      expect(mocks.importCsv).toHaveBeenCalledWith(
        'Date,Description,Amount,Type\n2026-11-01,Grocery Store,1500,EXPENSE',
        'ICICI Credit Card'
      );
    });
    expect(await screen.findByRole('status')).toBeTruthy();
    expect(screen.getByText(/import finished:/i)).toBeTruthy();
  });

  it('handles account disconnection', async () => {
    mocks.accounts.mockResolvedValue(sampleOverview);
    mocks.deleteAccount.mockResolvedValue(undefined);

    render(
      <BrowserRouter>
        <AccountsPage enabled={true} />
      </BrowserRouter>
    );
    await screen.findByText('HDFC Statement Nov 2026');

    const removeBtn = screen.getByRole('button', { name: /remove/i });
    fireEvent.click(removeBtn);

    await waitFor(() => {
      expect(mocks.deleteAccount).toHaveBeenCalledWith('conn-1');
    });
  });
});
