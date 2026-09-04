import { ChangeDetectionStrategy, Component } from '@angular/core';
import { MatTabsModule } from '@angular/material/tabs';
import { TranslatePipe } from '@ngx-translate/core';

import { CutsTab } from './tabs/cuts-tab';
import { FabricUsageTab } from './tabs/fabric-usage-tab';
import { ModelsTab } from './tabs/models-tab';

@Component({
  selector: 'app-models-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [MatTabsModule, TranslatePipe, CutsTab, FabricUsageTab, ModelsTab],
  template: `
    <h1 class="page-title">{{ 'nav.models' | translate }}</h1>

    <mat-tab-group>
      <mat-tab [label]="'model.tabs.models' | translate">
        <ng-template matTabContent>
          <app-models-tab />
        </ng-template>
      </mat-tab>

      <mat-tab [label]="'model.tabs.cuts' | translate">
        <ng-template matTabContent>
          <app-cuts-tab />
        </ng-template>
      </mat-tab>

      <mat-tab [label]="'usage.tab' | translate">
        <ng-template matTabContent>
          <app-fabric-usage-tab />
        </ng-template>
      </mat-tab>
    </mat-tab-group>
  `,
})
export class ModelsPage {}
