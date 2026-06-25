import { Component } from '@angular/core';
import { RouterOutlet } from '@angular/router';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [RouterOutlet],
  template: `
    <div class="app-bg-gradient" aria-hidden="true"></div>
    <div class="app-bg-noise" aria-hidden="true"></div>
    <router-outlet />
  `,
  styles: [':host { display: block; min-height: 100vh; }'],
})
export class AppComponent {}
