// @vitest-environment jsdom
import { afterEach, describe, expect, it, vi } from 'vitest';
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import PayPalPage from './PayPalPage';

const mocks=vi.hoisted(()=>({status:vi.fn(),reviews:vi.fn(),import:vi.fn()}));
vi.mock('./api',()=>({
  financeApi:{paypalStatus:mocks.status,paypalReviews:mocks.reviews,importPayPal:mocks.import},
  apiErrorMessage:()=> 'Sandbox reporting is unavailable.',
}));
afterEach(()=>{cleanup();vi.clearAllMocks();});

describe('PayPal Sandbox page',()=>{
  it('keeps PayPal data out of Demo Mode',()=>{
    render(<PayPalPage enabled={false}/>);
    expect(screen.getByText(/demo mode does not connect/i)).toBeTruthy();
    expect(mocks.status).not.toHaveBeenCalled();
  });

  it('shows server connection state and reports import counts',async()=>{
    mocks.status.mockResolvedValue({configured:true,connected:true,state:'CONNECTED',connectedAt:null,lastVerifiedAt:null});
    mocks.reviews.mockResolvedValue([]);
    mocks.import.mockResolvedValue({importedCount:2,duplicateCount:1,reviewCount:3,environment:'SANDBOX',state:'CONNECTED'});
    render(<PayPalPage enabled/>);
    expect(await screen.findByText(/connected to sandbox reporting/i)).toBeTruthy();
    fireEvent.click(screen.getByRole('button',{name:/verify & import/i}));
    await waitFor(()=>expect(mocks.import).toHaveBeenCalledTimes(1));
    await waitFor(()=>expect(screen.getByRole('status').textContent).toContain('2 imported, 1 duplicates skipped, 3 records queued for review.'));
  });
});
