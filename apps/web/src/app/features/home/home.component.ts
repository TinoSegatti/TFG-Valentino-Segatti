import { Component } from '@angular/core';
import { RouterLink } from '@angular/router';

@Component({
  selector: 'app-home',
  standalone: true,
  imports: [RouterLink],
  template: `
    <main class="landing">
      <section class="hero glass-card">
        <span class="brand">REFORMA</span>
        <h1>El alimento de tu granja, bajo control.</h1>
        <p>
          ERP SaaS para la gestión integral de granjas porcinas: compras, fórmulas,
          fabricaciones, inventario y análisis predictivo con IA.
        </p>
        <a routerLink="/auth/login" class="cta">
          Ingresar <i class="pi pi-arrow-right"></i>
        </a>
      </section>
    </main>
  `,
  styles: [
    `
      .landing {
        min-height: 100vh;
        display: flex;
        align-items: center;
        justify-content: center;
        padding: 1.5rem;
        font-family: system-ui, -apple-system, 'Segoe UI', sans-serif;
      }
      .hero {
        max-width: 40rem;
        padding: 3rem 2.5rem;
        display: flex;
        flex-direction: column;
        gap: 1rem;
        text-align: center;
        align-items: center;
      }
      .hero .brand {
        font-weight: 800;
        letter-spacing: 0.22em;
        color: var(--reforma-accent);
      }
      .hero h1 {
        margin: 0;
        font-size: clamp(1.8rem, 4vw, 2.6rem);
        line-height: 1.15;
        color: var(--reforma-text);
      }
      .hero p {
        margin: 0;
        max-width: 34ch;
        color: var(--reforma-text-dim);
      }
      .cta {
        margin-top: 0.5rem;
        display: inline-flex;
        align-items: center;
        gap: 0.5rem;
        padding: 0.8rem 1.6rem;
        font-weight: 600;
        color: var(--reforma-accent-contrast);
        background: linear-gradient(180deg, var(--reforma-accent), var(--reforma-accent-strong));
        text-decoration: none;
        border-radius: 12px;
        transition: filter 0.12s ease, transform 0.12s ease;
      }
      .cta:hover {
        filter: brightness(1.07);
        transform: translateY(-1px);
      }
    `,
  ],
})
export class HomeComponent {}
