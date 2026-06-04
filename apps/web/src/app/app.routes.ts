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
    path: 'mis-plantas',
    canActivate: [authGuard],
    loadComponent: () =>
      import('./features/mis-plantas/mis-plantas.component').then((m) => m.MisPlantasComponent),
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
        path: 'compras/:idCompra/editar',
        loadComponent: () =>
          import('./features/granja/compras/compra-editar.component').then(
            (m) => m.CompraEditarComponent,
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
        path: 'formulas/:idFormula/editar',
        loadComponent: () =>
          import('./features/granja/formulas/formula-editar.component').then(
            (m) => m.FormulaEditarComponent,
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
    ],
  },
  { path: '**', redirectTo: '' },
];
