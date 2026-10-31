import { Component, input } from '@angular/core';

/** Chip con color según el estado del partido (PROGRAMADO/JUGADO/CANCELADO). */
@Component({
  selector: 'app-chip-estado',
  template: `<span [class]="'chip chip--' + estado().toLowerCase()">{{ estado() }}</span>`,
})
export class ChipEstado {
  readonly estado = input.required<string>();
}
