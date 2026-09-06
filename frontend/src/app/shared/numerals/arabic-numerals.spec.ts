import { describe, expect, it } from 'vitest';

import { normaliseNumericInput, parseNumeric, toWesternDigits } from './arabic-numerals';

describe('toWesternDigits', () => {
  it('folds Arabic-Indic digits', () => {
    expect(toWesternDigits('٠١٢٣٤٥٦٧٨٩')).toBe('0123456789');
  });

  it('folds the Persian and Urdu digit range', () => {
    expect(toWesternDigits('۰۱۲۳۴۵۶۷۸۹')).toBe('0123456789');
  });

  it('folds the Arabic decimal separator', () => {
    expect(toWesternDigits('١٢٫٥')).toBe('12.5');
  });

  it('leaves Western digits untouched', () => {
    expect(toWesternDigits('12.5')).toBe('12.5');
  });

  it('preserves length, so a caret stays valid across it', () => {
    const arabic = '١٢٣٤٫٥';
    expect(toWesternDigits(arabic)).toHaveLength(arabic.length);
  });

  it('leaves surrounding text alone', () => {
    expect(toWesternDigits('قصة ٥٠٠')).toBe('قصة 500');
  });
});

describe('normaliseNumericInput', () => {
  it('folds digits and keeps the caret where it was', () => {
    expect(normaliseNumericInput('١٢٣', 3)).toEqual({ text: '123', caret: 3 });
  });

  it('drops letters, pulling the caret back with them', () => {
    // "12a3" with the caret after "a" becomes "123" with the caret after "2".
    expect(normaliseNumericInput('12a3', 3)).toEqual({ text: '123', caret: 2 });
  });

  it('keeps one decimal point and drops any others', () => {
    expect(normaliseNumericInput('1.2.3', 5).text).toBe('1.23');
  });

  it('keeps a leading minus but not one in the middle', () => {
    expect(normaliseNumericInput('-12', 3).text).toBe('-12');
    expect(normaliseNumericInput('1-2', 3).text).toBe('12');
  });

  it('never returns a negative caret', () => {
    expect(normaliseNumericInput('abc', 3).caret).toBe(0);
  });
});

describe('parseNumeric', () => {
  it('reads Arabic-Indic digits as the same number as Western ones', () => {
    expect(parseNumeric('١٢٣٫٥')).toBe(123.5);
    expect(parseNumeric('123.5')).toBe(123.5);
  });

  it('treats empty as absent rather than zero', () => {
    // A blank weight must not slip past a min(0) check as if it were 0.
    expect(parseNumeric('')).toBeNull();
    expect(parseNumeric('   ')).toBeNull();
  });

  it('treats a partial number as absent', () => {
    expect(parseNumeric('-')).toBeNull();
    expect(parseNumeric('.')).toBeNull();
  });

  it('ignores thousands separators in either script', () => {
    expect(parseNumeric('1,234')).toBe(1234);
    expect(parseNumeric('١٬٢٣٤')).toBe(1234);
  });

  it('returns null for something that is not a number', () => {
    expect(parseNumeric('abc')).toBeNull();
  });
});
