import { Directive, ElementRef, HostListener, forwardRef, inject } from '@angular/core';
import { ControlValueAccessor, NG_VALUE_ACCESSOR } from '@angular/forms';

import { toWesternDigits } from './arabic-numerals';

/**
 * Folds Arabic-Indic digits in a text field that holds an identifier.
 *
 * A model number is text, not a quantity — it can carry a dash or a letter — so
 * it cannot be a number field. It is still a number to the people typing it,
 * and an Arabic keyboard will happily put ٥٠٠ in it. Stored as typed, that
 * model would sit beside the one someone else entered as 500: two records, one
 * model, and a history split down the middle that nobody would think to look
 * for.
 *
 * <p>So the digits are folded on the way in while everything else — letters,
 * dashes, the leading + of a phone number — is left exactly as typed. Folding
 * is length-preserving, so the caret needs no correction and the user can go on
 * typing mid-string without the cursor jumping.
 *
 * <p>Opt in per field with the attribute; it is deliberately not automatic,
 * because a name field is free text where a stray digit is the user's own.
 */
@Directive({
  selector: 'input[appArabicDigits]',
  providers: [
    {
      provide: NG_VALUE_ACCESSOR,
      useExisting: forwardRef(() => ArabicDigitsDirective),
      multi: true,
    },
  ],
})
export class ArabicDigitsDirective implements ControlValueAccessor {
  private readonly element = inject<ElementRef<HTMLInputElement>>(ElementRef).nativeElement;

  private onChange: (value: string) => void = () => {};
  private onTouched: () => void = () => {};

  @HostListener('input')
  protected handleInput(): void {
    const folded = toWesternDigits(this.element.value);

    if (folded !== this.element.value) {
      const caret = this.element.selectionStart;
      this.element.value = folded;
      if (caret !== null) {
        this.element.setSelectionRange(caret, caret);
      }
    }

    this.onChange(folded);
  }

  @HostListener('blur')
  protected handleBlur(): void {
    this.onTouched();
  }

  writeValue(value: string | null): void {
    this.element.value = value ?? '';
  }

  registerOnChange(fn: (value: string) => void): void {
    this.onChange = fn;
  }

  registerOnTouched(fn: () => void): void {
    this.onTouched = fn;
  }

  setDisabledState(isDisabled: boolean): void {
    this.element.disabled = isDisabled;
  }
}
