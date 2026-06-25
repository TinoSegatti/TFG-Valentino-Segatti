/**
 * Estilos compartidos para vistas Ver/Editar con paneles Cabecera y Detalle
 * (compra-detalle, formula-detalle, fabricacion-detalle).
 *
 * Migrados al sistema de diseño glass oscuro (ver styles.css). Ya no usamos
 * fondos claros (#fafafa) ni colores hard-coded: todo va por tokens
 * --reforma-* y --glass-* para mantener consistencia con el resto de la app.
 */
export const GRANJA_VISTA_STYLES = `
  .back {
    color: var(--reforma-accent);
    text-decoration: none;
    font-size: 0.9rem;
    cursor: pointer;
    display: inline-flex;
    align-items: center;
    gap: 0.4rem;
    transition: color 0.12s ease;
  }
  .back:hover {
    color: var(--reforma-text);
  }
  h2 {
    margin: 0.5rem 0 1.25rem;
    color: var(--reforma-text);
    font-size: 1.4rem;
    font-weight: 700;
  }
  h3 {
    color: var(--reforma-text);
  }
  .panel {
    margin-bottom: 1.5rem;
    padding: 1.25rem 1.5rem;
    border: 1px solid var(--glass-border);
    border-radius: var(--reforma-radius);
    background: var(--glass-bg);
    box-shadow: var(--glass-shadow);
    backdrop-filter: blur(var(--glass-blur));
    -webkit-backdrop-filter: blur(var(--glass-blur));
    color: var(--reforma-text);
  }
  .panel-header {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 1rem;
    margin-bottom: 1rem;
    padding-bottom: 0.75rem;
    border-bottom: 1px solid var(--glass-border);
  }
  .panel-header h3 {
    margin: 0;
    font-size: 1.05rem;
    font-weight: 600;
    color: var(--reforma-text);
  }
  .panel-acciones {
    display: flex;
    gap: 0.5rem;
    flex-wrap: wrap;
  }
  .panel-acciones button {
    padding: 0.45rem 0.9rem;
    border-radius: 8px;
    cursor: pointer;
    font: inherit;
    font-size: 0.85rem;
    transition: filter 0.12s ease, background 0.12s ease, border-color 0.12s ease;
  }
  .panel-acciones button:disabled {
    opacity: 0.55;
    cursor: not-allowed;
  }
  .panel-acciones button.primario {
    color: var(--reforma-accent-contrast);
    background: linear-gradient(180deg, var(--reforma-accent), var(--reforma-accent-strong));
    border: none;
    font-weight: 600;
  }
  .panel-acciones button.primario:hover:not(:disabled) {
    filter: brightness(1.08);
  }
  .panel-acciones button.secundario {
    color: var(--reforma-text);
    background: var(--glass-bg);
    border: 1px solid var(--glass-border);
  }
  .panel-acciones button.secundario:hover:not(:disabled) {
    background: var(--glass-bg-hover);
    border-color: var(--glass-border-strong);
  }
  dl.vista-datos {
    display: grid;
    grid-template-columns: repeat(auto-fill, minmax(220px, 1fr));
    gap: 1rem 1.25rem;
    margin: 0;
  }
  dl.vista-datos > div {
    min-width: 0;
  }
  dl.vista-datos dt {
    font-size: 0.78rem;
    color: var(--reforma-text-dim);
    font-weight: 500;
    margin-bottom: 0.25rem;
  }
  dl.vista-datos dd {
    margin: 0;
    color: var(--reforma-text);
    font-weight: 600;
  }
  .conflicto {
    margin-top: 0.75rem;
    padding: 0.65rem 0.85rem;
    background: rgba(251, 191, 36, 0.12);
    border: 1px solid rgba(251, 191, 36, 0.35);
    border-radius: 10px;
    color: #fde68a;
    font-size: 0.9rem;
  }
  .error {
    color: #fecaca;
    margin-top: 0.5rem;
    font-size: 0.9rem;
  }
  .hint {
    color: var(--reforma-text-dim);
    font-size: 0.85rem;
  }
`;
