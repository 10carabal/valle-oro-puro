/** Estado de un partido (contrato de catalog-service). */
export type EstadoPartido = 'PROGRAMADO' | 'JUGADO' | 'CANCELADO';

/** localidades[] dentro de un partido (incluye `disponibles` = cupo - vendidos). */
export interface Localidad {
  id: number;
  nombre: string;
  precio: number;
  cupo: number;
  vendidos: number;
  disponibles: number;
}

/** GET /api/catalog/partidos y GET /api/catalog/partidos/{id} */
export interface Partido {
  id: number;
  rival: string;
  fecha: string; // YYYY-MM-DD
  hora: string; // HH:mm
  escenario: string;
  estado: EstadoPartido;
  localidades: Localidad[];
}

/** GET /api/catalog/localidades/{id} (no trae `disponibles`; calcular cupo - vendidos) */
export interface LocalidadInfo {
  id: number;
  partidoId: number;
  rival: string;
  nombre: string;
  precio: number;
  cupo: number;
  vendidos: number;
}
