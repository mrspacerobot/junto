import { describe, expect, it } from 'vitest';
import { usePagination } from './usePagination';

function deferred<T>() {
  let resolve!: (value: T) => void;
  const promise = new Promise<T>((res) => {
    resolve = res;
  });
  return { promise, resolve };
}

describe('usePagination', () => {
  it('appends subsequent cursor pages with a new reactive array', async () => {
    const pages = [{ data: ['first'], nextCursor: 'next' }, { data: ['second'] }];
    const pagination = usePagination(async () => pages.shift()!);

    await pagination.load(true);
    const firstReference = pagination.items.value;
    await pagination.load();

    expect(pagination.items.value).toEqual(['first', 'second']);
    expect(pagination.items.value).not.toBe(firstReference);
  });

  it('discards an older response after a filter reset', async () => {
    const oldPage = deferred<{ data: string[]; nextCursor?: string }>();
    const resetPage = deferred<{ data: string[]; nextCursor?: string }>();
    let calls = 0;
    const pagination = usePagination(() => (++calls === 1 ? oldPage.promise : resetPage.promise));

    const oldLoad = pagination.load(true);
    const resetLoad = pagination.load(true);
    resetPage.resolve({ data: ['new-filter'] });
    await resetLoad;
    oldPage.resolve({ data: ['stale-filter'] });
    await oldLoad;

    expect(pagination.items.value).toEqual(['new-filter']);
  });
});
