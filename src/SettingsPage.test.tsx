// @vitest-environment jsdom
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import SettingsPage from './SettingsPage';

const mocks = vi.hoisted(() => ({
  updateProfile: vi.fn(),
  exportCsv: vi.fn(),
}));

vi.mock('./api', () => ({
  authApi: {
    updateProfile: mocks.updateProfile,
  },
  financeApi: {
    exportCsv: mocks.exportCsv,
  },
  apiErrorMessage: () => 'Profile update error',
}));

beforeEach(() => {
  vi.clearAllMocks();
  vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => {});
  if (!globalThis.URL.createObjectURL) {
    globalThis.URL.createObjectURL = vi.fn().mockReturnValue('blob:mock-url');
  } else {
    vi.spyOn(globalThis.URL, 'createObjectURL').mockReturnValue('blob:mock-url');
  }
});

afterEach(() => {
  cleanup();
});

const mockUser = {
  id: 'usr-123',
  displayName: 'Alex Smith',
  email: 'alex@example.com',
};

describe('SettingsPage', () => {
  it('renders settings sections with user details and security overview', () => {
    render(
      <SettingsPage
        user={mockUser}
        onProfileUpdated={vi.fn()}
        onSignOut={vi.fn()}
      />
    );

    expect(screen.getByRole('heading', { name: /settings/i })).toBeTruthy();
    expect(screen.getByDisplayValue('Alex Smith')).toBeTruthy();
    expect(screen.getByDisplayValue('alex@example.com')).toBeTruthy();
    expect(screen.getByText(/aes-256-gcm/i)).toBeTruthy();
    expect(screen.getByText(/bcrypt/i)).toBeTruthy();
    expect(screen.getByText(/inr \(₹\) · indian rupee/i)).toBeTruthy();
  });

  it('updates display name successfully', async () => {
    const onProfileUpdated = vi.fn();
    mocks.updateProfile.mockResolvedValue({
      id: 'usr-123',
      displayName: 'Alexandre Smith',
      email: 'alex@example.com',
    });

    render(
      <SettingsPage
        user={mockUser}
        onProfileUpdated={onProfileUpdated}
        onSignOut={vi.fn()}
      />
    );

    const nameInput = screen.getByLabelText(/display name/i);
    fireEvent.change(nameInput, { target: { value: 'Alexandre Smith' } });

    fireEvent.click(screen.getByRole('button', { name: /save profile/i }));

    await waitFor(() => {
      expect(mocks.updateProfile).toHaveBeenCalledWith('Alexandre Smith');
      expect(onProfileUpdated).toHaveBeenCalledWith({
        id: 'usr-123',
        displayName: 'Alexandre Smith',
        email: 'alex@example.com',
      });
    });

    expect(await screen.findByText(/profile updated successfully/i)).toBeTruthy();
  });

  it('triggers CSV transaction archive export', async () => {
    mocks.exportCsv.mockResolvedValue(
      'ID,Date,Description,Amount,Type,Source\ntx-1,2026-11-01,Grocery,1200,EXPENSE,MANUAL'
    );

    render(
      <SettingsPage
        user={mockUser}
        onProfileUpdated={vi.fn()}
        onSignOut={vi.fn()}
      />
    );

    fireEvent.click(screen.getByRole('button', { name: /export all transactions \(csv\)/i }));

    await waitFor(() => {
      expect(mocks.exportCsv).toHaveBeenCalled();
    });
    expect(await screen.findByText(/transaction export downloaded/i)).toBeTruthy();
  });

  it('calls onSignOut callback when sign out button is clicked', () => {
    const onSignOut = vi.fn();
    render(
      <SettingsPage
        user={mockUser}
        onProfileUpdated={vi.fn()}
        onSignOut={onSignOut}
      />
    );

    fireEvent.click(screen.getByRole('button', { name: /^sign out$/i }));
    expect(onSignOut).toHaveBeenCalled();
  });
});
