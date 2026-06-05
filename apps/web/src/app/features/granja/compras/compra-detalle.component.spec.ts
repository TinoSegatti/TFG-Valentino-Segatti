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

  function fijarLineasSinCambios(): void {
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
    component['establecerSnapshotLineas']();
  }

  function inputEvent(value: string): Event {
    return { target: { value } } as unknown as Event;
  }

  it('permite salir sin cambios y con ítems cargados', () => {
    fijarLineasSinCambios();
    expect(component.puedeSalir()).toBeTrue();
  });

  it('bloquea salida con cambios sin guardar aunque totales cuadren', () => {
    fijarLineasSinCambios();
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
    component.lineas.update((lineas) => {
      const copia = [...lineas];
      copia[0] = { ...copia[0], cantidadKg: 10_001 };
      return copia;
    });

    expect(component.totalesCuadran()).toBeTrue();
    expect(component.puedeGuardar()).toBeTrue();
    expect(component.puedeSalir()).toBeFalse();
    expect(component.mensajeBloqueoSalida()).toContain('sin guardar');
  });

  it('permite salir tras sincronizar snapshot (guardado exitoso)', () => {
    fijarLineasSinCambios();
    component.lineas.update((lineas) => {
      const copia = [...lineas];
      copia[0] = { ...copia[0], cantidadKg: 10_001 };
      return copia;
    });
    expect(component.puedeSalir()).toBeFalse();

    component['establecerSnapshotLineas']();
    expect(component.puedeSalir()).toBeTrue();
  });

  it('recalcula subtotal y suma con un solo item al ingresar cantidad y precio', () => {
    component.compra.set({
      id: 'c1',
      numeroFactura: 'F-1',
      fechaCompra: '2026-06-01',
      totalFactura: 30_000,
      idProveedor: 1,
      codigoProveedor: 'P1',
      nombreProveedor: 'Prov',
      estado: 'BORRADOR',
      cantidadLineas: 0,
      sumaSubtotales: 0,
      lineas: [],
    });
    component.lineas.set([
      {
        idMateriaPrima: 1,
        codigo: 'MAIZ',
        nombre: 'Maíz',
        cantidadKg: null,
        precioPorKilo: null,
        subtotal: null,
        ultimoPrecioCatalogo: null,
        advertenciaLinea: null,
        ultimoCampoEditado: null,
      },
    ]);

    component.onCampoInput(inputEvent('10'), 0, 'cantidad');
    component.onCampoInput(inputEvent('3000'), 0, 'precio');

    expect(component.lineas()[0].subtotal).toBe(30_000);
    expect(component.sumaSubtotales()).toBe(30_000);
    expect(component.totalesCuadran()).toBeTrue();
    expect(component.puedeGuardar()).toBeTrue();
  });

  it('bloquea salida en factura vacía', () => {
    component.lineas.set([]);
    component['establecerSnapshotLineas']();
    expect(component.puedeSalir()).toBeFalse();
    expect(component.mensajeBloqueoSalida()).toContain('no tiene ítems');
  });
});
