import { PlanSuscripcion } from './usuario.model';

export type PeriodoFacturacion = 'MENSUAL' | 'ANUAL';
export type EstadoSuscripcion = 'PENDIENTE_PAGO' | 'ACTIVA' | 'CANCELADA' | 'EXPIRADA';
export type EstadoPago = 'APROBADO' | 'RECHAZADO' | 'PENDIENTE' | 'DEVUELTO';

/** Card del catálogo público (GET /api/suscripcion/planes). Límite null = ilimitado. */
export interface LimitesPlan {
  granjas: number | null;
  empleados: number | null;
  materiasPrimas: number | null;
  proveedores: number | null;
  animales: number | null;
  formulas: number | null;
  fabricaciones: number | null;
  archivos: number | null;
}

export interface PlanCatalogo {
  plan: PlanSuscripcion;
  precioMensualArs: number;
  precioAnualArs: number;
  limites: LimitesPlan;
  prediccionStock: boolean;
}

/**
 * Estado de la suscripción del dueño (GET /api/suscripcion). Si nunca contrató,
 * gestionada=false y solo viene planEfectivo (respuesta "implícita").
 */
export interface Suscripcion {
  planEfectivo: PlanSuscripcion;
  gestionada: boolean;
  plan: PlanSuscripcion | null;
  periodo: PeriodoFacturacion | null;
  estado: EstadoSuscripcion | null;
  precioArs: number | null;
  fechaInicio: string | null;
  fechaFinPeriodo: string | null;
  planPendiente: PlanSuscripcion | null;
  periodoPendiente: PeriodoFacturacion | null;
  ultimoCobroEstado: EstadoPago | null;
  ultimoCobroFecha: string | null;
}

export interface PagoSuscripcion {
  id: number;
  montoArs: number;
  estado: EstadoPago;
  descripcion: string;
  fechaPago: string;
}

export interface PaginaPagos {
  contenido: PagoSuscripcion[];
  pagina: number;
  tamano: number;
  totalElementos: number;
  totalPaginas: number;
}

/**
 * Resultado de POST /api/suscripcion/checkout. requierePago=true → navegar a urlPago
 * (pantalla simulada o checkout de MP según modo); false → downgrade programado (RD-P5).
 */
export interface CheckoutResponse {
  modo: 'simulado' | 'mp';
  requierePago: boolean;
  urlPago: string | null;
  suscripcion: Suscripcion;
}

/** Impacto de cambiar de plan (GET /api/suscripcion/cambio-impacto) para el modal RD-P6.c. */
export type TipoCambioPlan = 'UPGRADE' | 'DOWNGRADE' | 'SIN_CAMBIO';

export interface ImpactoRecurso {
  recurso: string;
  /** null para recursos de cuenta (empleados, granjas). */
  granja: string | null;
  cantidadActual: number;
  limiteDestino: number;
  excedente: number;
}

export interface CambioPlanImpacto {
  planActual: PlanSuscripcion;
  planDestino: PlanSuscripcion;
  tipoCambio: TipoCambioPlan;
  aplicaDesde: string;
  /** Impiden confirmar (hoy solo empleados, RD-P6.b): CTA deshabilitado + "Gestionar equipo". */
  bloqueantes: ImpactoRecurso[];
  /** Datos que quedarían en sobre-límite (RD-P6.a): se puede confirmar viendo el detalle. */
  advertencias: ImpactoRecurso[];
}
