import { InformeEstado } from '../../../data/models/informe.model';

/**
 * Arma el informe de estado como un documento HTML **autocontenido** (RF-REP-001):
 * CSS inline y gráficos SVG generados desde los datos (sin scripts ni recursos
 * externos), para que el archivo descargado abra correcto por file:// sin red.
 *
 * El render replica las secciones de la vista (resumen, proveedores, inventario,
 * compras, consumos, IA) en un estilo de "documento imprimible" (fondo claro),
 * distinto del tema oscuro de la app a propósito: el destino típico es compartirlo
 * o imprimirlo.
 */

const ACENTO = '#7c3aed';
const CIAN = '#0891b2';
const PALETA = ['#7c3aed', '#0891b2', '#db2777', '#d97706', '#059669', '#4f46e5', '#dc2626', '#0d9488'];

export function construirInformeHtml(nombreGranja: string, informe: InformeEstado): string {
  const secciones = [
    seccionResumen(informe),
    seccionProveedores(informe),
    seccionInventario(informe),
    seccionCompras(informe),
    seccionConsumos(informe),
    seccionIa(informe),
  ].join('\n');

  return `<!DOCTYPE html>
<html lang="es">
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>Informe de estado — ${esc(nombreGranja)} (${informe.desde} a ${informe.hasta})</title>
<style>${css()}</style>
</head>
<body>
<header class="cabecera">
  <div class="marca">REFORMA · ERP porcino</div>
  <h1>Informe de estado — ${esc(nombreGranja)}</h1>
  <p class="periodo">Período: <strong>${informe.desde}</strong> a <strong>${informe.hasta}</strong>
  · Generado el ${new Date().toLocaleDateString('es-AR')} ${new Date().toLocaleTimeString('es-AR', { hour: '2-digit', minute: '2-digit' })}</p>
</header>
${secciones}
<footer class="pie">Documento generado por REFORMA. Los valores reflejan compras y fabricaciones REGISTRADAS del período y el estado actual del inventario.</footer>
</body>
</html>`;
}

// === Secciones ===

function seccionResumen(informe: InformeEstado): string {
  const r = informe.resumen;
  const kpis = [
    { label: 'Compras del período', valor: num(r.compras) },
    { label: 'Gasto total', valor: money(r.gastoTotal) },
    { label: 'Valor de stock actual', valor: money(r.valorStock) },
    { label: 'Fabricaciones', valor: num(r.fabricaciones) },
    { label: 'Kg producidos', valor: num(r.kgProducidos) + ' kg' },
    { label: 'Merma total', valor: num(r.mermaTotal) + ' kg' },
  ]
    .map(
      (k) => `<div class="kpi"><span class="kpi-valor">${k.valor}</span><span class="kpi-label">${k.label}</span></div>`,
    )
    .join('');
  return `<section><h2>1. Resumen general</h2><div class="kpis">${kpis}</div></section>`;
}

function seccionProveedores(informe: InformeEstado): string {
  const proveedores = informe.proveedores.proveedores;
  if (proveedores.length === 0) {
    return `<section><h2>2. Proveedores</h2>${vacio('Sin compras registradas en el período.')}</section>`;
  }
  const filas = proveedores
    .map(
      (p) => `<tr><td>${esc(p.codigo)}</td><td>${esc(p.nombre)}</td><td class="num">${num(p.compras)}</td>
<td class="num">${money(p.monto)}</td><td class="num">${num(p.kg)}</td><td>${esc(p.materiaPrincipal ?? '—')}</td></tr>`,
    )
    .join('');
  const grafico = svgBarrasH(
    proveedores.slice(0, 8).map((p) => ({ label: p.nombre, valor: p.monto })),
    { money: true, titulo: 'Gasto por proveedor' },
  );
  return `<section><h2>2. Proveedores</h2>
${grafico}
<table><thead><tr><th>Código</th><th>Proveedor</th><th class="num">Compras</th><th class="num">Monto</th><th class="num">Kg</th><th>Materia principal</th></tr></thead>
<tbody>${filas}</tbody></table></section>`;
}

function seccionInventario(informe: InformeEstado): string {
  const items = informe.inventario.items.filter((i) => i.cantidadReal > 0 || i.valorStock > 0);
  const filas = items
    .map(
      (i) => `<tr><td>${esc(i.codigoMateriaPrima)}</td><td>${esc(i.nombreMateriaPrima)}</td>
<td class="num">${num(i.cantidadSistema)}</td><td class="num">${num(i.cantidadReal)}</td>
<td class="num">${num(i.merma)}</td><td class="num">${money(i.valorStock)}</td></tr>`,
    )
    .join('');
  const grafico = svgBarrasH(
    [...items]
      .sort((a, b) => b.valorStock - a.valorStock)
      .slice(0, 8)
      .map((i) => ({ label: i.nombreMateriaPrima, valor: i.valorStock })),
    { money: true, titulo: 'Valor de stock por materia prima' },
  );
  const cuerpo =
    items.length === 0
      ? vacio('Sin materias primas con stock.')
      : `${grafico}
<table><thead><tr><th>Código</th><th>Materia prima</th><th class="num">Cant. sistema (kg)</th><th class="num">Cant. real (kg)</th><th class="num">Merma (kg)</th><th class="num">Valor</th></tr></thead>
<tbody>${filas}</tbody>
<tfoot><tr><th colspan="5">Totales</th><th class="num">${money(informe.inventario.valorTotal)}</th></tr></tfoot></table>`;
  return `<section><h2>3. Inventario (estado actual)</h2>${cuerpo}</section>`;
}

function seccionCompras(informe: InformeEstado): string {
  const { evolucionMensual, materias } = informe.compras;
  if (materias.length === 0) {
    return `<section><h2>4. Compras</h2>${vacio('Sin compras registradas en el período.')}</section>`;
  }
  const grafico = svgLinea(
    evolucionMensual.map((p) => ({ label: p.mes, valor: p.monto })),
    { money: true, titulo: 'Evolución mensual del gasto' },
  );
  const filas = materias
    .map(
      (m) => `<tr><td>${esc(m.codigo)}</td><td>${esc(m.nombre)}</td><td class="num">${num(m.kg)}</td>
<td class="num">${money(m.monto)}</td><td class="num">${money(m.precioMin)}</td>
<td class="num">${money(m.precioMax)}</td><td class="num">${money(m.precioPromedio)}</td></tr>`,
    )
    .join('');
  return `<section><h2>4. Compras</h2>
${grafico}
<table><thead><tr><th>Código</th><th>Materia prima</th><th class="num">Kg</th><th class="num">Monto</th><th class="num">Precio mín.</th><th class="num">Precio máx.</th><th class="num">Precio prom.</th></tr></thead>
<tbody>${filas}</tbody></table></section>`;
}

function seccionConsumos(informe: InformeEstado): string {
  const { formulas, materias } = informe.consumos;
  if (formulas.length === 0 && materias.length === 0) {
    return `<section><h2>5. Consumos</h2>${vacio('Sin fabricaciones registradas en el período.')}</section>`;
  }
  const filasFormulas = formulas
    .map(
      (f) => `<tr><td>${esc(f.codigo)}</td><td>${esc(f.descripcion)}</td><td class="num">${num(f.fabricaciones)}</td>
<td class="num">${num(f.kgProducidos)}</td><td class="num">${money(f.costoTotal)}</td></tr>`,
    )
    .join('');
  const filasMaterias = materias
    .map(
      (m) => `<tr><td>${esc(m.codigo)}</td><td>${esc(m.nombre)}</td><td class="num">${num(m.kgConsumidos)}</td>
<td class="num">${money(m.costo)}</td></tr>`,
    )
    .join('');
  const grafico = svgBarrasH(
    materias.slice(0, 8).map((m) => ({ label: m.nombre, valor: m.kgConsumidos })),
    { money: false, titulo: 'Consumo por materia prima (kg)' },
  );
  return `<section><h2>5. Consumos y producción</h2>
<h3>Fabricaciones por fórmula</h3>
<table><thead><tr><th>Código</th><th>Descripción</th><th class="num">Fabricaciones</th><th class="num">Kg producidos</th><th class="num">Costo total</th></tr></thead>
<tbody>${filasFormulas}</tbody></table>
${grafico}
<h3>Consumo por materia prima</h3>
<table><thead><tr><th>Código</th><th>Materia prima</th><th class="num">Kg consumidos</th><th class="num">Costo</th></tr></thead>
<tbody>${filasMaterias}</tbody></table></section>`;
}

function seccionIa(informe: InformeEstado): string {
  const { anomalias, prediccionesDisponibles, predicciones } = informe.ia;
  const filasAnomalias = anomalias
    .map(
      (a) => `<tr><td>${esc(a.numeroFactura)}</td><td>${a.fechaCompra}</td><td>${esc(a.nombreMateriaPrima)}</td>
<td class="num">${money(a.precioIngresado)}</td><td class="num">${a.precioPromedioHistorico != null ? money(a.precioPromedioHistorico) : '—'}</td>
<td>${esc(a.clasificacion ?? '—')}</td><td>${a.usuarioConfirmo == null ? '—' : a.usuarioConfirmo ? 'Confirmada' : 'Corregida'}</td></tr>`,
    )
    .join('');
  const bloqueAnomalias =
    anomalias.length === 0
      ? vacio('Sin alertas de precio en el período.')
      : `<table><thead><tr><th>Factura</th><th>Fecha</th><th>Materia prima</th><th class="num">Precio ingresado</th><th class="num">Prom. histórico</th><th>Clasificación</th><th>Decisión</th></tr></thead>
<tbody>${filasAnomalias}</tbody></table>`;

  let bloquePredicciones: string;
  if (!prediccionesDisponibles) {
    bloquePredicciones = `<p class="upsell">La predicción de agotamiento de stock está disponible en los planes BUSINESS y ENTERPRISE.</p>`;
  } else if (predicciones.length === 0) {
    bloquePredicciones = vacio('Sin predicciones de stock calculadas.');
  } else {
    const filas = predicciones
      .map(
        (p) => `<tr><td>${esc(p.codigoMateriaPrima)}</td><td>${esc(p.nombreMateriaPrima)}</td>
<td><span class="nivel" data-nivel="${esc(p.nivelAlerta ?? '')}">${esc(p.nivelAlerta ?? '—')}</span></td>
<td class="num">${p.diasRestantes != null ? num(p.diasRestantes) : '—'}</td><td>${p.fechaAgotamiento ?? '—'}</td></tr>`,
      )
      .join('');
    bloquePredicciones = `<table><thead><tr><th>Código</th><th>Materia prima</th><th>Nivel</th><th class="num">Días restantes</th><th>Fecha estimada de agotamiento</th></tr></thead>
<tbody>${filas}</tbody></table>`;
  }
  return `<section><h2>6. Inteligencia artificial</h2>
<h3>Alertas de precio del período</h3>
${bloqueAnomalias}
<h3>Predicción de agotamiento de stock</h3>
${bloquePredicciones}</section>`;
}

// === Gráficos SVG (sin dependencias; abren por file://) ===

/** Barras horizontales: etiqueta, barra proporcional y valor. */
function svgBarrasH(datos: { label: string; valor: number }[], opts: { money: boolean; titulo: string }): string {
  const filtrados = datos.filter((d) => d.valor > 0);
  if (filtrados.length === 0) return '';
  const ancho = 760;
  const altoBarra = 24;
  const gap = 10;
  const xBarra = 220;
  const anchoBarraMax = ancho - xBarra - 120;
  const alto = filtrados.length * (altoBarra + gap) + 36;
  const max = Math.max(...filtrados.map((d) => d.valor));
  const barras = filtrados
    .map((d, i) => {
      const y = 30 + i * (altoBarra + gap);
      const w = Math.max(2, (d.valor / max) * anchoBarraMax);
      const color = PALETA[i % PALETA.length];
      return `<text x="${xBarra - 8}" y="${y + altoBarra / 2 + 4}" text-anchor="end" class="svg-label">${esc(recortar(d.label, 28))}</text>
<rect x="${xBarra}" y="${y}" width="${w.toFixed(1)}" height="${altoBarra}" rx="4" fill="${color}" fill-opacity="0.85"/>
<text x="${xBarra + w + 8}" y="${y + altoBarra / 2 + 4}" class="svg-valor">${opts.money ? money(d.valor) : num(d.valor)}</text>`;
    })
    .join('\n');
  return `<figure class="grafico"><svg viewBox="0 0 ${ancho} ${alto}" role="img" aria-label="${esc(opts.titulo)}">
<text x="0" y="16" class="svg-titulo">${esc(opts.titulo)}</text>
${barras}
</svg></figure>`;
}

/** Línea con puntos y valores — para la evolución mensual. */
function svgLinea(datos: { label: string; valor: number }[], opts: { money: boolean; titulo: string }): string {
  if (datos.length === 0) return '';
  const ancho = 760;
  const alto = 260;
  const margen = { arriba: 34, abajo: 40, izq: 70, der: 30 };
  const anchoUtil = ancho - margen.izq - margen.der;
  const altoUtil = alto - margen.arriba - margen.abajo;
  const max = Math.max(...datos.map((d) => d.valor), 1);
  const paso = datos.length > 1 ? anchoUtil / (datos.length - 1) : 0;
  const puntos = datos.map((d, i) => ({
    x: margen.izq + (datos.length > 1 ? i * paso : anchoUtil / 2),
    y: margen.arriba + altoUtil - (d.valor / max) * altoUtil,
    ...d,
  }));
  const poli = puntos.map((p) => `${p.x.toFixed(1)},${p.y.toFixed(1)}`).join(' ');
  const marcas = puntos
    .map(
      (p) => `<circle cx="${p.x.toFixed(1)}" cy="${p.y.toFixed(1)}" r="4" fill="${ACENTO}"/>
<text x="${p.x.toFixed(1)}" y="${(p.y - 10).toFixed(1)}" text-anchor="middle" class="svg-valor">${opts.money ? money(p.valor) : num(p.valor)}</text>
<text x="${p.x.toFixed(1)}" y="${alto - 14}" text-anchor="middle" class="svg-label">${esc(p.label)}</text>`,
    )
    .join('\n');
  // Área bajo la línea para dar el mismo lenguaje visual que la app.
  const area = `${margen.izq},${margen.arriba + altoUtil} ${poli} ${(margen.izq + (datos.length > 1 ? (datos.length - 1) * paso : anchoUtil / 2)).toFixed(1)},${margen.arriba + altoUtil}`;
  const ejeY = [0, 0.5, 1]
    .map((f) => {
      const y = margen.arriba + altoUtil - f * altoUtil;
      return `<line x1="${margen.izq}" y1="${y}" x2="${ancho - margen.der}" y2="${y}" class="svg-grid"/>
<text x="${margen.izq - 8}" y="${y + 4}" text-anchor="end" class="svg-label">${opts.money ? money(max * f) : num(max * f)}</text>`;
    })
    .join('\n');
  return `<figure class="grafico"><svg viewBox="0 0 ${ancho} ${alto}" role="img" aria-label="${esc(opts.titulo)}">
<text x="0" y="16" class="svg-titulo">${esc(opts.titulo)}</text>
${ejeY}
<polygon points="${area}" fill="${ACENTO}" fill-opacity="0.12"/>
<polyline points="${poli}" fill="none" stroke="${ACENTO}" stroke-width="2.5"/>
${marcas}
</svg></figure>`;
}

// === Utilidades ===

function css(): string {
  return `
  :root { color-scheme: light; }
  * { box-sizing: border-box; }
  body { margin: 0 auto; padding: 2.2rem 1.6rem 3rem; max-width: 880px; color: #1e293b;
    font: 15px/1.55 -apple-system, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif; background: #ffffff; }
  .cabecera { border-bottom: 3px solid ${ACENTO}; padding-bottom: 1rem; margin-bottom: 1.6rem; }
  .marca { font-size: 0.78rem; font-weight: 700; letter-spacing: 0.14em; text-transform: uppercase; color: ${ACENTO}; }
  h1 { margin: 0.3rem 0 0.4rem; font-size: 1.7rem; }
  .periodo { margin: 0; color: #475569; }
  h2 { margin: 2.2rem 0 0.8rem; font-size: 1.25rem; padding-bottom: 0.3rem; border-bottom: 1px solid #e2e8f0; }
  h3 { margin: 1.4rem 0 0.5rem; font-size: 1rem; color: #334155; }
  .kpis { display: grid; grid-template-columns: repeat(auto-fit, minmax(150px, 1fr)); gap: 0.7rem; }
  .kpi { border: 1px solid #e2e8f0; border-radius: 10px; padding: 0.75rem 0.9rem; background: #f8fafc; display: flex; flex-direction: column; }
  .kpi-valor { font-size: 1.2rem; font-weight: 700; color: ${CIAN}; }
  .kpi-label { font-size: 0.78rem; color: #64748b; }
  table { width: 100%; border-collapse: collapse; margin: 0.6rem 0 1rem; font-size: 0.88rem; }
  th, td { padding: 0.45rem 0.6rem; border-bottom: 1px solid #e2e8f0; text-align: left; vertical-align: top; }
  thead th { background: #f1f5f9; font-size: 0.78rem; text-transform: uppercase; letter-spacing: 0.05em; color: #475569; }
  tfoot th { border-top: 2px solid #cbd5e1; }
  .num, td.num, th.num { text-align: right; font-variant-numeric: tabular-nums; }
  .vacio { color: #64748b; font-style: italic; background: #f8fafc; border: 1px dashed #cbd5e1; border-radius: 8px; padding: 0.7rem 0.9rem; }
  .upsell { color: #7c3aed; background: #f5f3ff; border: 1px solid #ddd6fe; border-radius: 8px; padding: 0.7rem 0.9rem; }
  .grafico { margin: 0.8rem 0; }
  .grafico svg { width: 100%; height: auto; }
  .svg-titulo { font-size: 13px; font-weight: 700; fill: #334155; }
  .svg-label { font-size: 11px; fill: #64748b; }
  .svg-valor { font-size: 11px; font-weight: 600; fill: #334155; }
  .svg-grid { stroke: #e2e8f0; stroke-width: 1; }
  .nivel { font-size: 0.75rem; font-weight: 700; padding: 0.1rem 0.5rem; border-radius: 999px; background: #f1f5f9; }
  .nivel[data-nivel='CRITICO'] { color: #b91c1c; background: #fee2e2; }
  .nivel[data-nivel='ALERTA'] { color: #c2410c; background: #ffedd5; }
  .nivel[data-nivel='ATENCION'] { color: #a16207; background: #fef9c3; }
  .nivel[data-nivel='NORMAL'], .nivel[data-nivel='SIN_RIESGO'], .nivel[data-nivel='CRECIENTE'] { color: #15803d; background: #dcfce7; }
  .pie { margin-top: 2.5rem; padding-top: 1rem; border-top: 1px solid #e2e8f0; font-size: 0.78rem; color: #94a3b8; }
  @media print { body { padding: 0; } section { break-inside: avoid; } }
  `;
}

function vacio(texto: string): string {
  return `<p class="vacio">${esc(texto)}</p>`;
}

function esc(valor: string): string {
  return valor
    .replaceAll('&', '&amp;')
    .replaceAll('<', '&lt;')
    .replaceAll('>', '&gt;')
    .replaceAll('"', '&quot;');
}

function recortar(texto: string, max: number): string {
  return texto.length > max ? texto.slice(0, max - 1) + '…' : texto;
}

function num(valor: number): string {
  return Math.round(valor).toLocaleString('es-AR');
}

function money(valor: number): string {
  return '$ ' + Math.round(valor).toLocaleString('es-AR');
}
