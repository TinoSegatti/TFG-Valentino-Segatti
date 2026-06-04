import { CanDeactivateFn } from '@angular/router';
import { CompraDetalleComponent } from './compra-detalle.component';

export const compraDetalleCanDeactivate: CanDeactivateFn<CompraDetalleComponent> = (component) => {
  if (component.puedeSalir()) {
    return true;
  }
  alert(component.mensajeBloqueoSalida());
  return false;
};
