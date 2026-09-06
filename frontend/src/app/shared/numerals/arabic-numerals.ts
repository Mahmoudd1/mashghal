/**
 * Accepts Arabic-Indic digits wherever Western digits are expected.
 *
 * An Arabic keyboard produces ٠١٢٣٤٥٦٧٨٩ (U+0660–U+0669), and a Persian or Urdu
 * layout produces ۰۱۲۳۴۵۶۷۸۹ (U+06F0–U+06F9). A browser's `type="number"` field
 * rejects both outright — the keystroke simply does not register — so someone
 * entering a weight on an Arabic keyboard finds the field silently refusing to
 * accept anything. Every number in this app is therefore a text field whose
 * digits are folded to Western form as they are typed.
 *
 * Folding, rather than storing both forms: ٥٠٠ and 500 are the same model
 * number, and a system that treats them as two would quietly split a model's
 * history in half.
 */

const ARABIC_INDIC_ZERO = 0x0660;
const EXTENDED_ARABIC_INDIC_ZERO = 0x06f0;

/** Arabic decimal separator (U+066B) — the comma-looking one. */
const ARABIC_DECIMAL_SEPARATOR = '٫';

/** Arabic thousands separator (U+066C). */
const ARABIC_THOUSANDS_SEPARATOR = '٬';

/**
 * Rewrites Arabic-Indic digits as Western ones, character for character.
 *
 * The mapping is length-preserving so a caret position stays valid across it.
 */
export function toWesternDigits(text: string): string {
  let result = '';
  for (const character of text) {
    const code = character.codePointAt(0)!;
    if (code >= ARABIC_INDIC_ZERO && code <= ARABIC_INDIC_ZERO + 9) {
      result += String(code - ARABIC_INDIC_ZERO);
    } else if (code >= EXTENDED_ARABIC_INDIC_ZERO && code <= EXTENDED_ARABIC_INDIC_ZERO + 9) {
      result += String(code - EXTENDED_ARABIC_INDIC_ZERO);
    } else if (character === ARABIC_DECIMAL_SEPARATOR) {
      result += '.';
    } else if (character === ARABIC_THOUSANDS_SEPARATOR) {
      result += ',';
    } else {
      result += character;
    }
  }
  return result;
}

/**
 * Folds digits and drops what a number cannot contain, keeping the caret with
 * the character it was sitting after.
 *
 * A text field will accept letters where `type="number"` would not, so the
 * disallowed characters are removed as they arrive rather than left to fail
 * validation later. A single leading minus and a single decimal point survive.
 */
export function normaliseNumericInput(
  text: string,
  caret: number,
): { text: string; caret: number } {
  const folded = toWesternDigits(text);

  let result = '';
  let newCaret = caret;
  let seenDot = false;

  for (let index = 0; index < folded.length; index++) {
    const character = folded[index];
    const isDigit = character >= '0' && character <= '9';
    const isFirstDot = character === '.' && !seenDot;
    const isLeadingMinus = character === '-' && result.length === 0;

    if (isDigit || isFirstDot || isLeadingMinus) {
      result += character;
      if (isFirstDot) {
        seenDot = true;
      }
    } else if (index < caret) {
      // Dropped from before the caret, so the caret shifts left with it.
      newCaret--;
    }
  }

  return { text: result, caret: Math.max(0, newCaret) };
}

/**
 * The number a field holds, or null when it holds nothing usable.
 *
 * Empty is null rather than zero: a blank weight means "not entered", and
 * silently reading it as 0 would let an empty field pass a min(0) check.
 */
export function parseNumeric(text: string): number | null {
  const cleaned = toWesternDigits(text).replace(/[,\s]/g, '');
  if (cleaned === '' || cleaned === '-' || cleaned === '.' || cleaned === '-.') {
    return null;
  }
  const value = Number(cleaned);
  return Number.isFinite(value) ? value : null;
}
