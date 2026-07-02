/**
 * Utilidades puras para los paneles de visualización: agregación temporal por
 * mes y formateadores. Compartidas por el panel principal, Compras y
 * Fabricaciones para derivar series mensuales a partir de fechas ISO.
 */

const MESES_CORTOS = ['ene', 'feb', 'mar', 'abr', 'may', 'jun', 'jul', 'ago', 'sep', 'oct', 'nov', 'dic'];

/** 'YYYY-MM-DD…' → 'YYYY-MM'. Devuelve '' si la fecha es inválida. */
export function claveMes(iso: string | null | undefined): string {
  if (!iso || iso.length < 7) return '';
  return iso.slice(0, 7);
}

/** 'YYYY-MM' → 'ene 25'. */
export function etiquetaMes(clave: string): string {
  const [anio, mes] = clave.split('-');
  const idx = Number(mes) - 1;
  if (Number.isNaN(idx) || idx < 0 || idx > 11) return clave;
  return `${MESES_CORTOS[idx]} ${anio.slice(2)}`;
}

export interface SerieMensual {
  /** claves 'YYYY-MM' ordenadas ascendente (máx. `meses`). */
  claves: string[];
  /** etiquetas legibles paralelas a `claves`. */
  labels: string[];
  /** valores agregados paralelos a `claves`. */
  valores: number[];
}

/**
 * Agrega `items` por mes (según `fecha`) sumando `valor`, y devuelve los últimos
 * `meses` meses presentes en los datos (orden ascendente). Si `valor` no se
 * provee, cuenta ocurrencias.
 */
export function sumarPorMes<T>(
  items: readonly T[],
  fecha: (i: T) => string | null | undefined,
  valor?: (i: T) => number,
  meses = 12,
): SerieMensual {
  const acum = new Map<string, number>();
  for (const it of items) {
    const k = claveMes(fecha(it));
    if (!k) continue;
    acum.set(k, (acum.get(k) ?? 0) + (valor ? valor(it) : 1));
  }
  const claves = [...acum.keys()].sort().slice(-meses);
  return {
    claves,
    labels: claves.map(etiquetaMes),
    valores: claves.map((k) => Math.round((acum.get(k) ?? 0) * 100) / 100),
  };
}

/** Top-N por valor descendente; agrupa el resto en "Otros". */
export function topConOtros(
  entradas: { label: string; valor: number }[],
  n: number,
  etiquetaOtros = 'Otros',
): { labels: string[]; valores: number[] } {
  const orden = [...entradas].sort((a, b) => b.valor - a.valor);
  if (orden.length <= n) {
    return { labels: orden.map((e) => e.label), valores: orden.map((e) => e.valor) };
  }
  const top = orden.slice(0, n);
  const resto = orden.slice(n).reduce((s, e) => s + e.valor, 0);
  return {
    labels: [...top.map((e) => e.label), etiquetaOtros],
    valores: [...top.map((e) => e.valor), Math.round(resto * 100) / 100],
  };
}

/**
 * Como {@link topConOtros}, pero conserva un `nombre` descriptivo paralelo a cada
 * `label`. Pensado para gráficos donde el eje muestra el código corto (`labels`) y
 * el tooltip el nombre completo (`nombres`). El bucket agregado usa `etiquetaOtros`
 * en ambos.
 */
export function topNombrado(
  entradas: { label: string; nombre: string; valor: number }[],
  n: number,
  etiquetaOtros = 'Otros',
): { labels: string[]; nombres: string[]; valores: number[] } {
  const orden = [...entradas].sort((a, b) => b.valor - a.valor);
  if (orden.length <= n) {
    return {
      labels: orden.map((e) => e.label),
      nombres: orden.map((e) => e.nombre),
      valores: orden.map((e) => e.valor),
    };
  }
  const top = orden.slice(0, n);
  const resto = orden.slice(n).reduce((s, e) => s + e.valor, 0);
  return {
    labels: [...top.map((e) => e.label), etiquetaOtros],
    nombres: [...top.map((e) => e.nombre), etiquetaOtros],
    valores: [...top.map((e) => e.valor), Math.round(resto * 100) / 100],
  };
}
