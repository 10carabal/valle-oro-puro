# PLAN: Prueba CODESA — Boletería Baloncesto Valle del Cauca (4 días)

Flujo de trabajo tipo pendientes48: bloques por día, 🅿️ = paralelizable (una por agente),
cada tarea termina con una verificación concreta. No pasar de bloque sin revisar el anterior.

Leyenda de estado: `[ ]` pendiente · `[~]` en curso · `[x]` hecho · `❌` recortado

## Lo intocable (en este orden)

1. Caso crítico de concurrencia (la última entrada) + test verificable
2. 3 microservicios (auth, catalog, sales) con comunicación documentada
3. JWT + roles validados en el backend
4. JPQL parametrizada + Native Query parametrizada, justificadas en README
5. README + diagrama (lo primero que lee el evaluador)

Si un día se cae, se recorta en este orden: Swagger → pantalla "mis entradas" →
filtros avanzados → script demo. Nunca lo intocable.

## Decisiones cerradas

- Sin gateway: Angular (proxy) → 3 servicios; cada uno valida JWT con `common`
- Inventario (cupo/vendidos) en `catalog` (dueño de la localidad)
- Concurrencia: `UPDATE ... WHERE vendidos + :qty <= cupo` atómico + `CHECK` en BD +
  `UNIQUE(codigo_unico)`; tests con 2 y 20 hilos (Testcontainers)
- `RestClient` para `sales → catalog` (sin Spring Cloud)
- Naming en español: partidos, localidades, reservas, entradas
- PostgreSQL 16 con 3 esquemas (auth, catalog, sales), sin joins entre esquemas
- Stack: Java 21, Spring Boot 3.5.x, Maven wrapper, Flyway, Spring Security + JWT,
  Spring Data JPA, Springdoc, Testcontainers; Angular standalone + signals
- Puertos: auth 8081 · catalog 8082 · sales 8083 · PG **5433 en el host** (5432 la ocupaba
  `veni-postgres` de otro proyecto; interno Docker sigue en 5432) · Angular 4200

---

## Día 1 — Base + auth + catalog

### Bloque 0 — Base (secuencial)
- [x] 0.1 Instalar JDK 21 (`winget install Microsoft.OpenJDK.21`) y verificar `java -version`
  - Nota: OpenJDK 21.0.13 en `C:\Program Files\Microsoft\jdk-21.0.13.21-hotspot`; el PATH de los
    shells en curso hay que refrescarlo (`[Environment]::GetEnvironmentVariable('Path','Machine')`)
  - Verificación: `java -version` muestra 21 ✅ 2026-10-28
- [x] 0.2 `git init -b main` en `basket/`, `.gitignore` de Java/Angular/IDE, primer commit
  - Verificación: `git status` limpio salvo PLAN.md en staging
- [x] 0.3 Estructura monorepo: `backend/{common,auth-service,catalog-service,sales-service}`, `frontend/`, `infra/`
  - Nota: POM padre Spring Boot **3.5.16**, jjwt **0.12.7** (el BOM 0.12.6 no existe en Central),
    Maven 3.9.16 con wrapper (`mvnw.cmd`, distributionType=only-script) y Maven local en
    `Downloads\Dev\tools\apache-maven-3.9.16`
  - Verificación: `mvnw -B test` → BUILD SUCCESS (5/5 módulos) ✅ 2026-10-28
- [x] 0.4 `infra/docker-compose.yml`: PostgreSQL 16 (volúmen + healthcheck) + las 3 apps con `infra/Dockerfile`
    parametrizado (`--build-arg MODULE`) y `--profile full` (el MVP base es compose solo con PG)
  - Verificación: `docker compose up -d` levanta PG y los servicios crean sus esquemas con Flyway
- [x] 0.5 Flyway por servicio (`spring.flyway.schemas` = auth/catalog/sales) + seed de datos de ejemplo
    en las migraciones de cada servicio (usuarios USER/ADMIN, 3 partidos, localidades con cupo)
  - Verificación: al arrancar cada servicio sus migraciones aplican sin error y el seed deja datos
  - Hecho dentro de los bloques 1-3 (V2 de cada servicio; el seed de sales es vacío a propósito:
    las reservas son transaccionales). Fechas del seed relativas a `CURRENT_DATE`

### Bloque 1 — auth-service 🅿️ (agente A) — ✅ COMPLETO (2026-10-28)
- [x] 1.1 Módulo Maven `auth-service`: entidad `usuarios`, Flyway (`id, email UNIQUE, password_hash, nombre, rol, creado_en`)
  - Verificación: `./mvnw -q test` pasa con contexto arrancando contra PG de compose ✅ (Testcontainers)
- [x] 1.2 Registro (BCrypt) + login que emite JWT HS256 (sub=email, rol, exp) con `jjwt`
  - Verificación: curl registro → 201; curl login → token; token con rol decodifica correcto ✅ (test E2E)
- [x] 1.3 Seguridad: filtro JWT propio + `common` de utilidades; endpoints públicos (`/api/auth/**`) vs protegidos
  - Verificación: petición sin token → 401; con token inválido → 401 ✅
- [x] 1.4 Manejo centralizado de errores (`@RestControllerAdvice` + `ErrorResponse` en `common`) y validaciones (`@Valid`)
  - Verificación: email duplicado → 409 con cuerpo JSON de error; body inválido → 400 ✅
  - Resultado: 9/9 tests en verde (6 integración + 3 unitarios). Seed admin@valle.test/admin123,
    usuario@valle.test/usuario123. Hallazgo: conflicto jjwt-bom↔jackson resuelto centralizando
    `jackson-bom` antes que `jjwt-bom` en el pom padre (ver memory.md §7).

### Bloque 2 — catalog-service 🅿️ (agente B) — ✅ COMPLETO (2026-10-28)
- [x] 2.1 Módulo `catalog-service`: entidades `partidos` y `localidades` + migraciones
    (`partidos(rival, fecha, hora, escenario, estado)`, `localidades(nombre, precio, cupo, vendidos)`,
    `CHECK vendidos <= cupo`, índices `partidos(fecha, estado)`, `localidades(partido_id)`)
  - Verificación: `./mvnw -q test` pasa; `psql` muestra tablas + índices + CHECK ✅
- [x] 2.2 CRUD de partidos/localidades con rol ADMIN (`POST/PUT/DELETE` → 403 sin rol ADMIN)
  - Verificación: con token USER un PUT → 403; con token ADMIN → 200 ✅ (8 tests de controller;
    cancelar es lógico `PUT /partidos/{id}/cancelar`, sin DELETE físico)
- [x] 2.3 **JPQL parametrizada**: filtros por fecha y/o estado + disponibilidad por localidad
  - Verificación: test de repositorio con los 3 combinaciones (solo fecha, solo estado, ambas) ✅
  - Query final: `SELECT DISTINCT p FROM Partido p LEFT JOIN FETCH p.localidades l WHERE
    (:estado IS NULL OR p.estado = :estado) AND (CAST(:fecha AS java.time.LocalDate) IS NULL OR
    p.fecha = :fecha) ORDER BY p.fecha` — el CAST es obligatorio en Hibernate 6.6 + PG para
    parámetro LocalDate nulo (error 42P18 sin tipar)
- [x] 2.4 Endpoint de inventario `POST /api/catalog/localidades/{id}/reservar` (UPDATE atómico,
    0 filas → 409) y `.../liberar` (compensación). Este es el corazón del caso crítico
  - Verificación: test de integración con hilos: N llamadas sobre cupo=1 → exactamente 1 éxito ✅
  - `ConcurrenciaCupoTest`: 10 hilos con barrera de doble CountDownLatch → **1 éxito, 9
    ConflictException, vendidos==1** sobre Postgres 16 real (Testcontainers)
  - Resultado: 11/11 tests en verde ✅ 2026-10-28

## Día 2 — sales-service + caso crítico

### Bloque 3 — sales-service (primero lo intocable) — ✅ COMPLETO (2026-10-28)
- [x] 3.1 Módulo `sales-service`: entidades `reservas` (con `partido_id` + `rival_snapshot` desnormalizado,
    estado PENDING/CONFIRMED/REJECTED/CANCELLED) y `entradas` (`codigo_unico UNIQUE`); migraciones + índices
    `reservas(user_id)`, `reservas(localidad_id, estado)`
  - Verificación: `./mvnw -q test` pasa; tablas e índices visibles en `psql` ✅
  - `entradas.reserva_id` además UNIQUE (1 entrada por reserva); índices: `reservas(usuario_email)`,
    `reservas(localidad_id, estado)`, `reservas(partido_id)` — todos justificados en comentarios SQL
- [x] 3.2 Saga de reserva: `POST /reservas` (PENDING → llama `catalog` con `RestClient` → UPDATE atómico;
    si 409 → REJECTED/409) → `POST /reservas/{id}/pagar` (pago simulado → CONFIRMED + entrada con UUID)
    → cancelación libera cupo (compensación)
  - Verificación: flujo completo por curl: reserva → pagar → entrada con código; cancelar libera (`vendidos` baja) ✅
  - Detalle: REJECTED se persiste en transacción `REQUIRES_NEW` (trazabilidad) antes de propagar el 409;
    el Bearer del usuario se reenvía a catalog (propagación de JWT); `pagar` → 200; `hikari.pool=40`
    solo en el test de concurrencia (cada hilo perdedor ocupa 2 conexiones por el REQUIRES_NEW)
- [x] 3.3 **Native Query** de reporte: cantidad vendida y total recaudado por partido y localidad
    (`COUNT`, `SUM`, `GROUP BY` sobre `reservas`+`entradas` dentro del esquema `sales`) + endpoint admin
  - Verificación: con seed, el reporte devuelve las filas esperadas (test con assert exacto) ✅
  - `GET /api/sales/reportes/ventas?partidoId=` (ADMIN): solo CONFIRMED cuenta; CANCELLED/REJECTED no
- [x] 3.4 **Tests de concurrencia (Testcontainers)**: 2 hilos y 20 hilos comprando la última entrada
    vía el flujo de `sales` → exactamente `cupo` éxitos y el resto 409, `vendidos == cupo`
  - Verificación: `./mvnw test` en verde, ambas pruebas; resultados anotados aquí ✅
  - `ConcurrenciaCompraTest`: 10 hilos con barrera sobre cupo=1 (mock CAS replicando el contrato de
    catalog) → **1 CONFIRMED + 9 REJECTED, exactamente 1 entrada**; la garantía fuerte vive en el
    `ConcurrenciaCupoTest` de catalog (Postgres real) y se demuestra end-to-end con el script 6.2
- [x] 3.5 Cancelación de partido (ADMIN) → cancela reservas/entradas asociadas y libera cupos
  - Backend: `POST /api/sales/reservas/cancelar-por-partido/{partidoId}` → `{canceladas: n}` con
    liberación de cupo por compensación ✅ (frontend en tarea 5.2 del agente F)
  - Verificación: curl cancelar partido → reservas CANCELLED en el reporte ✅ (test 5/8)
  - Resultado: **8/8 tests en verde** ✅ 2026-10-28 · verificación conjunto: reactor completo
    `mvnw -B test` → **BUILD SUCCESS, 28/28 tests backend** (auth 9 + catalog 11 + sales 8)

## Día 3 — Angular

### Bloque 4 — Angular base + flujo público 🅿️ (agente C)
- [x] 4.1 `ng new frontend` (standalone, signals, SCSS), proxy a los 3 puertos, `environment.ts`
  - Nota: Angular **22.2.3**, `--skip-git`, tests con **Vitest** (Karma ya no existe). Proxy SIN
    pathRewrite: solo cambia el target (`/api/auth`→8081, `/api/catalog`→8082, `/api/sales`→8083)
  - Verificación: `ng build` → SUCCESS (272 kB inicial) ✅
- [x] 4.2 `auth.service` + interceptor JWT + `authGuard` + `adminGuard` + rutas
  - Verificación: `/admin` sin sesión redirige a `/login`; con USER redirige; con ADMIN entra ✅ (5 tests de guards)
- [x] 4.3 Lista de partidos con filtros por fecha y estado + detalle con localidades, precios y disponibilidad
  - Verificación: filtrar por fecha y por estado muestra resultados del seed ✅ (estado vacío, "Agotado", pipe COP)
- [x] 4.4 Flujo de compra: seleccionar localidad → cantidad → reserva → pago simulado → pantalla con
    código de entrada; 409 mostrado amigable cuando no hay cupo
  - Implementado: `core/services/sales.service.ts` (reservar/pagar/cancelar/misReservas tipado 1:1
    con el contrato), panel de 3 pasos en el detalle (cantidad → reserva → pago) con total en vivo,
    badge "Agotado", mensaje de 409 amigable vía `mensajeApi()`, refresh de disponibilidad en
    background; ruta `/mis-entradas` (protegida) con acciones contextuales (pagar pendiente,
    cancelar con confirmación) y visualización del código de entrada
  - Verificación: `ng test` → **56/56 en 11 archivos** en verde ✅ · `ng build` SUCCESS (276 kB)

### Bloque 5 — Angular admin 🅿️ (agente B)
- [x] 5.1 Login/registro (formularios reactivos + validaciones)
  - Verificación: registro + login funcionan y guardan el token ✅ (formularios con @Email,
    minLength 6, errores 401/409 con mensaje del ApiError; token en localStorage `basket_token` /
    `basket_usuario`; login con returnUrl)
- [x] 5.2 Panel admin: CRUD de partidos, edición de precios/cupos
  - El shell `/admin` protegido con children stubs ya existe (lazy loading)
  - Verificación: crear y editar un partido desde la UI, el cambio refleja en la lista pública ✅
  - Extras: cancelación con confirm() en secuencia catalog→sales (orquestación en cliente
    comentada, aviso de reintento si sales falla), gestión de localidades (crear/editar) con
    409 de cupo<vendidos vía `mensajeApi`, indicador Agotado, estados carga/vacío/error
- [x] 5.3 Reporte de ventas por partido (Native Query) en el panel admin
  - Verificación: el reporte muestra cantidad y total del seed ✅ (selector de partido,
    fila TOTALES con `computed`, vacío amigable)
  - Resultado agente F: `ng test` **42/42 en verde** (9 archivos, estable en 2 corridas) ✅ 2026-10-28

## Día 4 — Entrega
- [x] 6.1 E2E manual completo: registro → listar → filtrar → comprar → código → admin → reporte → cancelar
  - Suites A+B (flujo público y admin): **27/27** en verde; suite C (reglas de error y seguridad): **25/25**
  - Recorrido completo sin errores; 3 bugs encontrados y corregidos (→ tarea 6.7) y re-verificados en vivo
  - Verificación: recorrido sin errores; notas de cualquier bug → arreglo ✅
- [x] 6.2 Script demo de concurrencia (`infra/demo-concurrencia.mjs`): N peticiones HTTP paralelas
    sobre la última entrada; imprime éxitos/conflictos
  - Contra el stack corriendo: **1 éxito / 9 conflictos**, disponibles 1 → 0, exit code 0
  - Verificación: contra el stack corriendo imprime "1 éxito / N-1 conflictos" ✅
- [x] 6.3 README: arquitectura (diagrama Mermaid), requisitos, variables de entorno, cómo ejecutar,
    qué está implementado, **JPQL vs Native Query (justificación)**, **estrategia de concurrencia +
    porqué frente a optimista/pesimista**, cómo correr las pruebas
  - `README.md` en la raíz (~466 líneas): diagrama de arquitectura + diagrama de secuencia de compra
    (Mermaid), JPQL vs Native con justificación, concurrencia (UPDATE atómico + CHECK + UNIQUE) frente
    a optimista/pesimista, requisitos, env vars, ejecución (dev + compose full), seeds, Swagger, layout
  - Verificación: alguien puede clonar y levantar siguiendo solo el README ✅
- [x] 6.4 Springdoc (Swagger) en los 3 servicios, enlazado en el README *(recortable)*
  - `/swagger-ui.html` responde (302) en :8081, :8082 y :8083; enlazado en el README
  - Verificación: `/swagger-ui.html` responde en los 3 ✅
- [x] 6.5 Pasada final: `./mvnw test` en los 4 módulos + `ng build` + `ng test` en verde
  - Backend `.\mvnw.cmd -B test` → **BUILD SUCCESS, 28/28** (auth 9 + catalog 11 + sales 8)
  - Frontend `ng build` SUCCESS (276 kB) · `ng test` → **56/56 en 11 archivos**
  - Verificación: todo verde, resultados anotados aquí ✅
- [x] 6.6 Repo GitHub + commit final con el README
  - Repo público: **https://github.com/10carabal/valle-oro-puro** (`gh` autenticado como 10carabal)
  - Historial reorganizado en 12 ramas temáticas con PRs a `main` (merge commit), mensajes en inglés
  - Verificación: enlace del repo responde y clona limpio ✅

### Bloque 6.7 — Correcciones halladas en el E2E (verificadas en vivo) ✅
- [x] 6.7.1 **403 llegaba como 401**: `SecurityConfig` de sales no tenía `accessDeniedHandler`; el
    `sendError` por defecto re-despacha a `/error` sin autenticación → ahora devuelve `ApiError` 403
    directo (mismo patrón que ya tenía catalog)
- [x] 6.7.2 **500 en payload ilegible**: `HttpMessageNotReadableException` (JSON malformado / fecha no
    ISO) → ahora mapea a **400**
- [x] 6.7.3 **500 en ruta inexistente**: `NoResourceFoundException` → ahora mapea a **404**
- [x] 6.7.4 **500 silente**: el handler genérico ahora hace `log.error` con el stack completo (Spring
    no loguea las excepciones capturadas por `@ExceptionHandler`)
  - Verificación en vivo: reporte con USER → 403; JSON inválido → 400; ruta inexistente con token → 404;
    backend sigue en **28/28** ✅

---

## Registro de resultados

### Cierre del día 1 — 2026-10-28

- **Backend completo y verificado en conjunto**: `.\mvnw.cmd -B test` → BUILD SUCCESS, **28/28
  tests** (auth 9 + catalog 11 + sales 8, todos con Testcontainers sobre PostgreSQL 16 real).
- **Frontend (pre-4.4)**: `npx ng build` SUCCESS · `npx ng test --watch=false` → **42/42** en verde.
- Commits en `main`: afad912, 3950bcc, 63220d5, 2e02ce6, c44bcc9, ec4ba51, 68b8cc9, 4604b25, 2143e59.
- Postgres del compose corriendo en **5433** (contenedor `boleteria-postgres`, healthy). NO tocar
  `veni-postgres`/`veni-mailpit` (5432/8025, proyecto ajeno en marcha).
- `gh` autenticado como **10carabal** (listo para 6.6).
- **Agentes E (4.4) y G (6.3) cancelados al cerrar el día sin producir nada** (árbol verificado
  limpio: sin `README.md`, sin `mis-entradas/`, sin `sales.service.ts`); relanzar mañana.
- **Pendientes al retomar**: relanzar E (4.4 compra) y G (6.3 README), 6.1 E2E manual, 6.2 script
  `infra/demo-concurrencia.mjs`, 6.4 Springdoc, 6.5 gates finales (`mvnw -B test` + `ng build` +
  `ng test`), 6.6 repo GitHub. Colchón: quedan 3 días y solo queda integración + entrega.

### Cierre del día 4 (entrega) — 2026-10-31

- **4.4 compra completada**: flujo reserva → pago simulado → código de entrada, más `/mis-entradas`;
  `ng test` sube de 42/42 a **56/56 en 11 archivos**.
- **6.1 E2E manual**: suites A+B **27/27**, suite C **25/25**. Encontró 3 bugs (403→401, 500→400,
  500→404) corregidos en `common`/`sales` y **re-verificados en vivo** (bloque 6.7).
- **6.2 demo de concurrencia en vivo**: `node infra/demo-concurrencia.mjs 10` → **1 éxito / 9
  conflictos**, disponibles 1 → 0, exit 0 (garantía de la última entrada, visible end-to-end).
- **6.3 README** (~466 líneas) con diagramas Mermaid, JPQL vs Native y estrategia de concurrencia.
- **6.4 Swagger** responde (302) en los 3 servicios y queda enlazado en el README.
- **6.5 gates finales en verde**: backend `mvnw -B test` → **28/28** · frontend `ng build` SUCCESS +
  `ng test` → **56/56**.
- **6.6 repo publicado**: **https://github.com/10carabal/valle-oro-puro** (público). Historial
  reorganizado en **12 ramas temáticas con PRs a `main`** (merge commit, mensajes en inglés):
  `chore/scaffold`, `feat/auth-service`, `feat/frontend`, `feat/catalog-service`,
  `feat/sales-service`, `feat/admin-panel`, `docs/plan-tracking`, `fix/api-error-semantics`,
  `feat/purchase-flow`, `chore/concurrency-demo`, `docs/readme`, `docs/day-4-closeout`.
- **Prueba de integridad**: `git diff main wip/day4-pending` vacío → `main` replica exactamente el
  estado final de la prueba.

### Extra — Valor agregado frontend (2026-10-31)

Fuera del alcance original (los 6 bloques ya estaban cerrados); se suma como diferencial de diseño.

- [x] Skill **`frontend-design`** (anthropics) instalado y aplicado al rediseño.
- [x] Identidad deportiva **rojo/blanco** del Valle del Cauca con sistema de tokens en `styles.scss`
  (rojo `#C8102E`, tinta cálida, grises y semánticos; tipografía **Barlow Condensed + Barlow** con
  fallback a fuentes del sistema si no hay red).
- [x] Los **7 tips de Refactoring UI** aplicados: jerarquía por peso/color (no tamaño), blanco con
  opacidad sobre rojo (nunca gris sobre color), sombras con offset vertical, tablas/tarjetas sin
  bordes, iconos pequeños dentro de shapes, bordes de acento rojos (franja del header, alertas,
  nav activa) y jerarquía de botones (sólido / outline / texto; destructivos como texto).
- [x] **Angular Material 22 (MIT)** en las pantallas de acceso (`mat-form-field`, `mat-flat-button`)
  con tema M3 derivado de la paleta roja (`mat.theme` + `mat.$red-palette`).
- [x] Pantallas rediseñadas: shell, lista (bloque de fecha tipo talonario), detalle + compra,
  mis entradas (tickets), login/registro y panel admin.
- [x] Contratos de pruebas preservados: `ng build` **SUCCESS** (304 kB inicial) · `ng test` **56/56**.
- Nota de decisión: **PrimeNG 22 descartado** — desde su v22 exige licencia PrimeUI (banner
  "Invalid PrimeUI License" sin clave y prohibición de publicar la clave en repos públicos); la
  última versión MIT (21) no declara soporte para Angular 22.
