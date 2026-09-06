import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { beforeEach, describe, expect, it } from 'vitest';

import { ArabicDigitsDirective } from './arabic-digits.directive';

@Component({
  imports: [ReactiveFormsModule, ArabicDigitsDirective],
  template: `<input appArabicDigits [formControl]="modelNumber" />`,
})
class HostComponent {
  readonly modelNumber = new FormControl('', { nonNullable: true });
}

describe('ArabicDigitsDirective', () => {
  let fixture: ReturnType<typeof TestBed.createComponent<HostComponent>>;
  let input: HTMLInputElement;
  let control: FormControl<string>;

  beforeEach(() => {
    fixture = TestBed.createComponent(HostComponent);
    fixture.detectChanges();
    input = fixture.nativeElement.querySelector('input');
    control = fixture.componentInstance.modelNumber;
  });

  function type(text: string, caret = text.length): void {
    input.value = text;
    input.setSelectionRange(caret, caret);
    input.dispatchEvent(new Event('input'));
  }

  it('stores a model number typed in Arabic digits as the Western one', () => {
    type('٥٠٠');
    expect(control.value).toBe('500');
    expect(input.value).toBe('500');
  });

  it('keeps the value a string, dashes and letters intact', () => {
    type('٥٠٠-A');
    expect(control.value).toBe('500-A');
  });

  it('leaves a Western number untouched', () => {
    type('500');
    expect(control.value).toBe('500');
  });

  it('leaves the caret mid-string, so editing does not jump to the end', () => {
    type('٥٠٠', 1);
    expect(input.selectionStart).toBe(1);
  });

  it('writes a value set programmatically', () => {
    control.setValue('123');
    expect(input.value).toBe('123');
  });

  it('marks the control touched on blur', () => {
    input.dispatchEvent(new Event('blur'));
    expect(control.touched).toBe(true);
  });
});
