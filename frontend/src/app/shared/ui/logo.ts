import { ChangeDetectionStrategy, Component } from '@angular/core';

@Component({
  selector: 'app-logo',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <svg viewBox="0 0 76 66" width="76" height="66" fill="none" stroke="currentColor" stroke-width="4" aria-hidden="true">
      <path d="M3 38 38 5l35 33M15 62V40l23-22 23 22v22M27 62V44l11-10 11 10v18" />
    </svg>
    <span>Jiva House</span>
  `,
  styles: `
    :host {
      display: inline-flex;
      flex-direction: column;
      align-items: center;
      gap: 0.25rem;
      font-size: 1.35rem;
      font-weight: 600;
    }
  `,
})
export class Logo {}
