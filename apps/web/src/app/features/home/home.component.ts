import { Component } from '@angular/core';
import { RouterLink } from '@angular/router';

@Component({
  selector: 'app-home',
  standalone: true,
  imports: [RouterLink],
  template: `
    <main class="landing">
      <h1>REFORMA</h1>
      <p>ERP SaaS para gestión integral de granjas porcinas.</p>
      <a routerLink="/auth/login" class="cta">Ingresar</a>
    </main>
  `,
  styles: [
    `
      .landing {
        min-height: 100vh;
        display: flex;
        flex-direction: column;
        align-items: center;
        justify-content: center;
        font-family: system-ui, sans-serif;
        gap: 1rem;
      }
      .cta {
        padding: 0.75rem 1.5rem;
        background: #166534;
        color: white;
        text-decoration: none;
        border-radius: 4px;
      }
    `,
  ],
})
export class HomeComponent {}
