/**
 * Helpers for the "type freely, pick from what already exists" pattern.
 *
 * The user is not choosing from a closed list: they type a name, matching
 * existing records are offered, and anything genuinely new is created on save.
 * Matching is deliberately loose — trimmed, case-folded, and Arabic-normalised —
 * so a stray alef hamza or a trailing space does not create a duplicate record
 * that looks identical on screen.
 */

/** Folds the spellings that differ only by diacritic or hamza form. */
export function normalise(value: string): string {
  return value
    .trim()
    .toLocaleLowerCase()
    .replace(/[ً-ْ]/g, '') // harakat
    .replace(/[أإآٱ]/g, 'ا')
    .replace(/ى/g, 'ي')
    .replace(/ة/g, 'ه')
    .replace(/\s+/g, ' ');
}

/** Options whose name contains what has been typed so far. */
export function filterByName<T>(
  options: readonly T[],
  query: string,
  nameOf: (option: T) => string,
): T[] {
  const needle = normalise(query ?? '');
  if (needle === '') {
    return [...options];
  }
  return options.filter((option) => normalise(nameOf(option)).includes(needle));
}

/** The option whose name is the same as what was typed, ignoring the above noise. */
export function findExact<T>(
  options: readonly T[],
  query: string,
  nameOf: (option: T) => string,
): T | undefined {
  const needle = normalise(query ?? '');
  return needle === '' ? undefined : options.find((option) => normalise(nameOf(option)) === needle);
}
