import { Component } from '@angular/core';

@Component({
  selector: 'app-resumen',
  standalone: true,
  template: `
    <h2>Resumen de la granja</h2>
    <p>
      Pantalla en construcción. Aquí irán KPIs operativos: valor total de stock, últimas compras,
      últimas fabricaciones y alertas de IA (RF-GRN-006).
    </p>
  `,
  styles: [
    `
      :host {
        display: block;
      }
      h2 {
        margin-top: 0;
      }
      p {
        color: #4b5563;
        max-width: 60ch;
      }
    `,
  ],
})
export class ResumenComponent {}
