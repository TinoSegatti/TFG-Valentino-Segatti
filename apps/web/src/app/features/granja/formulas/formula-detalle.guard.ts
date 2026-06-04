import { CanDeactivateFn } from '@angular/router';
import { FormulaDetalleComponent } from './formula-detalle.component';

export const formulaDetalleCanDeactivate: CanDeactivateFn<FormulaDetalleComponent> = (
  component,
) => {
  if (component.puedeSalir()) return true;
  return confirm(component.mensajeBloqueoSalida());
};
