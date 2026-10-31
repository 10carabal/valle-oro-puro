import { Pipe, PipeTransform } from '@angular/core';

const FORMATO_COP = new Intl.NumberFormat('es-CO', {
  style: 'currency',
  currency: 'COP',
  maximumFractionDigits: 0,
});

/** Formatea un precio en pesos colombianos: 15000 → "$ 15.000". */
@Pipe({ name: 'cop', standalone: true })
export class CopPipe implements PipeTransform {
  transform(valor: number | null | undefined): string {
    return valor == null ? '—' : FORMATO_COP.format(valor);
  }
}
