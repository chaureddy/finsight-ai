// @vitest-environment jsdom
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import AiAssistantPage from './AiAssistantPage';

const mocks = vi.hoisted(() => ({
  aiChat: vi.fn(),
  aiInsights: vi.fn(),
}));

vi.mock('./api', () => ({
  financeApi: {
    aiChat: mocks.aiChat,
    aiInsights: mocks.aiInsights,
  },
  apiErrorMessage: () => 'Chat service error',
}));

beforeEach(() => {
  vi.clearAllMocks();
  mocks.aiInsights.mockResolvedValue([
    {
      id: 'ins-1',
      title: 'Healthy Savings Rate',
      message: 'You saved 35% of income this month.',
      tone: 'positive',
    },
  ]);
});

afterEach(() => {
  cleanup();
});

describe('AiAssistantPage', () => {
  it('renders welcome greeting and proactive insights when enabled', async () => {
    render(<AiAssistantPage enabled={true} displayName="Alex Smith" />);

    expect(screen.getByText(/hello alex smith/i)).toBeTruthy();
    expect(screen.getByText(/100% data grounded/i)).toBeTruthy();

    expect(await screen.findByText('Healthy Savings Rate:')).toBeTruthy();
    expect(screen.getByText('You saved 35% of income this month.')).toBeTruthy();
  });

  it('submits a user prompt and displays the grounded response', async () => {
    mocks.aiChat.mockResolvedValue({
      reply: 'Your current net worth is **₹4,50,000** with **₹5,00,000** in assets.',
      suggestedQuestions: ['What are my liabilities?', 'Where can I save more?'],
      groundingData: { netWorth: 450000, assets: 500000 },
    });

    render(<AiAssistantPage enabled={true} displayName="Alex Smith" />);

    const input = screen.getByPlaceholderText(/ask about your spending/i);
    fireEvent.change(input, { target: { value: 'What is my current net worth?' } });

    fireEvent.click(screen.getByRole('button', { name: /send message/i }));

    expect(await screen.findByText(/your current net worth is/i)).toBeTruthy();
    expect(screen.getByText('What are my liabilities?')).toBeTruthy();
    expect(mocks.aiChat).toHaveBeenCalledWith('What is my current net worth?');
  });

  it('sends question when a suggestion chip is clicked', async () => {
    mocks.aiChat.mockResolvedValue({
      reply: 'You spent ₹12,000 on Food and Dining this month.',
      suggestedQuestions: ['Set a budget for Food'],
    });

    render(<AiAssistantPage enabled={true} displayName="Alex Smith" />);

    // Click one of the default suggestions
    const suggestionBtn = screen.getByRole('button', { name: 'Where is my money going this month?' });
    fireEvent.click(suggestionBtn);

    await waitFor(() => {
      expect(mocks.aiChat).toHaveBeenCalledWith('Where is my money going this month?');
    });
    expect(await screen.findByText(/you spent ₹12,000 on food/i)).toBeTruthy();
  });

  it('displays friendly error if chat endpoint fails', async () => {
    mocks.aiChat.mockRejectedValue(new Error('Network disconnected'));

    render(<AiAssistantPage enabled={true} displayName="Alex Smith" />);

    const input = screen.getByPlaceholderText(/ask about your spending/i);
    fireEvent.change(input, { target: { value: 'Any forecast?' } });
    fireEvent.click(screen.getByRole('button', { name: /send message/i }));

    expect(await screen.findByText(/unable to process query/i)).toBeTruthy();
  });
});
