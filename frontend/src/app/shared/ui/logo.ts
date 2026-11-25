import { ChangeDetectionStrategy, Component } from '@angular/core';

@Component({
  selector: 'app-logo',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <svg
      viewBox="0 0 76 66"
      width="76"
      height="66"
      fill="none"
      stroke="currentColor"
      stroke-width="4.500"
      stroke-linecap="round"
      stroke-linejoin="round"
      aria-hidden="true"
    >
      <path d="M4 42 38 8l34 34M29 13l9-9 9 9M14 41v22M62 41v22M24 61V35l14-14 14 14v26" />
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
