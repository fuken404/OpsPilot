import { Component, input } from '@angular/core';

/** Marca: una línea de tendencia que llega a su punto, como un ticket que llega a tiempo. */
@Component({
  selector: 'app-logo',
  template: `
    <span class="logo" [class.light]="light()">
      <svg viewBox="0 0 32 32" width="28" height="28" aria-hidden="true">
        <rect width="32" height="32" rx="8" [attr.fill]="light() ? '#3f3f46' : '#27272a'" />
        <path d="M8 20.5 13.5 15l4 4L24 11.5" fill="none" stroke="#a78bfa" stroke-width="3"
              stroke-linecap="round" stroke-linejoin="round" />
        <circle cx="24" cy="11.5" r="2.5" fill="#a78bfa" />
      </svg>
      <span class="word">OpsPilot</span>
    </span>
  `,
  styles: `
    .logo { display: inline-flex; align-items: center; gap: 10px; }
    .word { font: 900 20px/1 var(--font-display); letter-spacing: -0.01em; color: var(--ink); }
    .light .word { color: #fff; }
  `,
})
export class Logo {
  readonly light = input(false);
}
