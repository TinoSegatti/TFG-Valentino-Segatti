import { Component, inject, OnInit, signal } from '@angular/core';
import { ActivatedRoute, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { ReformaApiService } from '../../data/api/reforma-api.service';

@Component({
  selector: 'app-granja-shell',
  standalone: true,
  imports: [RouterLink, RouterLinkActive, RouterOutlet],
  template: `
    <nav class="nav">
      <a routerLink="/mis-plantas" class="back">← Granjas</a>
      <span class="title">{{ nombre() || 'Cargando…' }}</span>
    </nav>
    <aside class="menu">
      <a routerLink="resumen" routerLinkActive="active">Resumen</a>
      <a routerLink="materias-primas" routerLinkActive="active">Materias primas</a>
      <a routerLink="proveedores" routerLinkActive="active">Proveedores</a>
      <a routerLink="animales" routerLinkActive="active">Animales</a>
      <a routerLink="compras" routerLinkActive="active">Compras</a>
      <a routerLink="formulas" routerLinkActive="active">Formulas</a>
      <a routerLink="fabricaciones" routerLinkActive="active">Fabricaciones</a>
      <a routerLink="inventario" routerLinkActive="active">Inventario</a>
    </aside>
    <main class="content">
      <router-outlet />
    </main>
  `,
  styles: [
    `
      :host {
        display: grid;
        grid-template-columns: 220px 1fr;
        grid-template-rows: auto 1fr;
        min-height: 100vh;
        font-family: system-ui, sans-serif;
      }
      .nav {
        grid-column: 1 / -1;
        padding: 1rem 1.5rem;
        background: #166534;
        color: white;
        display: flex;
        gap: 1.5rem;
        align-items: center;
      }
      .nav .back {
        color: #d1fae5;
        text-decoration: none;
      }
      .nav .title {
        font-weight: 600;
      }
      .menu {
        padding: 1rem;
        background: #f9fafb;
        border-right: 1px solid #e5e7eb;
        display: flex;
        flex-direction: column;
        gap: 0.5rem;
      }
      .menu a {
        padding: 0.5rem 0.75rem;
        color: #1f2937;
        text-decoration: none;
        border-radius: 4px;
      }
      .menu a.active {
        background: #166534;
        color: white;
      }
      .menu .todo {
        margin-top: 1rem;
        color: #6b7280;
        font-size: 0.8rem;
      }
      .content {
        padding: 1.5rem;
      }
    `,
  ],
})
export class GranjaShellComponent implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly api = inject(ReformaApiService);

  readonly idGranja = () => this.route.snapshot.paramMap.get('idGranja') ?? '';
  readonly nombre = signal<string>('');

  ngOnInit(): void {
    const id = this.idGranja();
    if (!id) {
      return;
    }
    this.api.getGranja(id).subscribe({
      next: (g) => this.nombre.set(g.nombreGranja),
      // Si falla la carga del nombre, mostramos el id como respaldo (no rompe la navegación).
      error: () => this.nombre.set(`Granja ${id}`),
    });
  }
}
