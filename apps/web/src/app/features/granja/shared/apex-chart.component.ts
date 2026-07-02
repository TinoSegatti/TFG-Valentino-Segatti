import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  ElementRef,
  afterNextRender,
  computed,
  effect,
  inject,
  input,
  viewChild,
} from '@angular/core';
import ApexCharts from 'apexcharts';
import { ReformaChartOptions } from './apex-charts';

/**
 * Render imperativo de ApexCharts (NO usa `<apx-chart>` de ng-apexcharts).
 *
 * Motivo: ng-apexcharts dispara `render()` (asíncrono) y `destroy()` sin
 * coordinar, así que al navegar rápido entre módulos la librería dibujaba o
 * destruía sobre un DOM ya removido → errores "Element not found" y
 * "Cannot read properties of undefined (reading 'node')". Manejando la instancia
 * a mano podemos:
 *   - crear el gráfico SOLO cuando su contenedor entra en viewport (perf: no
 *     bloquea la navegación ni dibuja lo que no se ve);
 *   - hacer `render().catch()` (si el componente se destruyó mientras renderiza,
 *     se ignora en silencio);
 *   - destruir dentro de `try/catch` y con una bandera `destroyed` que evita
 *     crear nada después de la destrucción.
 *
 * Las claves `undefined` de las opciones no se pasan a ApexCharts.
 */
@Component({
  selector: 'reforma-apex',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `<div #host class="apex-host" [style.min-height.px]="minHeight()"></div>`,
  styles: [
    `
      :host {
        display: block;
      }
      .apex-host:empty {
        border-radius: 12px;
        background: linear-gradient(
          100deg,
          rgba(255, 255, 255, 0.02) 30%,
          rgba(255, 255, 255, 0.06) 50%,
          rgba(255, 255, 255, 0.02) 70%
        );
        background-size: 200% 100%;
        animation: apex-host-shimmer 1.4s ease-in-out infinite;
      }
      @keyframes apex-host-shimmer {
        from {
          background-position: 200% 0;
        }
        to {
          background-position: -200% 0;
        }
      }
      @media (prefers-reduced-motion: reduce) {
        .apex-host:empty {
          animation: none;
        }
      }
    `,
  ],
})
export class ApexChartComponent {
  readonly options = input.required<ReformaChartOptions>();

  private readonly host = viewChild.required<ElementRef<HTMLElement>>('host');

  private chart: ApexCharts | null = null;
  private observer: IntersectionObserver | null = null;
  private destroyed = false;

  /** Altura reservada antes de renderizar, para evitar saltos de layout. */
  protected readonly minHeight = computed(() => {
    const h = this.options().chart?.height;
    return typeof h === 'number' ? h : 280;
  });

  constructor() {
    const destroyRef = inject(DestroyRef);

    // Crear el gráfico cuando su contenedor sea visible.
    afterNextRender(() => this.observeVisibility());

    // Si cambian las opciones después de creado, actualizar en caliente.
    effect(() => {
      const opts = this.options();
      if (this.chart && !this.destroyed) {
        this.chart.updateOptions(this.toConfig(opts), false, false).catch(() => {});
      }
    });

    destroyRef.onDestroy(() => this.teardown());
  }

  private observeVisibility(): void {
    if (this.destroyed) return;
    const el = this.host().nativeElement;
    this.observer = new IntersectionObserver(
      (entries) => {
        if (entries.some((e) => e.isIntersecting)) {
          this.observer?.disconnect();
          this.observer = null;
          this.create();
        }
      },
      { rootMargin: '120px' },
    );
    this.observer.observe(el);
  }

  private create(): void {
    if (this.destroyed || this.chart) return;
    try {
      this.chart = new ApexCharts(this.host().nativeElement, this.toConfig(this.options()));
      // render() es asíncrono: si el componente se destruye antes de terminar,
      // ApexCharts lanzaría "Element not found"; lo ignoramos a propósito.
      this.chart.render().catch(() => {});
    } catch {
      this.chart = null;
    }
  }

  private teardown(): void {
    this.destroyed = true;
    this.observer?.disconnect();
    this.observer = null;
    const c = this.chart;
    this.chart = null;
    if (c) {
      try {
        c.destroy();
      } catch {
        /* destroy de un gráfico a medio crear: ruido benigno, lo ignoramos */
      }
    }
  }

  /** Aplana ReformaChartOptions a ApexOptions, descartando claves undefined. */
  private toConfig(o: ReformaChartOptions): ApexCharts.ApexOptions {
    const cfg: Record<string, unknown> = { series: o.series, chart: o.chart };
    const keys = [
      'xaxis',
      'yaxis',
      'labels',
      'colors',
      'dataLabels',
      'stroke',
      'fill',
      'grid',
      'legend',
      'tooltip',
      'plotOptions',
      'responsive',
      'annotations',
    ] as const;
    for (const k of keys) {
      const v = o[k];
      if (v !== undefined) cfg[k] = v;
    }
    return cfg as ApexCharts.ApexOptions;
  }
}
