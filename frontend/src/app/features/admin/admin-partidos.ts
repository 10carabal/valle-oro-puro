import { HttpErrorResponse } from '@angular/common/http';
import { Component, OnInit, computed, inject, signal } from '@angular/core';
import {
  AbstractControl,
  FormControl,
  FormGroup,
  ReactiveFormsModule,
  ValidationErrors,
  Validators,
} from '@angular/forms';
import { mensajeApi } from '../../core/models/api-error';
import { EstadoPartido, Localidad, Partido } from '../../core/models/partido';
import { AdminService } from '../../core/services/admin.service';
import { CatalogService } from '../../core/services/catalog.service';
import { ChipEstado } from '../../shared/chip-estado';
import { CopPipe } from '../../shared/pipes/cop';

/**
 * Fecha no pasada: comparación lexicográfica sobre YYYY-MM-DD (el formato que
 * produce `input type=date`) contra la fecha LOCAL del navegador.
 */
function fechaNoPasada(control: AbstractControl): ValidationErrors | null {
  const valor = control.value as unknown;
  if (typeof valor !== 'string' || valor === '') {
    return null; // el required se encarga del vacío
  }
  const hoy = new Date();
  const mes = String(hoy.getMonth() + 1).padStart(2, '0');
  const dia = String(hoy.getDate()).padStart(2, '0');
  const hoyIso = `${hoy.getFullYear()}-${mes}-${dia}`;
  return valor < hoyIso ? { fechaPasada: true } : null;
}

/**
 * Tarea 5.2 — Panel admin: CRUD de partidos y gestión de localidades (precios/cupos).
 * Lecturas con CatalogService; escrituras con AdminService (Bearer rol ADMIN).
 * Estados: carga / vacío / error en el listado y en la vista de localidades.
 */
@Component({
  selector: 'app-admin-partidos',
  imports: [ReactiveFormsModule, ChipEstado, CopPipe],
  templateUrl: './admin-partidos.html',
  styleUrl: './admin-partidos.scss',
})
export class AdminPartidos implements OnInit {
  private readonly catalogo = inject(CatalogService);
  private readonly admin = inject(AdminService);

  readonly estados: EstadoPartido[] = ['PROGRAMADO', 'JUGADO', 'CANCELADO'];

  // --- listado de partidos ---
  readonly partidos = signal<Partido[]>([]);
  readonly cargando = signal(true);
  readonly errorLista = signal<string | null>(null);

  // --- mensajes de la pantalla ---
  readonly exito = signal<string | null>(null);
  /** Aviso de cancelación parcial: partido cancelado pero reservas con reintento pendiente. */
  readonly aviso = signal<string | null>(null);
  readonly error = signal<string | null>(null);

  // --- formulario de partido (crear / editar: el modo lo define edicionId) ---
  readonly formPartido = new FormGroup({
    rival: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
    fecha: new FormControl('', { nonNullable: true, validators: [Validators.required, fechaNoPasada] }),
    hora: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
    escenario: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
    estado: new FormControl<EstadoPartido>('PROGRAMADO', {
      nonNullable: true,
      validators: [Validators.required],
    }),
  });
  readonly edicionId = signal<number | null>(null);
  readonly modoEdicion = computed(() => this.edicionId() !== null);
  readonly enviando = signal(false);

  // --- gestión de localidades del partido seleccionado ---
  readonly gestionandoId = signal<number | null>(null);
  readonly detalle = signal<Partido | null>(null);
  readonly cargandoDetalle = signal(false);
  readonly errorDetalle = signal<string | null>(null);
  readonly localidades = computed(() => this.detalle()?.localidades ?? []);

  readonly formLocalidad = new FormGroup({
    id: new FormControl<number | null>(null, { nonNullable: true }),
    nombre: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
    precio: new FormControl<number | null>(null, {
      nonNullable: true,
      validators: [Validators.required, Validators.min(0.01)], // precio > 0
    }),
    cupo: new FormControl<number | null>(null, {
      nonNullable: true,
      validators: [Validators.required, Validators.min(1)], // cupo >= 1
    }),
  });
  readonly guardandoLocalidad = signal(false);
  readonly exitoLocalidad = signal<string | null>(null);
  readonly errorLocalidad = signal<string | null>(null);

  ngOnInit(): void {
    this.cargarLista(true);
  }

  trackPorId(_index: number, item: { id: number }): number {
    return item.id;
  }

  trackPorFila(_index: number, fila: { partidoId: number; localidadId: number }): string {
    return `${fila.partidoId}-${fila.localidadId}`;
  }

  mostrarErrorPartido(control: 'rival' | 'fecha' | 'hora' | 'escenario', codigo: string): boolean {
    const campo = this.formPartido.controls[control];
    return campo.touched && campo.hasError(codigo);
  }

  mostrarErrorLocalidad(control: 'nombre' | 'precio' | 'cupo', codigo: string): boolean {
    const campo = this.formLocalidad.controls[control];
    return campo.touched && campo.hasError(codigo);
  }

  // ---------------------------------------------------------------- partidos

  /** Crea (sin id) o actualiza (con id). Formulario inválido → NO se llama al HTTP. */
  guardarPartido(): void {
    if (this.formPartido.invalid) {
      this.formPartido.markAllAsTouched();
      this.exito.set(null);
      this.error.set(null);
      return;
    }

    const id = this.edicionId();
    const v = this.formPartido.getRawValue();
    const datos = { rival: v.rival, fecha: v.fecha, hora: v.hora, escenario: v.escenario };
    const peticion =
      id === null
        ? this.admin.crearPartido(datos)
        : this.admin.editarPartido(id, { ...datos, estado: v.estado });

    this.enviando.set(true);
    this.error.set(null);
    this.exito.set(null);

    peticion.subscribe({
      next: () => {
        this.enviando.set(false);
        this.exito.set(id === null ? 'Partido creado correctamente.' : 'Partido actualizado correctamente.');
        this.limpiarFormulario();
        this.cargarLista(false); // recarga la lista con el cambio reflejado
        if (id !== null && this.gestionandoId() === id) {
          this.cargarDetalle(id, false); // la vista de localidades quedó desactualizada
        }
      },
      error: (err: HttpErrorResponse) => {
        this.enviando.set(false);
        this.error.set(mensajeApi(err));
      },
    });
  }

  /** Rellena el MISMO formulario con los datos del partido (modo edición → PUT). */
  editarPartido(p: Partido): void {
    this.exito.set(null);
    this.aviso.set(null);
    this.error.set(null);
    this.edicionId.set(p.id);
    this.formPartido.patchValue({
      rival: p.rival,
      fecha: p.fecha,
      hora: p.hora,
      escenario: p.escenario,
      estado: p.estado,
    });
  }

  cancelarEdicion(): void {
    this.limpiarFormulario();
    this.error.set(null);
  }

  /**
   * Cancelar partido (decisión de orquestación desde el cliente, sin saga
   * distribuida): la secuencia es PRIMERO catalog (`PUT /partidos/{id}/cancelar`
   * → estado CANCELADO) y DESPUÉS sales (`cancelar-por-partido` → reservas).
   * Si sales falla, el partido YA quedó cancelado y las reservas quedan
   * pendientes de un reintento manual: por eso se muestra el aviso de reintento
   * en lugar de tratarlo como un error de la cancelación.
   */
  cancelarPartido(p: Partido): void {
    const seguro = confirm(`¿Cancelar el partido "${p.rival}" del ${p.fecha}?\nSe cancelarán también sus reservas.`);
    if (!seguro) {
      return;
    }

    this.exito.set(null);
    this.aviso.set(null);
    this.error.set(null);

    this.admin.cancelarPartido(p.id).subscribe({
      next: () => {
        // 1) catalog: el partido ya está CANCELADO → reflejarlo en la UI
        this.cargarLista(false);
        if (this.gestionandoId() === p.id) {
          this.cargarDetalle(p.id, false);
        }
        // 2) sales: cancelar reservas del partido (ver comentario de la secuencia)
        this.admin.cancelarReservasPorPartido(p.id).subscribe({
          next: (respuesta) => this.exito.set(`Partido cancelado. Reservas canceladas: ${respuesta.canceladas}`),
          error: () =>
            this.aviso.set(
              'El partido se canceló, pero sus reservas no se pudieron cancelar. Requiere reintento.',
            ),
        });
      },
      error: (err: HttpErrorResponse) => this.error.set(mensajeApi(err)),
    });
  }

  // ------------------------------------------------------------- localidades

  /** Abre (o cierra) la vista de gestión de localidades del partido. */
  abrirGestion(p: Partido): void {
    if (this.gestionandoId() === p.id) {
      this.cerrarGestion();
      return;
    }
    this.gestionandoId.set(p.id);
    this.exitoLocalidad.set(null);
    this.errorLocalidad.set(null);
    this.limpiarFormLocalidad();
    this.cargarDetalle(p.id, true);
  }

  cerrarGestion(): void {
    this.gestionandoId.set(null);
    this.detalle.set(null);
    this.errorDetalle.set(null);
    this.limpiarFormLocalidad();
  }

  /** Rellena el formulario de localidad para editar precio/cupo (PUT /localidades/{id}). */
  editarLocalidad(l: Localidad): void {
    this.exitoLocalidad.set(null);
    this.errorLocalidad.set(null);
    this.formLocalidad.setValue({ id: l.id, nombre: l.nombre, precio: l.precio, cupo: l.cupo });
  }

  descartarEdicionLocalidad(): void {
    this.limpiarFormLocalidad();
    this.exitoLocalidad.set(null);
    this.errorLocalidad.set(null);
  }

  /** Crea (id nulo) o actualiza la localidad. Inválido → sin llamada HTTP. */
  guardarLocalidad(): void {
    if (this.formLocalidad.invalid) {
      this.formLocalidad.markAllAsTouched();
      this.exitoLocalidad.set(null);
      this.errorLocalidad.set(null);
      return;
    }

    const partidoId = this.gestionandoId();
    if (partidoId === null) {
      return;
    }

    const v = this.formLocalidad.getRawValue();
    const datos = { nombre: v.nombre, precio: v.precio ?? 0, cupo: v.cupo ?? 0 };
    const peticion = v.id === null ? this.admin.crearLocalidad(partidoId, datos) : this.admin.editarLocalidad(v.id, datos);

    this.guardandoLocalidad.set(true);
    this.errorLocalidad.set(null);
    this.exitoLocalidad.set(null);

    peticion.subscribe({
      next: () => {
        this.guardandoLocalidad.set(false);
        this.exitoLocalidad.set(v.id === null ? 'Localidad creada correctamente.' : 'Localidad actualizada correctamente.');
        this.limpiarFormLocalidad();
        this.cargarDetalle(partidoId, false);
        this.cargarLista(false); // el listado también muestra cupos/vendidos
      },
      error: (err: HttpErrorResponse) => {
        this.guardandoLocalidad.set(false);
        // El 409 de cupo < vendidos ("cupo menor a lo ya vendido") llega como ApiError
        // y se muestra tal cual con mensajeApi (la validación de cupo es del backend).
        this.errorLocalidad.set(mensajeApi(err));
      },
    });
  }

  // ------------------------------------------------------------------ carga

  private cargarLista(mostrarCargando: boolean): void {
    if (mostrarCargando) {
      this.cargando.set(true);
    }
    this.errorLista.set(null);
    this.catalogo.getPartidos().subscribe({
      next: (partidos) => {
        this.partidos.set(partidos);
        this.cargando.set(false);
      },
      error: (err: HttpErrorResponse) => {
        this.errorLista.set(mensajeApi(err));
        this.cargando.set(false);
      },
    });
  }

  private cargarDetalle(id: number, mostrarCargando: boolean): void {
    if (mostrarCargando) {
      this.cargandoDetalle.set(true);
    }
    this.errorDetalle.set(null);
    this.catalogo.getPartido(id).subscribe({
      next: (partido) => {
        this.detalle.set(partido);
        this.cargandoDetalle.set(false);
      },
      error: (err: HttpErrorResponse) => {
        this.errorDetalle.set(mensajeApi(err));
        this.cargandoDetalle.set(false);
      },
    });
  }

  private limpiarFormulario(): void {
    this.edicionId.set(null);
    this.formPartido.reset({ rival: '', fecha: '', hora: '', escenario: '', estado: 'PROGRAMADO' });
  }

  private limpiarFormLocalidad(): void {
    this.formLocalidad.reset({ id: null, nombre: '', precio: null, cupo: null });
  }
}
