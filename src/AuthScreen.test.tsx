// @vitest-environment jsdom
import { afterEach, describe, expect, it, vi } from 'vitest';
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import AuthScreen from './AuthScreen';

afterEach(cleanup);

describe('AuthScreen', () => {
  it('displays clearly visible Login and Register options for guest users', () => {
    render(
      <AuthScreen
        onLogin={vi.fn()}
        onRegister={vi.fn()}
        onDemo={vi.fn()}
        loading={false}
        error=""
      />
    );

    expect(screen.getByRole('heading', { name: /welcome back/i })).toBeTruthy();
    expect(screen.getByRole('tab', { name: /sign in/i })).toBeTruthy();
    expect(screen.getByRole('tab', { name: /create account/i })).toBeTruthy();
    expect(screen.getByRole('button', { name: /explore labelled demo data/i })).toBeTruthy();
  });

  it('supports navigation between Login and Register tabs', () => {
    render(
      <AuthScreen
        onLogin={vi.fn()}
        onRegister={vi.fn()}
        onDemo={vi.fn()}
        loading={false}
        error=""
      />
    );

    // Click Create account tab
    fireEvent.click(screen.getByRole('tab', { name: /create account/i }));
    expect(screen.getByRole('heading', { name: /create your account/i })).toBeTruthy();
    expect(screen.getByLabelText(/your name/i)).toBeTruthy();

    // Click Sign in tab
    fireEvent.click(screen.getByRole('tab', { name: /sign in/i }));
    expect(screen.getByRole('heading', { name: /welcome back/i })).toBeTruthy();
    expect(screen.queryByLabelText(/your name/i)).toBeNull();
  });

  it('calls onLogin with email and password', async () => {
    const onLogin = vi.fn().mockResolvedValue(undefined);
    render(
      <AuthScreen
        onLogin={onLogin}
        onRegister={vi.fn()}
        onDemo={vi.fn()}
        loading={false}
        error=""
      />
    );

    fireEvent.change(screen.getByLabelText(/email address/i), { target: { value: 'user@example.com' } });
    fireEvent.change(screen.getByLabelText(/password/i), { target: { value: 'secretpassword123' } });
    fireEvent.click(screen.getByRole('button', { name: /sign in/i }));

    await waitFor(() => {
      expect(onLogin).toHaveBeenCalledWith('user@example.com', 'secretpassword123');
    });
  });

  it('enforces 12-character minimum password requirement during registration', async () => {
    const onRegister = vi.fn().mockResolvedValue(undefined);
    render(
      <AuthScreen
        onLogin={vi.fn()}
        onRegister={onRegister}
        onDemo={vi.fn()}
        loading={false}
        error=""
        initialMode="register"
      />
    );

    fireEvent.change(screen.getByLabelText(/your name/i), { target: { value: 'Alex Smith' } });
    fireEvent.change(screen.getByLabelText(/email address/i), { target: { value: 'alex@example.com' } });
    fireEvent.change(screen.getByLabelText(/password/i), { target: { value: 'short' } });
    fireEvent.click(screen.getByRole('button', { name: /create account/i }));

    // Should not call onRegister because password is < 12 characters
    expect(onRegister).not.toHaveBeenCalled();
    expect(await screen.findByRole('alert')).toBeTruthy();
    expect(screen.getByText(/password must be at least 12 characters/i)).toBeTruthy();
  });

  it('displays backend authentication errors', () => {
    render(
      <AuthScreen
        onLogin={vi.fn()}
        onRegister={vi.fn()}
        onDemo={vi.fn()}
        loading={false}
        error="Invalid credentials"
      />
    );

    expect(screen.getByRole('alert')).toBeTruthy();
    expect(screen.getByText('Invalid credentials')).toBeTruthy();
  });

  it('calls onDemo when clicking explore demo data', () => {
    const onDemo = vi.fn();
    render(
      <AuthScreen
        onLogin={vi.fn()}
        onRegister={vi.fn()}
        onDemo={onDemo}
        loading={false}
        error=""
      />
    );

    fireEvent.click(screen.getByRole('button', { name: /explore labelled demo data/i }));
    expect(onDemo).toHaveBeenCalled();
  });
});

