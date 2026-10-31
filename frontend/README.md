# Frontend — Boletería Baloncesto Valle del Cauca

Angular 22 (standalone + signals) · SCSS · sin librerías de UI externas.

## Cómo ejecutar

```bash
npm install
npm start        # ng serve con proxy → http://localhost:4200
npm test         # Vitest (watch=false: npm test -- --watch=false)
npm run build    # build de producción
```

## Regla del proxy (importante)

El navegador ve **todo bajo `/api/...`** (rutas relativas definidas en
`src/environments/environment.ts`) y el dev-server enruta cada prefijo al microservicio
dueño **sin reescribir el path** (solo cambia el target):

| Prefijo del navegador | Servicio | Target |
|---|---|---|
| `/api/auth/**`    | auth-service    | `http://localhost:8081` |
| `/api/catalog/**` | catalog-service | `http://localhost:8082` |
| `/api/sales/**`   | sales-service   | `http://localhost:8083` |

Los servicios exponen sus controllers con el prefijo completo (`/api/auth/login`,
`/api/catalog/partidos`, …), por eso **no hay `pathRewrite`**: quitar el prefijo rompería
los endpoints. Ejemplo: `GET /api/catalog/partidos?fecha=2026-10-30` →
`http://localhost:8082/api/catalog/partidos?fecha=2026-10-30`.

La configuración vive en `proxy.conf.json` (con el comentario de la regla) y se conecta en
`angular.json` → `serve.options.proxyConfig`.

## Estructura

```
src/app/
├── core/                  # singletons: servicios, guards, interceptors, modelos
│   ├── guards/            #   authGuard, adminGuard (CanActivateFn)
│   ├── interceptors/      #   jwtInterceptor (Bearer + logout en 401)
│   ├── models/            #   ApiError, Partido/Localidad, DTOs de auth
│   └── services/          #   AuthService (signals + localStorage), CatalogService
├── features/              # pantallas (lazy loading por ruta)
│   ├── partidos/          #   lista con filtros (?fecha=&estado=) + detalle
│   ├── login/             #   formulario reactivo (401)
│   ├── registro/          #   formulario reactivo (409)
│   └── admin/             #   shell + stubs (tareas 5.2/5.3)
└── shared/                # piezas reutilizables: ChipEstado, pipe `cop` (COP es-CO)
```

## Auth

- `AuthService` con signals (`token`, `user`, `estaLogueado`, `esAdmin`) y persistencia en
  `localStorage` (`basket_token`, `basket_usuario`).
- `jwtInterceptor` adjunta `Authorization: Bearer …` a `/api/*`; un 401 fuera del login
  cierra sesión y redirige a `/login`.
- `authGuard` / `adminGuard`: sin sesión → `/login?returnUrl=…`; USER en `/admin` → `/partidos`.
- Usuarios seed: `admin@valle.test`/`admin123` (ADMIN) · `usuario@valle.test`/`usuario123` (USER).

## Pendiente (otras tareas)

- **Tarea 4.4** — flujo de compra contra sales-service (`/api/sales/reservas`, pagar,
  código de entrada). El botón "Comprar" del detalle está deshabilitado con el comentario
  correspondiente.
- **Tareas 5.2/5.3** — CRUD de partidos y reporte de ventas dentro de `/admin`
  (hoy son stubs protegidos por `adminGuard`).
