import { Routes } from '@angular/router';
import { authGuard } from './core/auth/auth.guard';

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
    ],
  },
  { path: '**', redirectTo: '' },
];
