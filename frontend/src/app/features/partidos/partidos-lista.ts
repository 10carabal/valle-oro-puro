import { Component, OnInit, inject, signal } from '@angular/core';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { Partido } from '../../core/models/partido';
import { CatalogService } from '../../core/services/catalog.service';
import { ChipEstado } from '../../shared/chip-estado';
import { CopPipe } from '../../shared/pipes/cop';

/** Lista pública de partidos con filtros por fecha y estado sincronizados con la query string. */
@Component({
  selector: 'app-partidos-lista',
  imports: [RouterLink, ChipEstado, CopPipe],
  templateUrl: './partidos-lista.html',
  styleUrl: './partidos-lista.scss',
})
export class PartidosLista implements OnInit {
  private readonly catalogo = inject(CatalogService);
  private readonly ruta = inject(ActivatedRoute);
  private readonly router = inject(Router);

  readonly partidos = signal<Partido[]>([]);
  readonly cargando = signal(true);
  readonly fecha = signal('');
  readonly estado = signal('');
  readonly estados = ['PROGRAMADO', 'JUGADO', 'CANCELADO'] as const;

  ngOnInit(): void {
    // Los filtros viven en la query string (?fecha=YYYY-MM-DD&estado=PROGRAMADO):
    // cualquier cambio de queryParams recarga la lista desde el backend.
    this.ruta.queryParamMap.subscribe((params) => {
      this.fecha.set(params.get('fecha') ?? '');
      this.estado.set(params.get('estado') ?? '');
      this.cargar();
    });
  }

  cambiarFecha(valor: string): void {
    this.actualizarFiltros({ fecha: valor });
  }

  cambiarEstado(valor: string): void {
    this.actualizarFiltros({ estado: valor });
  }

  /** Quita todos los filtros y vuelve a la lista completa. */
  limpiarFiltros(): void {
    void this.router.navigate(['/partidos']);
  }

  /** Día del bloque de fecha (talonario) a partir de YYYY-MM-DD. */
  dia(fecha: string): string {
    const [, , dia] = fecha.split('-');
    return dia ? String(Number(dia)) : '';
  }

  /** Mes abreviado en español para el bloque de fecha. */
  mes(fecha: string): string {
    const [, mes] = fecha.split('-');
    const nombres = ['ENE', 'FEB', 'MAR', 'ABR', 'MAY', 'JUN', 'JUL', 'AGO', 'SEP', 'OCT', 'NOV', 'DIC'];
    const indice = Number(mes) - 1;
    return nombres[indice] ?? '';
  }

  trackPorId(_index: number, partido: Partido): number {
    return partido.id;
  }

  private cargar(): void {
    this.cargando.set(true);
    this.catalogo.getPartidos(this.fecha() || undefined, this.estado() || undefined).subscribe({
      next: (partidos) => {
        this.partidos.set(partidos);
        this.cargando.set(false);
      },
      error: () => {
        this.partidos.set([]);
        this.cargando.set(false);
      },
    });
  }

  /** Escribe los filtros en la query string (sin valores vacíos) y navega: el subscribe
   * de ngOnInit se encarga de recargar la lista con los nuevos params. */
  private actualizarFiltros(cambios: Record<string, string>): void {
    const filtros: Record<string, string> = {};
    for (const clave of ['fecha', 'estado']) {
      const actual = clave in cambios ? cambios[clave] : (this.ruta.snapshot.queryParamMap.get(clave) ?? '');
      if (actual) {
        filtros[clave] = actual;
      }
    }
    void this.router.navigate(['/partidos'], { queryParams: filtros });
  }
}
