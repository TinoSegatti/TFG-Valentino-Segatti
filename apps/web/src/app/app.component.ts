import { Component, inject } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { TemaService } from './core/tema/tema.service';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [RouterOutlet],
  template: `
    <div class="app-bg-fondo" aria-hidden="true"></div>
    <div class="app-bg-cortina" aria-hidden="true"></div>
    <div class="app-bg-gradient" aria-hidden="true"></div>
    <div class="app-bg-noise" aria-hidden="true"></div>
    <router-outlet />
  `,
  styles: [':host { display: block; min-height: 100vh; }'],
})
export class AppComponent {
  private readonly tema = inject(TemaService);

  constructor() {
    // Aplica el fondo cacheado antes del primer render (sin flash) y sincroniza con la API.
    this.tema.inicializar();
  }
}
