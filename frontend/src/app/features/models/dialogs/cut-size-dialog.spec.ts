import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { provideZonelessChangeDetection } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { provideTranslateService } from '@ngx-translate/core';
import { beforeEach, describe, expect, it } from 'vitest';

import { Cut } from '../../../core/models/api.models';
import { CutSizeDialog } from './cut-size-dialog';

const CUT = {
  id: 1,
  cutNumber: 'C-1',
  totalLayers: 100,
  primaryModelNumber: '101',
  branchNameAr: 'العجمي',
} as Cut;

/**
 * The marker dialog is the only way a second model joins a cut, so it has to
 * open. It shipped bound to a `modelId` control the form never had, which threw
 * NG01203 on open and left the two-model cut unreachable from the UI.
 */
describe('CutSizeDialog', () => {
  let fixture: ReturnType<typeof TestBed.createComponent<CutSizeDialog>>;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [CutSizeDialog],
      providers: [
        provideZonelessChangeDetection(),
        provideTranslateService(),
        provideHttpClient(),
        // The model list is fetched by a resource; intercepted so the dialog is
        // exercised with an empty list rather than a failed request.
        provideHttpClientTesting(),
        { provide: MatDialogRef, useValue: { close: () => {} } },
        { provide: MAT_DIALOG_DATA, useValue: { cut: CUT } },
      ],
    });
    fixture = TestBed.createComponent(CutSizeDialog);
    fixture.detectChanges();
  });

  it('opens, with every control in the template bound to the form', () => {
    const bound = Array.from(
      fixture.nativeElement.querySelectorAll('[formControlName]') as NodeListOf<Element>,
    ).map((el) => el.getAttribute('formControlName'));

    expect(bound).toContain('modelNumber');
    expect(bound).toContain('piecesPerLayer');
  });

  it('prefills the cut’s own model, so the common case is one field', () => {
    const input = fixture.nativeElement.querySelector(
      'input[formControlName="modelNumber"]',
    ) as HTMLInputElement;

    expect(input.value).toBe('101');
  });

  it('takes a model number that does not exist yet', () => {
    const input = fixture.nativeElement.querySelector(
      'input[formControlName="modelNumber"]',
    ) as HTMLInputElement;
    input.value = '201';
    input.dispatchEvent(new Event('input'));
    fixture.detectChanges();

    // No model list is loaded here, so an unknown number is what it looks like:
    // the dialog offers to create it rather than refusing the entry.
    expect(fixture.nativeElement.textContent).toContain('cut.modelCreatedHint');
  });
});
