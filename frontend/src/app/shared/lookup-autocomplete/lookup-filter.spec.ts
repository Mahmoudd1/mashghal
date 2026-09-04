import { describe, expect, it } from 'vitest';

import { filterByName, findExact, normalise } from './lookup-filter';

const options = [{ name: 'قطن' }, { name: 'قُطن مصري' }, { name: 'Cotton' }];
const nameOf = (o: { name: string }) => o.name;

describe('lookup filtering', () => {
  it('folds hamza, taa marbuta and harakat so near-identical names match', () => {
    expect(normalise('أقمشة')).toBe(normalise('اقمشه'));
    expect(normalise('قُطن')).toBe(normalise('قطن'));
  });

  it('ignores case and surrounding whitespace', () => {
    expect(normalise('  Cotton ')).toBe(normalise('cotton'));
  });

  it('offers every option for an empty query', () => {
    expect(filterByName(options, '', nameOf)).toHaveLength(3);
  });

  it('matches on a substring', () => {
    expect(filterByName(options, 'قطن', nameOf).map(nameOf)).toEqual(['قطن', 'قُطن مصري']);
  });

  it('finds an existing record despite a spelling variant, so no duplicate is created', () => {
    expect(findExact(options, ' قُطن ', nameOf)).toEqual({ name: 'قطن' });
  });

  it('returns nothing for a genuinely new name', () => {
    expect(findExact(options, 'كتان', nameOf)).toBeUndefined();
  });
});
