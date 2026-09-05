import { signal } from '@angular/core';
import { describe, expect, it } from 'vitest';

import { clientPage } from './client-page';

const rows = (n: number) => Array.from({ length: n }, (_, i) => i);

describe('clientPage', () => {
  it('returns only the current page', () => {
    const page = clientPage(signal(rows(1000)), 25);

    expect(page.length()).toBe(1000);
    expect(page.items().length).toBe(25);
    expect(page.items()[0]).toBe(0);
  });

  it('moves to the requested page', () => {
    const page = clientPage(signal(rows(1000)), 25);
    page.onPage({ pageIndex: 3, pageSize: 25, length: 1000 });

    expect(page.items()[0]).toBe(75);
    expect(page.items().length).toBe(25);
  });

  it('yields a short final page rather than padding', () => {
    const page = clientPage(signal(rows(30)), 25);
    page.onPage({ pageIndex: 1, pageSize: 25, length: 30 });

    expect(page.items()).toEqual([25, 26, 27, 28, 29]);
  });

  it('clamps back into range when a filter shrinks the list', () => {
    const source = signal(rows(1000));
    const page = clientPage(source, 25);
    page.onPage({ pageIndex: 30, pageSize: 25, length: 1000 });
    expect(page.items()[0]).toBe(750);

    // A search narrows the list to two rows while the reader sits on page 31.
    source.set(rows(2));

    expect(page.pageIndex()).toBe(0);
    expect(page.items()).toEqual([0, 1]);
  });

  it('handles an empty list', () => {
    const page = clientPage(signal<number[]>([]), 25);

    expect(page.length()).toBe(0);
    expect(page.pageIndex()).toBe(0);
    expect(page.items()).toEqual([]);
  });
});
