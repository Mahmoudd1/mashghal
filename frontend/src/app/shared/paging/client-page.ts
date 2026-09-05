import { computed, signal, Signal } from '@angular/core';

import { PageEvent } from '@angular/material/paginator';

/**
 * Client-side paging over a list already held in memory.
 *
 * Endpoints that return a whole collection stay cheap to fetch — the JSON
 * compresses well and one request beats many — but rendering every row does
 * not: a card or expansion panel carries Material components, and a thousand
 * of them costs far more than the bytes did. This caps what reaches the DOM
 * without splitting the request.
 *
 * Use the server's own paging (as cuts and intakes do) when the list is large
 * enough that fetching it whole is the problem too.
 */
export function clientPage<T>(source: Signal<readonly T[]>, initialSize = 25) {
  const pageIndex = signal(0);
  const pageSize = signal(initialSize);

  const length = computed(() => source().length);

  // A shrinking list (a filter narrowing) can strand the reader past the end.
  const safeIndex = computed(() => {
    const lastPage = Math.max(0, Math.ceil(length() / pageSize()) - 1);
    return Math.min(pageIndex(), lastPage);
  });

  const items = computed(() => {
    const start = safeIndex() * pageSize();
    return source().slice(start, start + pageSize());
  });

  return {
    items,
    length,
    pageIndex: safeIndex,
    pageSize: pageSize.asReadonly(),
    onPage(event: PageEvent): void {
      pageIndex.set(event.pageIndex);
      pageSize.set(event.pageSize);
    },
  };
}
