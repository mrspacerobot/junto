import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { createWebHistory } from 'vue-router';
import { takeMagicLinkToken } from './magicLinkToken';

describe('takeMagicLinkToken', () => {
  const routerHistories: ReturnType<typeof createWebHistory>[] = [];

  beforeEach(() => {
    window.history.replaceState(
      { back: '/auth/login', current: '/auth/verify', router: 'preserved' },
      '',
      '/auth/verify',
    );
  });

  afterEach(() => {
    routerHistories.splice(0).forEach((routerHistory) => routerHistory.destroy());
  });

  it('returns a fragment token and synchronizes router history without the secret', () => {
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
    const routerHistory = createWebHistory();
    routerHistories.push(routerHistory);

    expect(takeMagicLinkToken(routerHistory)).toBe('one-time-secret');

    expect(window.location.pathname + window.location.search + window.location.hash).toBe(
      '/auth/verify?source=email',
    );
    expect(window.history.length).toBe(historyLength);
    expect(routerHistory.location).toBe('/auth/verify?source=email');
    expect(window.history.state).toMatchObject({
      back: '/auth/login',
      current: '/auth/verify?source=email',
      router: 'preserved',
    });

    routerHistory.push('/auth/login');

    expect(window.history.state.back).toBe('/auth/verify?source=email');
    expect(JSON.stringify(window.history.state)).not.toContain('one-time-secret');
  });

  it('does not consume a legacy query token but still removes it from browser history', () => {
    window.history.pushState(
      { current: '/auth/verify?token=legacy-secret&source=email' },
      '',
      '/auth/verify?token=legacy-secret&source=email',
    );
    const routerHistory = createWebHistory();
    routerHistories.push(routerHistory);

    expect(takeMagicLinkToken(routerHistory)).toBe('');
    expect(window.location.pathname + window.location.search).toBe('/auth/verify?source=email');
    expect(window.location.hash).toBe('');
    expect(routerHistory.location).toBe('/auth/verify?source=email');
    expect(JSON.stringify(window.history.state)).not.toContain('legacy-secret');
  });
});
