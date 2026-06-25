import { Directive, ElementRef, HostListener, Input, forwardRef, inject } from '@angular/core';
import { ControlValueAccessor, NG_VALUE_ACCESSOR } from '@angular/forms';

/**
 * Directiva de formato numérico en vivo para inputs. Muestra los miles separados
 * por coma y los decimales por punto (formato 1,335,265.20) a medida que se
 * escribe, y limita la cantidad de decimales a `appNumero` (por defecto 2).
 *
 * Implementa ControlValueAccessor, por lo que se usa con `[(ngModel)]` o
 * `[ngModel]`+`(ngModelChange)` y escribe en el modelo el `number` ya parseado
 * (o `null` si el campo queda vacío).
 *
 * Uso:
 *   <input appNumero ...>            // 2 decimales
 *   <input [appNumero]="3" ...>      // 3 decimales
 */
@Directive({
  selector: 'input[appNumero]',
  standalone: true,
  providers: [
    {
      provide: NG_VALUE_ACCESSOR,
      useExisting: forwardRef(() => NumeroFormatoDirective),
      multi: true,
    },
  ],
})
export class NumeroFormatoDirective implements ControlValueAccessor {
  private readonly host = inject<ElementRef<HTMLInputElement>>(ElementRef);

  @Input('appNumero') maxDecimales: number | string = 2;

  private valor: number | null = null;
  private onChange: (valor: number | null) => void = () => {};
  private onTouched: () => void = () => {};

  constructor() {
    const input = this.host.nativeElement;
    input.type = 'text';
    input.inputMode = 'decimal';
    input.autocomplete = 'off';
  }

  private get input(): HTMLInputElement {
    return this.host.nativeElement;
  }

  private get decimales(): number {
    if (this.maxDecimales === '' || this.maxDecimales == null) return 2;
    const d = Number(this.maxDecimales);
    return Number.isFinite(d) && d >= 0 ? Math.floor(d) : 2;
  }

  writeValue(value: number | null): void {
    this.valor = value ?? null;
    // No pisar lo que el usuario está tipeando: solo refrescar el texto cuando el
    // input no tiene foco (evita borrar el punto decimal recién escrito).
    if (typeof document !== 'undefined' && document.activeElement === this.input) return;
    this.input.value =
      value == null || Number.isNaN(value) ? '' : this.formatearNumero(value);
  }

  registerOnChange(fn: (valor: number | null) => void): void {
    this.onChange = fn;
  }

  registerOnTouched(fn: () => void): void {
    this.onTouched = fn;
  }

  setDisabledState(isDisabled: boolean): void {
    this.input.disabled = isDisabled;
  }

  @HostListener('input')
  onInput(): void {
    const input = this.input;
    const textoPrevio = input.value;
    const caret = input.selectionStart ?? textoPrevio.length;
    const digitosAntes = (textoPrevio.slice(0, caret).match(/\d/g) ?? []).length;
    // ¿El caret quedó en la parte decimal (a la derecha del punto)? Necesario para no
    // dejar el cursor a la izquierda del "." al escribirlo (cuenta solo dígitos).
    const idxPuntoPrevio = textoPrevio.indexOf('.');
    const enDecimales = idxPuntoPrevio !== -1 && caret > idxPuntoPrevio;

    const { texto, valor } = this.parsear(textoPrevio);
    input.value = texto;
    this.reubicarCaret(texto, digitosAntes, enDecimales);

    this.valor = valor;
    this.onChange(valor);
  }

  @HostListener('blur')
  onBlur(): void {
    const { valor } = this.parsear(this.input.value);
    this.valor = valor;
    this.input.value = valor == null ? '' : this.formatearNumero(valor);
    this.onChange(valor);
    this.onTouched();
  }

  /** Normaliza el texto crudo a `{ texto, valor }` respetando los decimales máximos. */
  private parsear(raw: string): { texto: string; valor: number | null } {
    const dec = this.decimales;
    let limpio = (raw ?? '').replace(/[^0-9.]/g, '');
    if (dec === 0) limpio = limpio.replace(/\./g, '');

    const idxPunto = limpio.indexOf('.');
    let enteros: string;
    let decimales = '';
    let tienePunto = false;
    if (idxPunto === -1) {
      enteros = limpio;
    } else {
      tienePunto = true;
      enteros = limpio.slice(0, idxPunto);
      decimales = limpio.slice(idxPunto + 1).replace(/\./g, '').slice(0, dec);
    }

    enteros = enteros.replace(/^0+(?=\d)/, '');
    const enterosAgrupados = enteros.replace(/\B(?=(\d{3})+(?!\d))/g, ',');

    let texto = enterosAgrupados;
    if (tienePunto) texto += '.' + decimales;

    const numStr = enteros + (decimales ? '.' + decimales : '');
    const valor = numStr === '' ? null : Number(numStr);
    return { texto, valor: valor != null && Number.isNaN(valor) ? null : valor };
  }

  /** Ubica el caret después de `digitos` dígitos del texto formateado. */
  private reubicarCaret(texto: string, digitos: number, enDecimales = false): void {
    let pos = 0;
    let vistos = 0;
    while (pos < texto.length && vistos < digitos) {
      if (/\d/.test(texto[pos])) vistos++;
      pos++;
    }
    // Si el caret estaba en la zona decimal, asegurarse de quedar a la DERECHA del punto
    // (caso típico: recién se escribió el "." y aún no hay decimales).
    if (enDecimales) {
      const idxPunto = texto.indexOf('.');
      if (idxPunto !== -1 && pos <= idxPunto) pos = idxPunto + 1;
    }
    this.input.setSelectionRange(pos, pos);
  }

  private formatearNumero(value: number): string {
    return value.toLocaleString('en-US', { maximumFractionDigits: this.decimales });
  }
}
