import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideZonelessChangeDetection } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { FormArray, FormGroup } from '@angular/forms';
import { provideNativeDateAdapter } from '@angular/material/core';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { provideTranslateService } from '@ngx-translate/core';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import { CutDialog } from './cut-dialog';

/**
 * The ticket: one cutting run producing model 101 in 6/8/10 and model 201 in
 * 12/14/16, where size 10 is cut twice a layer. Four a layer plus three is the
 * seven the cut is counted in.
 */
const TICKET = [
  { modelNumber: '101', sizes: [[3, 1] as const, [2, 1] as const, [1, 2] as const] },
  { modelNumber: '201', sizes: [[6, 1] as const, [5, 1] as const, [4, 1] as const] },
];

/**
 * The dialog's members are `protected` — the template's business, not another
 * class's. The spec drives them through this shape rather than the DOM, which
 * would add Material's overlays to what is really a question about arithmetic
 * and the order of two HTTP calls.
 */
interface DialogUnderTest {
  form: FormGroup<{ models: FormArray<FormGroup> }> & {
    patchValue(value: Record<string, unknown>): void;
  };
  addModel(): void;
  addSize(index: number): void;
  perLayerAt(index: number): number;
  totalPerLayer(): number;
  duplicateModel(): boolean;
  duplicateSize(index: number): boolean;
  blocked(): boolean;
  save(): void;
}

describe('CutDialog', () => {
  let fixture: ReturnType<typeof TestBed.createComponent<CutDialog>>;
  let dialog: DialogUnderTest;
  let http: HttpTestingController;
  const close = vi.fn();

  beforeEach(() => {
    close.mockClear();
    TestBed.configureTestingModule({
      imports: [CutDialog],
      providers: [
        provideZonelessChangeDetection(),
        provideTranslateService(),
        provideNativeDateAdapter(),
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: MatDialogRef, useValue: { close } },
        { provide: MAT_DIALOG_DATA, useValue: {} },
      ],
    });
    fixture = TestBed.createComponent(CutDialog);
    dialog = fixture.componentInstance as unknown as DialogUnderTest;
    http = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
  });

  /** Fills the header and the two models exactly as the ticket describes them. */
  function fillTicket(): void {
    dialog.form.patchValue({ cutNumber: 'C-900', branchId: 1 });

    const models = dialog.form.controls.models;
    TICKET.forEach((model, index) => {
      if (index > 0) {
        dialog.addModel();
      }
      models.at(index).patchValue({ modelNumber: model.modelNumber });
      model.sizes.forEach(([garmentSizeId, piecesPerLayer], sizeIndex) => {
        dialog.addSize(index);
        (models.at(index).get('sizes') as FormArray<FormGroup>)
          .at(sizeIndex)
          .patchValue({ garmentSizeId, piecesPerLayer });
      });
    });
    fixture.detectChanges();
  }

  it('opens with one model, the cut’s own', () => {
    expect(dialog.form.controls.models.length).toBe(1);
  });

  it('counts what a layer yields, per model and for the cut', () => {
    fillTicket();

    expect(dialog.perLayerAt(0)).toBe(4); // 6 + 8 + 10 twice
    expect(dialog.perLayerAt(1)).toBe(3);
    expect(dialog.totalPerLayer()).toBe(7);
  });

  it('refuses the same model number twice', () => {
    fillTicket();
    dialog.form.controls.models.at(1).patchValue({ modelNumber: '101' });
    fixture.detectChanges();

    expect(dialog.duplicateModel()).toBe(true);
    expect(dialog.blocked()).toBe(true);
  });

  it('refuses a size entered twice on one model', () => {
    fillTicket();
    const sizes = dialog.form.controls.models.at(0).get('sizes') as FormArray<FormGroup>;
    sizes.at(1).patchValue({ garmentSizeId: 3 });
    fixture.detectChanges();

    expect(dialog.duplicateSize(0)).toBe(true);
    expect(dialog.blocked()).toBe(true);
  });

  it('creates the cut, then its marker one row at a time', () => {
    fillTicket();
    dialog.save();

    const create = http.expectOne((r) => r.method === 'POST' && r.url.endsWith('/cuts'));
    // The first model is the cut's own, so the create call names it.
    expect(create.request.body.modelNumber).toBe('101');
    expect(create.request.body.cutNumber).toBe('C-900');
    create.flush({ id: 77 });

    // Strictly sequential: the next row is only sent once the previous lands.
    // Two rows naming a model that does not exist yet would otherwise race to
    // create it.
    const expected = [
      ['101', 3, 1],
      ['101', 2, 1],
      ['101', 1, 2],
      ['201', 6, 1],
      ['201', 5, 1],
      ['201', 4, 1],
    ];
    for (const [modelNumber, garmentSizeId, piecesPerLayer] of expected) {
      const row = http.expectOne('/api/cuts/77/sizes');
      expect([
        row.request.body.modelNumber,
        row.request.body.garmentSizeId,
        row.request.body.piecesPerLayer,
      ]).toEqual([modelNumber, garmentSizeId, piecesPerLayer]);
      row.flush({ id: 77 });
    }

    expect(close).toHaveBeenCalledWith(true);
  });
});
