import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { ApexChartComponent } from './apex-chart.component';
import { sparkline } from './apex-charts';

export interface KpiDelta {
  /** Texto ya formateado, ej. "+12.4%" o "-2.3%". */
  valor: string;
  /** true = verde/acento, false = rojo. */
  positivo: boolean;
}

/**
 * Tarjeta KPI estilo handoff: etiqueta + pill de delta arriba, valor grande
 * (Space Grotesk / tabular-nums), subtexto y sparkline opcional al pie.
 * Hover: se eleva -5px con borde acento. Entrada escalonada vía clase `rf-rise`
 * (el padre setea `--rf-i`). Ver design_handoff_reforma_erp/README.md.
 */
@Component({
  selector: 'reforma-kpi-card',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ApexChartComponent],
  template: `
    <article class="glass-card kpi rf-rise">
      <header>
        <span class="label">
          @if (icon()) {
            <i class="pi {{ icon() }}"></i>
          }
          {{ label() }}
        </span>
        @if (delta(); as d) {
          <span class="delta" [class.neg]="!d.positivo">{{ d.valor }}</span>
        }
      </header>

      <div class="valor rf-num">{{ value() }}</div>

      @if (sub()) {
        <div class="sub">{{ sub() }}</div>
      }

      @if (spark().length > 1) {
        <div class="spark">
          <reforma-apex [options]="sparkOptions()" />
        </div>
      }
    </article>
  `,
  styles: [
    `
      :host {
        display: block;
        height: 100%;
      }
      .kpi {
        padding: 1.1rem 1.25rem 0.9rem;
        height: 100%;
        box-sizing: border-box;
        display: flex;
        flex-direction: column;
        gap: 0.35rem;
        transition: transform 0.25s ease, box-shadow 0.25s ease, border-color 0.25s ease;
      }
      .kpi:hover {
        transform: translateY(-5px);
        border-color: var(--reforma-accent);
        box-shadow: 0 28px 60px -24px rgba(0, 0, 0, 0.6);
      }
      header {
        display: flex;
        align-items: center;
        justify-content: space-between;
        gap: 0.5rem;
      }
      .label {
        display: inline-flex;
        align-items: center;
        gap: 0.4rem;
        font-size: 0.82rem;
        color: var(--reforma-text-dim);
      }
      .label i {
        color: var(--reforma-accent);
        font-size: 0.85rem;
      }
      .delta {
        font-size: 0.72rem;
        font-weight: 600;
        padding: 0.12rem 0.45rem;
        border-radius: 999px;
        color: #ccffe6;
        background: rgba(52, 211, 153, 0.16);
      }
      .delta.neg {
        color: #fecaca;
        background: rgba(251, 113, 133, 0.16);
      }
      .valor {
        font-size: 2rem;
        font-weight: 600;
        color: var(--reforma-text);
        line-height: 1.1;
        margin-top: 0.15rem;
      }
      .sub {
        font-size: 0.78rem;
        color: var(--reforma-text-faint);
      }
      .spark {
        margin-top: auto;
        padding-top: 0.4rem;
        min-height: 48px;
      }
    `,
  ],
})
export class KpiCardComponent {
  readonly label = input<string>('');
  // Acepta string | null porque suele venir del pipe `number` (devuelve string | null).
  readonly value = input<string | null>('—');
  readonly sub = input<string | null>('');
  readonly icon = input<string>('');
  readonly delta = input<KpiDelta | null>(null);
  readonly spark = input<number[]>([]);
  readonly accent = input<string>('#9d77f4');

  protected readonly sparkOptions = computed(() => sparkline(this.spark(), this.accent()));
}
