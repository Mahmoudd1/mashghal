import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { beforeEach, describe, expect, it } from 'vitest';

import { NumericFieldDirective } from './numeric-field.directive';

/**
 * The same shape the real dialogs use — `matInput` sets the element's `type`
 * from its own setter, and the static `min` pulls in Angular's `MinValidator`,
 * whose selector is `input[type=number][min][formControlName]`. Both have to
 * survive the switch to a text field.
 */
@Component({
  imports: [ReactiveFormsModule, MatFormFieldModule, MatInputModule, NumericFieldDirective],
  template: `
    <form [formGroup]="form">
      <mat-form-field appearance="outline">
        <mat-label>layers</mat-label>
        <input matInput type="number" min="1" step="1" formControlName="layers" dir="ltr" />
      </mat-form-field>
    </form>
  `,
})
class MaterialHostComponent {
  readonly form = new FormBuilder().nonNullable.group({
    layers: [null as number | null, [Validators.required, Validators.min(1)]],
  });
}

describe('NumericFieldDirective inside a Material field', () => {
  let fixture: ReturnType<typeof TestBed.createComponent<MaterialHostComponent>>;
  let input: HTMLInputElement;
  let component: MaterialHostComponent;

  beforeEach(() => {
    fixture = TestBed.createComponent(MaterialHostComponent);
    fixture.detectChanges();
    input = fixture.nativeElement.querySelector('input');
    component = fixture.componentInstance;
  });

  function type(text: string): void {
    input.value = text;
    input.setSelectionRange(text.length, text.length);
    input.dispatchEvent(new Event('input'));
  }

  it('wins the type back from matInput, which sets it first', () => {
    expect(input.type).toBe('text');
  });

  it('asks for the plain digit keypad when the field steps by whole numbers', () => {
    expect(input.inputMode).toBe('numeric');
  });

  it('takes Arabic digits and gives the control a number', () => {
    type('٤');
    expect(component.form.controls.layers.value).toBe(4);
    expect(component.form.valid).toBe(true);
  });

  it('still enforces the min attribute validator that the markup declares', () => {
    type('٠');
    expect(component.form.controls.layers.hasError('min')).toBe(true);
  });

  it('lets Material see the field fill and empty, so the label floats', () => {
    const field = fixture.debugElement.nativeElement.querySelector('mat-form-field');
    type('٤');
    fixture.detectChanges();
    expect(field.classList.contains('mat-form-field-empty')).toBe(false);

    component.form.controls.layers.setValue(null);
    fixture.detectChanges();
    expect(input.value).toBe('');
  });
});
