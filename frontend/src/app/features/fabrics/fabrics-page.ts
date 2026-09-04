import { ChangeDetectionStrategy, Component } from '@angular/core';
import { MatTabsModule } from '@angular/material/tabs';
import { TranslatePipe } from '@ngx-translate/core';

import { RemainingTab } from './tabs/remaining-tab';
import { FabricTypesTab } from './tabs/fabric-types-tab';
import { IntakesTab } from './tabs/intakes-tab';
import { StockTab } from './tabs/stock-tab';

@Component({
  selector: 'app-fabrics-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [MatTabsModule, TranslatePipe, FabricTypesTab, IntakesTab, RemainingTab, StockTab],
  template: `
    <h1 class="page-title">{{ 'nav.fabrics' | translate }}</h1>

    <mat-tab-group>
      <mat-tab [label]="'fabric.tabs.inventory' | translate">
        <ng-template matTabContent>
          <app-stock-tab />
        </ng-template>
      </mat-tab>

      <mat-tab [label]="'fabric.tabs.intakes' | translate">
        <ng-template matTabContent>
          <app-intakes-tab />
        </ng-template>
      </mat-tab>

      <mat-tab [label]="'fabric.tabs.remaining' | translate">
        <ng-template matTabContent>
          <app-remaining-tab />
        </ng-template>
      </mat-tab>

      <mat-tab [label]="'fabric.tabs.types' | translate">
        <ng-template matTabContent>
          <app-fabric-types-tab />
        </ng-template>
      </mat-tab>
    </mat-tab-group>
  `,
})
export class FabricsPage {}
