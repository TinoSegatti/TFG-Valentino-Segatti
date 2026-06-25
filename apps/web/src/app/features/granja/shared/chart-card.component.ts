import { ChangeDetectionStrategy, Component, input } from '@angular/core';

/**
 * Tarjeta glass contenedora para un gráfico (u otro contenido).
 *
 * Unifica el marco visual de todos los gráficos de los módulos y del panel
 * principal: título opcional con ícono, subtítulo, estados de carga y "sin datos",
 * y un slot de acciones. El gráfico (`<apx-chart>`) se proyecta como contenido.
 *
 * Uso:
 *   <reforma-chart-card title="Tendencia de precios" icon="pi-chart-line"
 *                       [loading]="cargando()" [empty]="series().length === 0">
 *     <apx-chart ... />
 *   </reforma-chart-card>
 */
@Component({
  selector: 'reforma-chart-card',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <section class="glass-card chart-card">
      <header>
        <div class="titles">
          <h3>
            @if (icon()) {
              <i class="pi {{ icon() }}"></i>
            }
            {{ title() }}
          </h3>
          @if (subtitle()) {
            <p>{{ subtitle() }}</p>
          }
        </div>
        <ng-content select="[actions]" />
      </header>

      <div class="content">
        @if (loading()) {
          <div class="state">
            <i class="pi pi-spin pi-spinner"></i>
            <span>Cargando…</span>
          </div>
        } @else if (empty()) {
          <div class="state empty">
            <i class="pi pi-chart-bar"></i>
            <span>{{ emptyText() }}</span>
          </div>
        } @else {
          <ng-content />
        }
      </div>
    </section>
  `,
  styles: [
    `
      :host {
        display: block;
      }
      .chart-card {
        padding: 1.1rem 1.25rem 1rem;
        height: 100%;
        box-sizing: border-box;
        display: flex;
        flex-direction: column;
      }
      header {
        display: flex;
        align-items: flex-start;
        justify-content: space-between;
        gap: 0.75rem;
        margin-bottom: 0.5rem;
      }
      h3 {
        margin: 0;
        font-size: 1rem;
        font-weight: 600;
        color: var(--reforma-text);
        display: flex;
        align-items: center;
        gap: 0.5rem;
      }
      h3 i {
        color: var(--reforma-accent);
      }
      .titles p {
        margin: 0.2rem 0 0;
        font-size: 0.82rem;
        color: var(--reforma-text-dim);
      }
      .content {
        flex: 1;
        min-height: 0;
      }
      .state {
        height: 16rem;
        display: flex;
        flex-direction: column;
        align-items: center;
        justify-content: center;
        gap: 0.6rem;
        color: var(--reforma-text-faint);
      }
      .state i {
        font-size: 1.8rem;
      }
    `,
  ],
})
export class ChartCardComponent {
  readonly title = input<string>('');
  readonly subtitle = input<string>('');
  readonly icon = input<string>('');
  readonly loading = input<boolean>(false);
  readonly empty = input<boolean>(false);
  readonly emptyText = input<string>('Sin datos para mostrar');
}
