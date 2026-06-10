/** Estilos compartidos: vista Ver con paneles Cabecera / Detalle. */
export const GRANJA_VISTA_STYLES = `
  .back {
    color: #166534;
    text-decoration: none;
    font-size: 0.9rem;
    cursor: pointer;
  }
  h2 {
    margin: 0.5rem 0 1.25rem;
  }
  .panel {
    margin-bottom: 1.5rem;
    padding: 1rem 1.25rem;
    border: 1px solid #e5e7eb;
    border-radius: 8px;
    background: #fafafa;
  }
  .panel-header {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 1rem;
    margin-bottom: 1rem;
    padding-bottom: 0.75rem;
    border-bottom: 1px solid #e5e7eb;
  }
  .panel-header h3 {
    margin: 0;
    font-size: 1rem;
  }
  .panel-acciones {
    display: flex;
    gap: 0.5rem;
    flex-wrap: wrap;
  }
  .panel-acciones button {
    padding: 0.35rem 0.75rem;
    border: none;
    border-radius: 4px;
    cursor: pointer;
    font-size: 0.85rem;
  }
  .panel-acciones button.primario {
    background: #166534;
    color: white;
  }
  .panel-acciones button.secundario {
    background: #e5e7eb;
    color: #1f2937;
  }
  dl.vista-datos {
    display: grid;
    grid-template-columns: repeat(auto-fill, minmax(200px, 1fr));
    gap: 0.75rem;
    margin: 0;
  }
  dl.vista-datos dt {
    font-size: 0.75rem;
    color: #6b7280;
  }
  dl.vista-datos dd {
    margin: 0.15rem 0 0;
    font-weight: 600;
  }
  .conflicto {
    margin-top: 0.75rem;
    padding: 0.65rem 0.75rem;
    background: #fffbeb;
    border: 1px solid #fcd34d;
    border-radius: 4px;
    color: #b45309;
    font-size: 0.9rem;
  }
  .error {
    color: #b91c1c;
    margin-top: 0.5rem;
  }
  .hint {
    color: #6b7280;
    font-size: 0.85rem;
  }
`;
