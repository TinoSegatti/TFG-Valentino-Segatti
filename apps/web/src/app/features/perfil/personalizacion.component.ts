import { Component, OnDestroy, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { mensajeErrorHttp } from '../../core/http/api-error.util';
import { FONDOS_TEMA, TemaService } from '../../core/tema/tema.service';
import {
  CORTINA_MAX,
  CORTINA_MIN,
  FONDO_PERSONALIZADO,
  IMAGEN_MAX_CHARS,
  PREFERENCIAS_DEFAULT,
  PreferenciasUi,
} from '../../data/models/preferencias.model';

/**
 * Personalización del tema (módulo C): galería curada de fondos + imagen propia elegida
 * de los archivos del usuario (se reescala/comprime en el navegador antes de subir) +
 * slider de intensidad de la cortina de contraste, con preview en vivo
 * (TemaService.previsualizar). La cortina se aplica SIEMPRE sobre el fondo — también
 * sobre la imagen propia, por clara que sea — con mínimo fijo (RD-C3). Guardar persiste
 * en el backend (sigue al usuario entre dispositivos); salir sin guardar descarta la preview.
 */
@Component({
  selector: 'app-personalizacion',
  standalone: true,
  imports: [FormsModule],
  template: `
    <section class="glass-card personalizacion">
      <h3 class="reforma-section-title"><i class="pi pi-palette"></i> Personalización</h3>
      <p class="ayuda text-dim">
        Elegí un tema o subí una imagen propia como fondo de la plataforma. La cortina
        oscurece el fondo para que el texto siga siendo legible incluso con imágenes muy
        claras: su mínimo es fijo a propósito.
      </p>

      <div class="galeria">
        @for (f of fondos; track f.clave) {
          <button
            type="button"
            class="miniatura"
            [class.elegida]="fondo() === f.clave"
            [style.background]="f.css === 'none' ? 'var(--opaque-surface, #0a0a0f)' : f.css"
            (click)="elegirFondo(f.clave)"
            [attr.aria-pressed]="fondo() === f.clave"
          >
            <span class="nombre">{{ f.etiqueta }}</span>
            @if (fondo() === f.clave) {
              <i class="pi pi-check-circle"></i>
            }
          </button>
        }

        <!-- Imagen propia: elegir un archivo la selecciona y la previsualiza -->
        <label
          class="miniatura imagen-propia"
          [class.elegida]="fondo() === fondoPersonalizado"
          [style.background-image]="imagen() ? 'url(' + imagen() + ')' : ''"
          [attr.aria-pressed]="fondo() === fondoPersonalizado"
        >
          <input
            type="file"
            accept="image/png,image/jpeg,image/webp"
            (change)="onImagenSeleccionada($event)"
            [disabled]="procesandoImagen()"
            hidden
          />
          <span class="nombre">
            @if (procesandoImagen()) {
              <i class="pi pi-spin pi-spinner"></i> Procesando…
            } @else if (imagen()) {
              Imagen propia
            } @else {
              <i class="pi pi-upload"></i> Imagen propia…
            }
          </span>
          @if (fondo() === fondoPersonalizado) {
            <i class="pi pi-check-circle tilde"></i>
          }
        </label>
      </div>
      @if (imagen() && fondo() !== fondoPersonalizado) {
        <p class="mini text-dim nota-imagen">
          Tu imagen queda guardada en la tarjeta "Imagen propia": tocala para volver a usarla,
          o elegí otro archivo desde ahí.
        </p>
      }

      <label class="cortina" [class.deshabilitada]="fondo() === 'default'">
        <span class="mini text-dim">
          Intensidad de la cortina: <strong>{{ porcentajeCortina() }}%</strong>
        </span>
        <input
          type="range"
          [min]="cortinaMin"
          [max]="cortinaMax"
          step="0.05"
          [disabled]="fondo() === 'default'"
          [ngModel]="cortina()"
          (ngModelChange)="cambiarCortina($event)"
        />
      </label>

      <div class="acciones">
        <button
          type="button"
          class="reforma-btn reforma-btn-sm"
          [disabled]="guardando() || !hayCambios()"
          (click)="guardar()"
        >
          <i class="pi pi-save"></i> Guardar
        </button>
        <button
          type="button"
          class="reforma-btn-ghost reforma-btn-sm"
          [disabled]="guardando()"
          (click)="restablecer()"
        >
          <i class="pi pi-refresh"></i> Restablecer
        </button>
        @if (guardadoOk()) {
          <span class="ok"><i class="pi pi-check"></i> Guardado</span>
        }
      </div>

      @if (error()) {
        <p class="reforma-alert reforma-alert-error">
          <i class="pi pi-exclamation-circle"></i> {{ error() }}
        </p>
      }
    </section>
  `,
  styles: [
    `
      :host {
        display: block;
      }
      .personalizacion {
        margin-top: 1.25rem;
        padding: 1.25rem 1.5rem;
      }
      .reforma-section-title i {
        color: var(--reforma-accent);
        margin-right: 0.4rem;
      }
      .ayuda {
        margin: 0.25rem 0 1rem;
        font-size: 0.85rem;
      }
      .mini {
        font-size: 0.85rem;
      }
      .galeria {
        display: grid;
        grid-template-columns: repeat(auto-fill, minmax(120px, 1fr));
        gap: 0.7rem;
        margin-bottom: 1.1rem;
      }
      .miniatura {
        position: relative;
        height: 68px;
        border-radius: 12px;
        border: 1px solid var(--glass-border);
        cursor: pointer;
        display: flex;
        align-items: flex-end;
        padding: 0.4rem 0.5rem;
        transition: border-color 0.15s ease, transform 0.15s ease;
      }
      .miniatura:hover {
        transform: translateY(-2px);
      }
      .miniatura.elegida {
        border-color: var(--reforma-accent);
        box-shadow: 0 0 0 2px var(--reforma-accent-soft);
      }
      .miniatura .nombre {
        font-size: 0.72rem;
        font-weight: 600;
        color: #f1f5f9;
        text-shadow: 0 1px 4px rgba(0, 0, 0, 0.65);
        display: inline-flex;
        align-items: center;
        gap: 0.3rem;
      }
      .miniatura i {
        position: absolute;
        top: 0.4rem;
        right: 0.45rem;
        color: #ede9fe;
        text-shadow: 0 1px 4px rgba(0, 0, 0, 0.6);
      }
      .miniatura .nombre i {
        position: static;
      }
      .imagen-propia {
        background-color: var(--glass-bg-strong, rgba(255, 255, 255, 0.06));
        background-size: cover;
        background-position: center;
        border-style: dashed;
        margin: 0;
      }
      .imagen-propia.elegida {
        border-style: solid;
      }
      .nota-imagen {
        margin: -0.5rem 0 1rem;
      }
      .cortina {
        display: flex;
        flex-direction: column;
        gap: 0.35rem;
        margin-bottom: 1.1rem;
        max-width: 26rem;
      }
      .cortina.deshabilitada {
        opacity: 0.55;
      }
      .cortina input {
        accent-color: var(--reforma-accent);
      }
      .acciones {
        display: flex;
        align-items: center;
        gap: 0.6rem;
        flex-wrap: wrap;
      }
      .ok {
        color: var(--reforma-ok, #34d399);
        font-size: 0.85rem;
        display: inline-flex;
        align-items: center;
        gap: 0.3rem;
      }
    `,
  ],
})
export class PersonalizacionComponent implements OnDestroy {
  private readonly tema = inject(TemaService);

  readonly fondos = FONDOS_TEMA;
  readonly cortinaMin = CORTINA_MIN;
  readonly cortinaMax = CORTINA_MAX;
  readonly fondoPersonalizado = FONDO_PERSONALIZADO;

  /** Lado máximo tras reescalar; suficiente para un fondo y clave para entrar en el tope. */
  private static readonly IMAGEN_LADO_MAX = 1920;

  // Estado de edición (arranca en lo guardado; la preview se aplica en vivo).
  readonly fondo = signal(this.tema.prefs().fondo);
  readonly cortina = signal(this.tema.prefs().intensidadCortina);
  readonly imagen = signal<string | null>(this.tema.prefs().imagenPersonalizada ?? null);
  readonly procesandoImagen = signal(false);
  readonly guardando = signal(false);
  readonly guardadoOk = signal(false);
  readonly error = signal<string | null>(null);

  readonly porcentajeCortina = computed(() => Math.round(this.cortina() * 100));

  readonly hayCambios = computed(() => {
    const guardadas = this.tema.prefs();
    return (
      this.fondo() !== guardadas.fondo ||
      this.cortina() !== guardadas.intensidadCortina ||
      (this.fondo() === FONDO_PERSONALIZADO &&
        this.imagen() !== (guardadas.imagenPersonalizada ?? null))
    );
  });

  elegirFondo(clave: string): void {
    if (clave === FONDO_PERSONALIZADO && !this.imagen()) {
      return; // sin imagen elegida todavía; el tile de imagen tiene su propio picker
    }
    this.fondo.set(clave);
    this.previsualizar();
  }

  cambiarCortina(valor: number): void {
    this.cortina.set(Number(valor));
    this.previsualizar();
  }

  async onImagenSeleccionada(event: Event): Promise<void> {
    const input = event.target as HTMLInputElement;
    const archivo = input.files?.[0];
    input.value = ''; // permite volver a elegir el mismo archivo
    if (!archivo) {
      return;
    }
    this.procesandoImagen.set(true);
    this.error.set(null);
    try {
      const dataUrl = await this.comprimirImagen(archivo);
      this.imagen.set(dataUrl);
      this.fondo.set(FONDO_PERSONALIZADO);
      this.previsualizar();
    } catch {
      this.error.set(
        'No se pudo procesar la imagen. Probá con un archivo PNG, JPG o WebP más liviano.',
      );
    } finally {
      this.procesandoImagen.set(false);
    }
  }

  guardar(): void {
    this.guardando.set(true);
    this.error.set(null);
    this.guardadoOk.set(false);
    this.tema.guardar(this.prefsEnEdicion()).subscribe({
      next: () => {
        this.guardando.set(false);
        this.guardadoOk.set(true);
      },
      error: (err: HttpErrorResponse) => {
        this.guardando.set(false);
        this.error.set(mensajeErrorHttp(err, 'No se pudo guardar la personalización'));
      },
    });
  }

  restablecer(): void {
    this.fondo.set(PREFERENCIAS_DEFAULT.fondo);
    this.cortina.set(PREFERENCIAS_DEFAULT.intensidadCortina);
    this.previsualizar();
  }

  ngOnDestroy(): void {
    // Si se va sin guardar, vuelve a la preferencia persistida.
    this.tema.descartarPreview();
  }

  private prefsEnEdicion(): PreferenciasUi {
    const esPersonalizada = this.fondo() === FONDO_PERSONALIZADO;
    return {
      fondo: this.fondo(),
      intensidadCortina: this.cortina(),
      imagenPersonalizada: esPersonalizada ? this.imagen() : null,
    };
  }

  private previsualizar(): void {
    this.guardadoOk.set(false);
    this.tema.previsualizar(this.prefsEnEdicion());
  }

  /**
   * Reescala (lado máx. 1920 px) y comprime a JPEG bajando la calidad hasta entrar en el
   * tope del backend (~1 MB). Todo en el navegador: al servidor solo viaja el data URL final.
   */
  private async comprimirImagen(archivo: File): Promise<string> {
    const bitmap = await createImageBitmap(archivo);
    const escala = Math.min(
      1,
      PersonalizacionComponent.IMAGEN_LADO_MAX / Math.max(bitmap.width, bitmap.height),
    );
    const canvas = document.createElement('canvas');
    canvas.width = Math.max(1, Math.round(bitmap.width * escala));
    canvas.height = Math.max(1, Math.round(bitmap.height * escala));
    const ctx = canvas.getContext('2d');
    if (!ctx) {
      throw new Error('canvas no disponible');
    }
    ctx.drawImage(bitmap, 0, 0, canvas.width, canvas.height);
    bitmap.close();
    for (const calidad of [0.82, 0.65, 0.5, 0.35]) {
      const dataUrl = canvas.toDataURL('image/jpeg', calidad);
      if (dataUrl.length <= IMAGEN_MAX_CHARS) {
        return dataUrl;
      }
    }
    throw new Error('imagen demasiado grande');
  }
}
