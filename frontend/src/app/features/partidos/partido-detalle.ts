import { Component, OnInit, inject, signal } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { Partido } from '../../core/models/partido';
import { CatalogService } from '../../core/services/catalog.service';
import { ChipEstado } from '../../shared/chip-estado';
import { CopPipe } from '../../shared/pipes/cop';

/** Detalle de un partido: datos + localidades con precio y disponibilidad. */
@Component({
  selector: 'app-partido-detalle',
  imports: [RouterLink, ChipEstado, CopPipe],
  templateUrl: './partido-detalle.html',
  styleUrl: './partido-detalle.scss',
})
export class PartidoDetalle implements OnInit {
  private readonly catalogo = inject(CatalogService);
  private readonly ruta = inject(ActivatedRoute);

  readonly partido = signal<Partido | null>(null);
  readonly cargando = signal(true);

  ngOnInit(): void {
    this.ruta.paramMap.subscribe((params) => {
      const id = params.get('id');
      if (id) {
        this.cargar(id);
      } else {
        this.partido.set(null);
        this.cargando.set(false);
      }
    });
  }

  trackPorId(_index: number, localidad: { id: number }): number {
    return localidad.id;
  }

  private cargar(id: string): void {
    this.cargando.set(true);
    this.catalogo.getPartido(id).subscribe({
      next: (partido) => {
        this.partido.set(partido);
        this.cargando.set(false);
      },
      error: () => {
        this.partido.set(null);
        this.cargando.set(false);
      },
    });
  }
}
