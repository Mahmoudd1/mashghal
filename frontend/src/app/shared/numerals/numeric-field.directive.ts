import { Directive, ElementRef, HostListener, OnInit, forwardRef, inject } from '@angular/core';
import { ControlValueAccessor, NG_VALUE_ACCESSOR } from '@angular/forms';

import { normaliseNumericInput, parseNumeric } from './arabic-numerals';

/**
 * Lets a number field take Arabic-Indic digits, which `type="number"` refuses.
 *
 * A browser validates a `type="number"` field against its own grammar, and that
 * grammar knows only 0-9. Pressing ٥ on an Arabic keyboard therefore does
 * nothing at all — no character appears and no event fires — so the field looks
 * broken rather than strict. The only way round it is to stop the browser
 * parsing the field: the element is switched to `type="text"` here, and the
 * digits are folded to Western form as they are typed.
 *
 * <p>The switch happens in `ngOnInit` because `MatInput` assigns `type` from its
 * own input setter, which runs first and would otherwise put `number` back.
 *
 * <p>Crucially the control's value stays a `number | null`, exactly what
 * `NumberValueAccessor` used to write. Every `Validators.min`, every arithmetic
 * `computed()`, and every request payload downstream keeps seeing a number, so
 * nothing outside this file has to know the field is now text.
 *
 * <p>Angular picks a custom value accessor over a built-in one, so declaring
 * this on `input[type=number]` displaces `NumberValueAccessor` without a
 * conflict, and without a single change to the markup.
 */
@Directive({
  selector: 'input[type=number]',
  providers: [
    {
      provide: NG_VALUE_ACCESSOR,
      useExisting: forwardRef(() => NumericFieldDirective),
      multi: true,
    },
  ],
})
export class NumericFieldDirective implements ControlValueAccessor, OnInit {
  private readonly element = inject<ElementRef<HTMLInputElement>>(ElementRef).nativeElement;

  private onChange: (value: number | null) => void = () => {};
  private onTouched: () => void = () => {};

  ngOnInit(): void {
    // Read while the element still describes itself as a number field.
    const step = this.element.getAttribute('step');

    this.element.type = 'text';

    // A phone still offers a digit pad; `decimal` is the one with a separator
    // key, so fields that step by whole numbers ask for the plainer keypad.
    this.element.inputMode = step === null || step === '1' ? 'numeric' : 'decimal';
  }

  @HostListener('input')
  protected handleInput(): void {
    const caret = this.element.selectionStart ?? this.element.value.length;
    const normalised = normaliseNumericInput(this.element.value, caret);

    // Rewriting `value` moves the caret to the end, so it is only touched when
    // something actually changed, and put back where the typing left it.
    if (normalised.text !== this.element.value) {
      this.element.value = normalised.text;
      this.element.setSelectionRange(normalised.caret, normalised.caret);
    }

    this.onChange(parseNumeric(normalised.text));
  }

  @HostListener('blur')
  protected handleBlur(): void {
    this.onTouched();
  }

  writeValue(value: number | null): void {
    // Displayed in Western digits whatever was typed, matching how every
    // already-saved number is rendered elsewhere in the app.
    this.element.value = value === null || value === undefined ? '' : String(value);
  }

  registerOnChange(fn: (value: number | null) => void): void {
    this.onChange = fn;
  }

  registerOnTouched(fn: () => void): void {
    this.onTouched = fn;
  }

  setDisabledState(isDisabled: boolean): void {
    this.element.disabled = isDisabled;
  }
}
