import { beforeEach, describe, expect, it, vi } from 'vitest';
import { takeMagicLinkToken } from './magicLinkToken';

describe('takeMagicLinkToken', () => {
  beforeEach(() => {
    window.history.replaceState(
      { back: '/auth/login', current: '/auth/verify', router: 'preserved' },
      '',
      '/auth/verify',
    );
  });

  it('returns a fragment token and replaces the current history entry without the secret', () => {
    window.history.pushState(
      {
        back: '/auth/login',
        current: '/auth/verify?source=email#token=one-time-secret',
        router: 'preserved',
      },
      '',
      '/auth/verify?source=email#token=one-time-secret',
    );
    const historyLength = window.history.length;
    const replaceState = vi.spyOn(window.history, 'replaceState');

    expect(takeMagicLinkToken()).toBe('one-time-secret');

    expect(window.location.pathname + window.location.search + window.location.hash).toBe(
      '/auth/verify?source=email',
    );
    expect(window.history.length).toBe(historyLength);
    const sanitizedState = {
      back: '/auth/login',
      current: '/auth/verify?source=email',
      router: 'preserved',
    };
    expect(window.history.state).toEqual(sanitizedState);
    expect(replaceState).toHaveBeenCalledWith(sanitizedState, '', '/auth/verify?source=email');
  });

  it('does not consume a legacy query token but still removes it from browser history', () => {
    window.history.pushState(
      { current: '/auth/verify?token=legacy-secret&source=email' },
      '',
      '/auth/verify?token=legacy-secret&source=email',
    );

    expect(takeMagicLinkToken()).toBe('');
    expect(window.location.pathname + window.location.search).toBe('/auth/verify?source=email');
    expect(window.location.hash).toBe('');
    expect(JSON.stringify(window.history.state)).not.toContain('legacy-secret');
  });
});
