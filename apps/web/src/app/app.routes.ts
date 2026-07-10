import { Routes } from '@angular/router';
import { authGuard } from './core/auth/auth.guard';
import { compraDetalleCanDeactivate } from './features/granja/compras/compra-detalle.guard';
import { formulaDetalleCanDeactivate } from './features/granja/formulas/formula-detalle.guard';

export const routes: Routes = [
  {
    path: '',
    loadComponent: () => import('./features/home/home.component').then((m) => m.HomeComponent),
  },
  {
    path: 'auth/login',
    loadComponent: () =>
      import('./features/auth/login/login.component').then((m) => m.LoginComponent),
  },
  {
    path: 'auth/registro',
    loadComponent: () =>
      import('./features/auth/registro/registro.component').then((m) => m.RegistroComponent),
  },
  {
    path: 'auth/verificar-email',
    loadComponent: () =>
      import('./features/auth/verificar-email/verificar-email.component').then(
        (m) => m.VerificarEmailComponent,
      ),
  },
  {
    path: 'auth/reenviar-verificacion',
    loadComponent: () =>
      import('./features/auth/reenviar-verificacion/reenviar-verificacion.component').then(
        (m) => m.ReenviarVerificacionComponent,
      ),
  },
  {
    path: 'auth/olvide-password',
    loadComponent: () =>
      import('./features/auth/olvide-password/olvide-password.component').then(
        (m) => m.OlvidePasswordComponent,
      ),
  },
  {
    path: 'auth/restablecer',
    loadComponent: () =>
      import('./features/auth/restablecer/restablecer.component').then(
        (m) => m.RestablecerComponent,
      ),
  },
  {
    path: 'auth/aceptar-invitacion',
    loadComponent: () =>
      import('./features/auth/aceptar-invitacion/aceptar-invitacion.component').then(
        (m) => m.AceptarInvitacionComponent,
      ),
  },
  {
    // Catálogo público: visible sin login (desde la home) y logueado.
    path: 'planes',
    loadComponent: () =>
      import('./features/planes/planes.component').then((m) => m.PlanesComponent),
  },
  {
    // Pantalla de pago de la pasarela simulada (la URL que devuelve el checkout).
    path: 'planes/checkout-simulado',
    canActivate: [authGuard],
    loadComponent: () =>
      import('./features/planes/checkout-simulado.component').then(
        (m) => m.CheckoutSimuladoComponent,
      ),
  },
  {
    // Vuelta del checkout (back_url de MP / navegación de la simulada).
    path: 'planes/retorno',
    canActivate: [authGuard],
    loadComponent: () =>
      import('./features/planes/retorno.component').then((m) => m.RetornoComponent),
  },
  {
    path: 'suscripcion',
    canActivate: [authGuard],
    loadComponent: () =>
      import('./features/suscripcion/suscripcion.component').then((m) => m.SuscripcionComponent),
  },
  {
    path: 'mis-plantas',
    canActivate: [authGuard],
    loadComponent: () =>
      import('./features/mis-plantas/mis-plantas.component').then((m) => m.MisPlantasComponent),
  },
  {
    path: 'perfil',
    canActivate: [authGuard],
    loadComponent: () =>
      import('./features/perfil/perfil.component').then((m) => m.PerfilComponent),
  },
  {
    path: 'equipo',
    canActivate: [authGuard],
    loadComponent: () =>
      import('./features/equipo/equipo.component').then((m) => m.EquipoComponent),
  },
  {
    path: 'auditoria',
    canActivate: [authGuard],
    loadComponent: () =>
      import('./features/auditoria/auditoria.component').then((m) => m.AuditoriaComponent),
  },
  {
    path: 'granja/:idGranja',
    canActivate: [authGuard],
    loadComponent: () =>
      import('./features/granja/granja-shell.component').then((m) => m.GranjaShellComponent),
    children: [
      { path: '', redirectTo: 'resumen', pathMatch: 'full' },
      {
        path: 'resumen',
        loadComponent: () =>
          import('./features/granja/resumen/resumen.component').then((m) => m.ResumenComponent),
      },
      {
        path: 'materias-primas',
        loadComponent: () =>
          import('./features/granja/materias-primas/materias-primas.component').then(
            (m) => m.MateriasPrimasComponent,
          ),
      },
      {
        path: 'proveedores',
        loadComponent: () =>
          import('./features/granja/proveedores/proveedores.component').then(
            (m) => m.ProveedoresComponent,
          ),
      },
      {
        path: 'animales',
        loadComponent: () =>
          import('./features/granja/animales/animales.component').then(
            (m) => m.AnimalesComponent,
          ),
      },
      {
        path: 'compras',
        loadComponent: () =>
          import('./features/granja/compras/compras.component').then((m) => m.ComprasComponent),
      },
      {
        path: 'compras/nueva',
        loadComponent: () =>
          import('./features/granja/compras/compra-nueva.component').then(
            (m) => m.CompraNuevaComponent,
          ),
      },
      {
        path: 'compras/:idCompra',
        canDeactivate: [compraDetalleCanDeactivate],
        loadComponent: () =>
          import('./features/granja/compras/compra-detalle.component').then(
            (m) => m.CompraDetalleComponent,
          ),
      },
      {
        path: 'formulas',
        loadComponent: () =>
          import('./features/granja/formulas/formulas.component').then(
            (m) => m.FormulasComponent,
          ),
      },
      {
        path: 'formulas/nueva',
        loadComponent: () =>
          import('./features/granja/formulas/formula-nueva.component').then(
            (m) => m.FormulaNuevaComponent,
          ),
      },
      {
        path: 'formulas/:idFormula',
        canDeactivate: [formulaDetalleCanDeactivate],
        loadComponent: () =>
          import('./features/granja/formulas/formula-detalle.component').then(
            (m) => m.FormulaDetalleComponent,
          ),
      },
      {
        path: 'fabricaciones',
        loadComponent: () =>
          import('./features/granja/fabricaciones/fabricaciones.component').then(
            (m) => m.FabricacionesComponent,
          ),
      },
      {
        path: 'fabricaciones/nueva',
        loadComponent: () =>
          import('./features/granja/fabricaciones/fabricacion-nueva.component').then(
            (m) => m.FabricacionNuevaComponent,
          ),
      },
      {
        path: 'fabricaciones/:idFabricacion',
        loadComponent: () =>
          import('./features/granja/fabricaciones/fabricacion-detalle.component').then(
            (m) => m.FabricacionDetalleComponent,
          ),
      },
      {
        path: 'inventario',
        loadComponent: () =>
          import('./features/granja/inventario/inventario.component').then(
            (m) => m.InventarioComponent,
          ),
      },
      {
        path: 'informe',
        loadComponent: () =>
          import('./features/granja/informe/informe.component').then((m) => m.InformeComponent),
      },
      {
        path: 'archivos',
        loadComponent: () =>
          import('./features/granja/archivos/archivos.component').then(
            (m) => m.ArchivosComponent,
          ),
      },
      {
        path: 'archivos/:idArchivo',
        loadComponent: () =>
          import('./features/granja/archivos/archivo-detalle.component').then(
            (m) => m.ArchivoDetalleComponent,
          ),
      },
      { path: 'iventario', redirectTo: 'inventario', pathMatch: 'full' },
      { path: '**', redirectTo: 'resumen' },
    ],
  },
  { path: '**', redirectTo: '' },
];
