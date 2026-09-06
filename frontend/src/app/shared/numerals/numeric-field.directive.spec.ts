import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { FormControl, ReactiveFormsModule, Validators } from '@angular/forms';
import { beforeEach, describe, expect, it } from 'vitest';

import { NumericFieldDirective } from './numeric-field.directive';

@Component({
  imports: [ReactiveFormsModule, NumericFieldDirective],
  template: `<input type="number" min="1" step="0.001" [formControl]="weight" />`,
})
class HostComponent {
  readonly weight = new FormControl<number | null>(null, Validators.min(1));
}

describe('NumericFieldDirective', () => {
  let fixture: ReturnType<typeof TestBed.createComponent<HostComponent>>;
  let input: HTMLInputElement;
  let control: FormControl<number | null>;

  beforeEach(() => {
    fixture = TestBed.createComponent(HostComponent);
    fixture.detectChanges();
    input = fixture.nativeElement.querySelector('input');
    control = fixture.componentInstance.weight;
  });

  /** What the browser does when someone types into the field. */
  function type(text: string, caret = text.length): void {
    input.value = text;
    input.setSelectionRange(caret, caret);
    input.dispatchEvent(new Event('input'));
  }

  it('renders as a text field, since a number field would refuse the keystrokes', () => {
    expect(input.type).toBe('text');
  });

  it('asks for the separator keypad when the field steps by fractions', () => {
    expect(input.inputMode).toBe('decimal');
  });

  it('writes a number to the control, not the typed string', () => {
    type('12.5');
    expect(control.value).toBe(12.5);
  });

  it('reads Arabic-Indic digits as the same number as Western ones', () => {
    type('١٢٫٥');
    expect(control.value).toBe(12.5);
  });

  it('shows the Arabic digits folded back into the field as they are typed', () => {
    type('١٢٣');
    expect(input.value).toBe('123');
  });

  it('leaves an empty field as null rather than zero', () => {
    type('12');
    type('');
    expect(control.value).toBeNull();
  });

  it('keeps min validation working, because the value is still a number', () => {
    type('٠');
    expect(control.hasError('min')).toBe(true);

    type('٥');
    expect(control.valid).toBe(true);
  });

  it('displays a programmatically set number in Western digits', () => {
    control.setValue(7.25);
    expect(input.value).toBe('7.25');

    control.setValue(null);
    expect(input.value).toBe('');
  });

  it('leaves the caret where the typing was, not at the end', () => {
    // "١٢٣" typed, then the caret moved back and "٥" inserted in the middle.
    type('١٢٥٣', 3);
    expect(input.value).toBe('1253');
    expect(input.selectionStart).toBe(3);
  });

  it('drops a letter that a number field would never have accepted', () => {
    type('12a');
    expect(input.value).toBe('12');
    expect(control.value).toBe(12);
  });

  it('disables the element when the control is disabled', () => {
    control.disable();
    expect(input.disabled).toBe(true);
  });

  it('marks the control touched on blur', () => {
    input.dispatchEvent(new Event('blur'));
    expect(control.touched).toBe(true);
  });
});
