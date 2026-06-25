import { signal } from '@angular/core';

export type DireccionOrden = 'asc' | 'desc';

/** Valor por el que se puede ordenar una columna. */
export type ValorOrden = string | number | null | undefined;

/** Mapa de clave de columna -> función que extrae el valor a comparar. */
export type AccesoresOrden<T> = Record<string, (item: T) => ValorOrden>;

/**
 * Estado de ordenamiento reutilizable para tablas. Mantiene la columna activa y
 * la dirección (asc/desc) en signals, de modo que un `computed` que llame a
 * `ordenar(...)` se recalcule automáticamente al cambiar el orden.
 *
 * Uso típico en un componente:
 *   readonly orden = new OrdenTabla();
 *   readonly itemsOrdenados = computed(() =>
 *     this.orden.ordenar(this.items(), { codigo: (i) => i.codigo, precio: (i) => i.precio }));
 *
 * En la plantilla, en cada <th> ordenable:
 *   <th class="sortable" [class.is-asc]="orden.esAsc('codigo')"
 *       [class.is-desc]="orden.esDesc('codigo')" (click)="orden.alternar('codigo')">Código</th>
 */
export class OrdenTabla {
  readonly clave = signal<string | null>(null);
  readonly direccion = signal<DireccionOrden>('asc');

  /** Alterna la columna: misma clave invierte la dirección; clave nueva arranca en asc. */
  alternar(clave: string): void {
    if (this.clave() === clave) {
      this.direccion.update((d) => (d === 'asc' ? 'desc' : 'asc'));
    } else {
      this.clave.set(clave);
      this.direccion.set('asc');
    }
  }

  esActiva(clave: string): boolean {
    return this.clave() === clave;
  }

  esAsc(clave: string): boolean {
    return this.clave() === clave && this.direccion() === 'asc';
  }

  esDesc(clave: string): boolean {
    return this.clave() === clave && this.direccion() === 'desc';
  }

  /**
   * Devuelve una copia ordenada de `items` según la columna y dirección activas.
   * Los números se comparan numéricamente; los textos con `localeCompare`
   * (numérico, para que "MP2" quede antes que "MP10"). Los valores nulos o
   * vacíos siempre quedan al final, sin importar la dirección.
   */
  ordenar<T>(items: readonly T[], accesores: AccesoresOrden<T>): T[] {
    const clave = this.clave();
    if (!clave) return [...items];
    const accesor = accesores[clave];
    if (!accesor) return [...items];

    const dir = this.direccion() === 'asc' ? 1 : -1;
    return [...items].sort((a, b) => {
      const va = accesor(a);
      const vb = accesor(b);
      const aNula = va == null || va === '';
      const bNula = vb == null || vb === '';
      if (aNula && bNula) return 0;
      if (aNula) return 1;
      if (bNula) return -1;
      if (typeof va === 'number' && typeof vb === 'number') {
        return (va - vb) * dir;
      }
      return (
        String(va).localeCompare(String(vb), 'es', { numeric: true, sensitivity: 'base' }) * dir
      );
    });
  }
}
