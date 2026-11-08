// @vitest-environment jsdom
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { BrowserRouter } from 'react-router-dom';
import ProfilePage from './ProfilePage';

const mocks = vi.hoisted(() => ({
  updateProfile: vi.fn(),
}));

vi.mock('./api', () => ({
  authApi: {
    updateProfile: mocks.updateProfile,
  },
  apiErrorMessage: () => 'Profile update failed',
}));

beforeEach(() => {
  vi.clearAllMocks();
});

afterEach(() => {
  cleanup();
});

const mockUser = {
  id: 'usr-456',
  displayName: 'Alexandre Smith',
  email: 'alex@example.com',
};

describe('ProfilePage', () => {
  it('renders registered email, display name, and security attributes', () => {
    render(
      <BrowserRouter>
        <ProfilePage user={mockUser} onProfileUpdated={vi.fn()} onSignOut={vi.fn()} />
      </BrowserRouter>
    );

    expect(screen.getByRole('heading', { name: /user profile/i })).toBeTruthy();
    expect(screen.getByDisplayValue('Alexandre Smith')).toBeTruthy();
    expect(screen.getByDisplayValue('alex@example.com')).toBeTruthy();
    expect(screen.getByText(/httponly cookie \(jsessionid\)/i)).toBeTruthy();
    expect(screen.getByText(/bcrypt 12 rounds salted hash/i)).toBeTruthy();
    expect(screen.getByText(/aes-256-gcm at rest/i)).toBeTruthy();
  });

  it('updates display name via authApi.updateProfile', async () => {
    const onProfileUpdated = vi.fn();
    mocks.updateProfile.mockResolvedValue({
      id: 'usr-456',
      displayName: 'Alex Smith Jr.',
      email: 'alex@example.com',
    });

    render(
      <BrowserRouter>
        <ProfilePage user={mockUser} onProfileUpdated={onProfileUpdated} onSignOut={vi.fn()} />
      </BrowserRouter>
    );

    const input = screen.getByLabelText(/display name/i);
    fireEvent.change(input, { target: { value: 'Alex Smith Jr.' } });

    fireEvent.click(screen.getByRole('button', { name: /save profile changes/i }));

    await waitFor(() => {
      expect(mocks.updateProfile).toHaveBeenCalledWith('Alex Smith Jr.');
      expect(onProfileUpdated).toHaveBeenCalledWith({
        id: 'usr-456',
        displayName: 'Alex Smith Jr.',
        email: 'alex@example.com',
      });
    });

    expect(await screen.findByText(/profile updated successfully/i)).toBeTruthy();
  });

  it('displays error if update fails', async () => {
    mocks.updateProfile.mockRejectedValue(new Error('Network error'));

    render(
      <BrowserRouter>
        <ProfilePage user={mockUser} onProfileUpdated={vi.fn()} onSignOut={vi.fn()} />
      </BrowserRouter>
    );

    const input = screen.getByLabelText(/display name/i);
    fireEvent.change(input, { target: { value: 'New Name' } });
    fireEvent.click(screen.getByRole('button', { name: /save profile changes/i }));

    expect(await screen.findByRole('alert')).toBeTruthy();
    expect(screen.getByText(/profile update failed/i)).toBeTruthy();
  });

  it('triggers onSignOut callback when sign out button is clicked', () => {
    const onSignOut = vi.fn();
    render(
      <BrowserRouter>
        <ProfilePage user={mockUser} onProfileUpdated={vi.fn()} onSignOut={onSignOut} />
      </BrowserRouter>
    );

    fireEvent.click(screen.getByRole('button', { name: /sign out/i }));
    expect(onSignOut).toHaveBeenCalled();
  });
});

