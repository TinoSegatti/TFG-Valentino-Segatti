import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, Router } from '@angular/router';
import { ReformaApiService } from '../../../data/api/reforma-api.service';
import { CompraDetalleComponent } from './compra-detalle.component';

describe('CompraDetalleComponent — salida', () => {
  let component: CompraDetalleComponent;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [CompraDetalleComponent],
      providers: [
        { provide: ReformaApiService, useValue: {} },
        {
          provide: ActivatedRoute,
          useValue: {
            snapshot: { paramMap: { get: () => 'c_test' } },
            parent: { snapshot: { paramMap: { get: () => 'g_demo' } } },
          },
        },
        { provide: Router, useValue: { navigate: jasmine.createSpy('navigate') } },
      ],
    }).compileComponents();

    component = TestBed.createComponent(CompraDetalleComponent).componentInstance;
  });

  it('permite salir sin cambios y con ítems cargados', () => {
    component.hayCambiosSinGuardar.set(false);
    component.lineas.set([
      {
        idMateriaPrima: 1,
        codigo: 'MAIZ',
        nombre: 'Maíz',
        cantidadKg: 10_000,
        precioPorKilo: 3_000,
        subtotal: 30_000_000,
        ultimoPrecioCatalogo: 3_000,
        advertenciaLinea: null,
        ultimoCampoEditado: null,
      },
    ]);
    expect(component.puedeSalir()).toBeTrue();
  });

  it('bloquea salida con cambios sin guardar aunque totales cuadren', () => {
    component.hayCambiosSinGuardar.set(true);
    component.compra.set({
      id: 'c1',
      numeroFactura: 'F-30M',
      fechaCompra: '2026-06-01',
      totalFactura: 30_000_000,
      idProveedor: 1,
      codigoProveedor: 'P1',
      nombreProveedor: 'Prov',
      estado: 'BORRADOR',
      cantidadLineas: 1,
      sumaSubtotales: 0,
      lineas: [],
    });
    component.lineas.set([
      {
        idMateriaPrima: 1,
        codigo: 'MAIZ',
        nombre: 'Maíz',
        cantidadKg: 10_000,
        precioPorKilo: 3_000,
        subtotal: 30_000_000,
        ultimoPrecioCatalogo: 3_000,
        advertenciaLinea: null,
        ultimoCampoEditado: 'cantidad',
      },
    ]);

    expect(component.totalesCuadran()).toBeTrue();
    expect(component.puedeGuardar()).toBeTrue();
    expect(component.puedeSalir()).toBeFalse();
    expect(component.mensajeBloqueoSalida()).toContain('sin guardar');
  });

  it('permite salir tras marcar guardado (sin cambios pendientes)', () => {
    component.hayCambiosSinGuardar.set(true);
    component.lineas.set([
      {
        idMateriaPrima: 1,
        codigo: 'MAIZ',
        nombre: 'Maíz',
        cantidadKg: 10_000,
        precioPorKilo: 3_000,
        subtotal: 30_000_000,
        ultimoPrecioCatalogo: 3_000,
        advertenciaLinea: null,
        ultimoCampoEditado: null,
      },
    ]);

    component.hayCambiosSinGuardar.set(false);
    expect(component.puedeSalir()).toBeTrue();
  });

  it('bloquea salida en factura vacía', () => {
    component.hayCambiosSinGuardar.set(false);
    component.lineas.set([]);
    expect(component.puedeSalir()).toBeFalse();
    expect(component.mensajeBloqueoSalida()).toContain('no tiene ítems');
  });
});
