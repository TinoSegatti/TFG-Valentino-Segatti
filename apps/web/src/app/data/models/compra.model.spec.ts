import {
  calcularCantidadCompra,
  calcularSubtotalCompra,
  COMPRA_TOLERANCIA,
  dentroToleranciaCompra,
  lineaDetalleVacia,
  recalcularLineaDetalle,
  redondearCompra,
  LineaDetalleUi,
} from './compra.model';

describe('compra.model — decimales y tolerancia', () => {
  it('redondearCompra conserva 3 decimales', () => {
    expect(redondearCompra(10.1254)).toBe(10.125);
    expect(redondearCompra(10.1255)).toBe(10.126);
  });

  it('redondearCompra maneja montos de 30 millones', () => {
    expect(redondearCompra(30_000_000)).toBe(30_000_000);
    expect(redondearCompra(15_000_000.4995)).toBe(15_000_000.5);
  });

  it('calcularSubtotalCompra con decimales', () => {
    expect(calcularSubtotalCompra(100.125, 300.333)).toBe(30_070.842);
    expect(calcularSubtotalCompra(10_000, 3_000)).toBe(30_000_000);
  });

  it('dentroToleranciaCompra respeta ±0,50', () => {
    expect(dentroToleranciaCompra(1_000, 1_000)).toBeTrue();
    expect(dentroToleranciaCompra(1_000, 999.6)).toBeTrue();
    expect(dentroToleranciaCompra(1_000, 999.4)).toBeFalse();
    expect(dentroToleranciaCompra(30_000_000, 30_000_000)).toBeTrue();
    expect(dentroToleranciaCompra(30_000_000, 29_999_999.6)).toBeTrue();
  });

  it('recalcularLineaDetalle recalcula subtotal al editar cantidad', () => {
    const linea: LineaDetalleUi = {
      ...lineaDetalleVacia(),
      cantidadKg: 100.125,
      precioPorKilo: 300.333,
      ultimoCampoEditado: 'cantidad',
    };
    recalcularLineaDetalle(linea);
    expect(linea.subtotal).toBe(30_070.842);
    expect(linea.advertenciaLinea).toBeNull();
  });

  it('recalcularLineaDetalle deriva cantidad al editar subtotal', () => {
    const linea: LineaDetalleUi = {
      ...lineaDetalleVacia(),
      precioPorKilo: 3_000,
      subtotal: 30_000_000,
      ultimoCampoEditado: 'subtotal',
    };
    recalcularLineaDetalle(linea);
    expect(linea.cantidadKg).toBe(calcularCantidadCompra(30_000_000, 3_000));
    expect(linea.advertenciaLinea).toBeNull();
  });

  it('recalcularLineaDetalle marca advertencia fuera de tolerancia por línea', () => {
    const linea: LineaDetalleUi = {
      ...lineaDetalleVacia(),
      cantidadKg: 10,
      precioPorKilo: 100,
      subtotal: 500,
      ultimoCampoEditado: null,
    };
    recalcularLineaDetalle(linea);
    expect(linea.advertenciaLinea).toContain('fuera de tolerancia');
  });

  it('suma de dos líneas de 15M cuadra con total 30M', () => {
    const lineas = [15_000_000, 15_000_000];
    const suma = redondearCompra(lineas.reduce((a, b) => a + b, 0));
    expect(dentroToleranciaCompra(30_000_000, suma)).toBeTrue();
    expect(COMPRA_TOLERANCIA).toBe(0.5);
  });
});
