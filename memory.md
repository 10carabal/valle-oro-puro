# memory.md — Lecciones aprendidas y contexto del proyecto

**Cómo usar este archivo:** todo agente que trabaje en este repo DEBE leerlo antes de empezar.
Aquí vive el contexto compartido del equipo (entorno, decisiones ya cerradas, trampas conocidas).
Al terminar cada tarea, añade aquí cualquier lección nueva en "Lecciones aprendidas" (append,
nada de reescribir lo que ya está). Última actualización: 2026-10-31.

---

## 1. Qué estamos construyendo

Prueba técnica CODESA: sistema de boletería de baloncesto (Valle del Cauca). Plazo: 4 días.
Ver `PLAN.md` para tareas/estado y la sección "Lo intocable" para prioridades.

- 3 microservicios Spring Boot (auth :8081, catalog :8082, sales :8083) + Angular (:4200)
- PostgreSQL 16 con 3 esquemas (auth, catalog, sales) en UN contenedor
- JPQL parametrizada (catalog) + Native Query parametrizada (sales)
- JWT HS256 con secreto compartido, roles USER/ADMIN validados en CADA servicio
- Caso crítico: UPDATE atómico condicional contra sobreventa, con tests multihilo

## 2. Decisiones CERRADAS (no reabrir en discusiones)

| Decisión | Por qué |
|---|---|
| Sin API Gateway | No es requisito; Angular con proxy → 3 puertos; cada servicio valida su JWT |
| Inventario (cupo/vendidos) vive en `catalog` | Dueño de la localidad; el listado público muestra disponibilidad sin llamadas cruzadas; el admin edita cupo en un solo servicio |
| Concurrencia: `UPDATE ... WHERE vendidos + :qty <= cupo` + `CHECK` en BD | Correcto, sin locks largos; alternativas (optimista/pesimista) solo se EXPLICAN en el README |
| `RestClient` sales → catalog (sin Spring Cloud/Feign) | Viene en Spring Boot 3, cero dependencias extra |
| JWT propagado: sales reenvía la cabecera Authorization del usuario a catalog | La autorización se valida en el backend de cada servicio, sin tokens de servicio |
| Naming en español (partidos, localidades, reservas, entradas) | El documento de la prueba está en español; `match` es palabra SQL |
| Cancelar partido = lógico (`estado CANCELADO`), nunca DELETE | Permite reportar ventas históricas |
| Datos de ejemplo vía migraciones Flyway V2 de cada servicio | Requisito de "migraciones + datos de ejemplo" y siempre disponibles |
| Seed de fechas con `CURRENT_DATE + INTERVAL` | El demo sirve siempre sin regenerar datos |

## 3. Contratos API (los definen los dueños; otros agentes SOLO consumen)

**auth-service (:8081) — dueño: agente A**
- POST `/api/auth/registro` {email,password,nombre} → 201 {email,nombre,rol} · 409 email duplicado
- POST `/api/auth/login` {email,password} → 200 {token,email,rol,nombre} · 401 credenciales
- GET `/api/auth/yo` (Bearer) → {email,rol} · 401 sin token
- Seed: `admin@valle.test`/`admin123` (ADMIN), `usuario@valle.test`/`usuario123` (USER)

**catalog-service (:8082) — dueño: agente B**
- GET `/api/catalog/partidos?fecha=&estado=` público → partidos con localidades `{id,nombre,precio,cupo,vendidos,disponibles}`
- GET `/api/catalog/partidos/{id}` público · GET `/api/catalog/localidades/{id}` público → `LocalidadInfo`
- POST/PUT `/api/catalog/partidos...` ADMIN (`hasRole`) · PUT `.../cancelar` ADMIN
- POST `/api/catalog/localidades/{id}/reservar` authenticated → `CupoResponse` o **409 sin cupo**
- POST `/api/catalog/localidades/{id}/liberar` authenticated → `CupoResponse`

**sales-service (:8083) — dueño: agente D (en implementación), contrato fijado por el coordinador**
- POST `/api/sales/reservas` {localidadId, cantidad} autenticada → 201 {reservaId, estado, localidadId,
  cantidad, total, partidoId, rival} · **409** si catalog no tiene cupo
- POST `/api/sales/reservas/{id}/pagar` (dueño) → CONFIRMED + `entrada:{codigo UUID, emitidaEn}` ·
  403 otro usuario · 409 si no está PENDING
- POST `/api/sales/reservas/{id}/cancelar` (dueño/ADMIN) → CANCELLED + libera cupo en catalog
- POST `/api/sales/reservas/cancelar-por-partido/{partidoId}` **ADMIN** → `{canceladas: n}`
- GET `/api/sales/reservas` (autenticada) → reservas del usuario con su entrada
- GET `/api/sales/reportes/ventas?partidoId=` **ADMIN** → filas
  `{partidoId, rival, localidadId, cantidadVendida, totalRecaudado}` (Native Query, solo CONFIRMED)

**Common (`backend/common`) — quien lo modifique, avisa al equipo ANTES**
- `error.ApiError` + `error.GlobalExceptionHandler` (404/409/400/401/403/500 en JSON uniforme)
- `error.NotFoundException`, `error.ConflictException`
- `security.JwtService` (generar/validar, claim "rol", 8h), `security.JwtAuthenticationFilter`
- `dto.ReservarCupoRequest`, `dto.CupoResponse`, `dto.LocalidadInfo`

## 4. Entorno de esta máquina (trampas conocidas)

- **JDK 21** en `C:\Program Files\Microsoft\jdk-21.0.13.21-hotspot` (instalado con winget el
  2026-10-28). Los shells en curso NO lo ven: refrescar SIEMPRE antes de compilar:
  ```powershell
  $env:JAVA_HOME='C:\Program Files\Microsoft\jdk-21.0.13.21-hotspot'
  $env:Path="$env:JAVA_HOME\bin;$env:Path"
  ```
- **Maven**: no está instalado como sistema; usar el wrapper `.\mvnw.cmd -B ...` desde la raíz.
  Maven local solo para emergencias: `Downloads\Dev\tools\apache-maven-3.9.16\bin`.
- **PowerShell parte argumentos `-Dx=y` mal** (ej. `-Dmaven=3.9.16` llega cortado): usar comillas
  `"-Dmaven=3.9.16"` o escribir la propiedad a mano en el archivo.
- **Docker Desktop NO arranca con el sistema** (hay que abrirlo). Los tests de integración usan
  Testcontainers → verificar `docker info` antes; si el daemon tarda, esperar (~5 min la 1ª vez
  por la descarga de `postgres:16-alpine` y ryuk).
- **Puertos Docker**: el 5432 lo ocupa `veni-postgres` (otro proyecto del usuario, EN MARCHA —
  no tocar ni parar). Nuestro Postgres corre en **`boleteria-postgres` con host 5433** (interno
  5432); los `application.yml` usan por defecto `jdbc:postgresql://localhost:5433/boleteria`.
- **No hay Python** en esta máquina (el `python` del store es un alias molesto). Para PDFs:
  `npx -y pdf-parse text <archivo.pdf>` funciona.
- **Fallos DNS transitorios** existen (una vez falló `repo1.maven.org`): reintentar, no asumir
  que no hay red.
- **Dos Maven a la vez** en este repo Windows pueden pelear por archivos de `.m2`: si aparece un
  error de archivo bloqueado, esperar 30s y reintentar.

## 5. Versiones exactas (no adivinar)

- Spring Boot **3.5.16** (parent) · Java **21** · jjwt BOM **0.12.7** (¡0.12.6 NO existe en Central!)
- springdoc **2.8.13** · Flyway con `flyway-database-postgresql` · Testcontainers BOM 1.21.x (Boot)
- PostgreSQL **16-alpine** · Angular **22.2.3** (standalone + signals, `ng new --skip-git
  --style=scss --ssr=false`, tests con **Vitest**: `npx ng test --watch=false`; Karma ya no existe)
- **Trampa Jackson (resuelta):** el pom padre importa `jackson-bom` ANTES que `jjwt-bom` porque
  jjwt-bom arrastra `jackson-databind:2.12.7.1` de jjwt-root y rompe el arranque con
  `NoClassDefFoundError: ToEmptyObjectSerializer`. No quitar ni reordenar ese import.

## 6. Reglas del equipo (agentes)

1. Leer `PLAN.md` y este archivo antes de empezar.
2. Cada agente escribe SOLO en su módulo/carpetas asignadas; nada de `git` (los commits los hace
   el coordinador) ni de tocar `PLAN.md` o poms ajenos.
3. Antes de declarar una tarea hecha: correr SU verificación del PLAN y dejar los resultados en
   la respuesta (tests en verde, comprobación manual, etc.).
4. Toda decisión nueva que cambie un contrato o una decisión cerrada de la sección 2 → se
   discute con el coordinador y se registra aquí.
5. Lecciones de entorno o del código van a la sección 7 (append).

## 7. Lecciones aprendidas (append, orden cronológico)

- 2026-10-28: el `mvnw` generado con `distributionType=only-script` quedó con la URL de
  distribución rota por el corte de PowerShell; se corrigió a mano en
  `.mvn/wrapper/maven-wrapper.properties`.
- 2026-10-28: los módulos sin `src` compilaban igual ("No sources to compile"); el primer BUILD
  SUCCESS no prueba que las dependencias resuelvan — la prueba real es compilar con código fuente.
- 2026-10-28: `Select-String` con rutas posicionales revienta por parseo de regex; usar siempre
  `-Path <archivo> -Pattern <patrón>`.
- 2026-10-28 (agente A): `jjwt-bom` en el `dependencyManagement` del padre hereda de `jjwt-root`
  `jackson-databind:2.12.7.1`; al arrancar cualquier servicio con `starter-web` aparece
  `NoClassDefFoundError: com/fasterxml/jackson/databind/ser/std/ToEmptyObjectSerializer`.
  Fix centralizado en el pom padre: importar `com.fasterxml.jackson:jackson-bom:${jackson-bom.version}`
  ANTES que `jjwt-bom` (el primer BOM importado gana). Los servicios no necesitan nada propio.
- 2026-10-28 (agente A): emails normalizados a minúsculas antes de persistir para que el
  `UNIQUE(email)` no falle con variaciones de mayúsculas. Usuarios registrados por API nacen
  siempre `USER` (el ADMIN solo viene del seed).
- 2026-10-28 (agente A): generar hashes BCrypt de ejemplo con un test temporal que los imprima,
  pegarlos en `V2__seed` y BORRAR el test (no dejar utilidades de un solo uso en el repo).
- 2026-10-28 (agente C): convenciones del frontend ya establecidas — proxy **sin pathRewrite**
  (solo cambia target; los servicios exponen `/api/<servicio>/...`), token en `localStorage`
  (`basket_token`, `basket_usuario`), helper `mensajeApi()` de `core/models/api-error.ts` para
  leer el `message` del `ApiError`, pipe `cop` para formatear COP, chips de estado en `shared/`.
  El shell `/admin` protegido tiene children stubs listos (`admin/partidos`, `admin/reportes`) y
  el detalle de partido deja el botón de compra deshabilitado con comentario `tarea 4.4`.
  Quien implemente 4.4/5.x reutiliza ESOS archivos y no crea estructura paralela.
- 2026-10-28 (agente C): Angular 22 trae **Vitest** como runner por defecto; los comandos de
  verificación del frontend son `npx ng build` y `npx ng test --watch=false` (sin Karma).
- 2026-10-28 (agente B): builds Maven CONCURRENTES del mismo módulo se pisan (`target/classes`
  compartido y migraciones Flyway "ausentes" en el primer contexto). Si un test falla de forma
  rara estando otro agente compilando, RE-EJECUTAR antes de investigar: puede ser solo el solape.
- 2026-10-28 (agente B): en Hibernate 6.6 + PostgreSQL, un parámetro `LocalDate` NULO en JPQL
  falla con `42P18 could not determine data type` salvo que se declare el tipo:
  `CAST(:fecha AS java.time.LocalDate)`. El parámetro de enum NO necesita cast.
- 2026-10-28 (agente D): en Spring 6.2, `MockRestRequestMatchers.jsonPath(...)` devuelve un
  matcher encadenable: `.andExpect(jsonPath("$.x").value(1))` — pasar el matcher directo a
  `.andExpect(...)` no compila. Ojo con el choque de import estático `jsonPath` entre MockMvc y
  MockRest.
- 2026-10-28 (agente D): un `mvnw test` que falla en `testCompile` deja clases stale en
  `target/test-classes` que la siguiente ejecución reutiliza ("Nothing to compile"); ante tests
  incoherentes con el código actual, borrar `backend/<módulo>/target` y reintentar.
- 2026-10-28 (agente D): en tests que comparten BD dentro de una clase, aislar los datos por
  `usuario_email`/partido propio (o `deleteAll` al inicio) en vez de aserciones globales con `findAll()`.
- 2026-10-28 (agente D): persistir la reserva `REJECTED` requiere transacción `REQUIRES_NEW`
  (el rollback del 409 borraría el registro); por eso el pool Hikari sube a 40 SOLO en el test
  multihilo de sales (cada hilo perdedor usa 2 conexiones).
- 2026-10-28: `docker compose up` puede reventar con "port is already allocated" si otro proyecto
  usa el puerto (aquí `veni-postgres` en 5432). Solución aplicada: mapear nuestro PG a **5433**
  en `infra/docker-compose.yml` y en los defaults `DB_URL` de los 3 servicios. No parar proyectos ajenos.
- 2026-10-28 (agente F): convenciones admin — `core/services/admin.service.ts` (escrituras),
  cancelación de partido en cliente secuencia `PUT /partidos/{id}/cancelar` → `POST
  /api/sales/reservas/cancelar-por-partido/{id}` con aviso de reintento si sales falla, y el 409
  de `cupo < vendidos` se muestra SIEMPRE con `mensajeApi()` (el backend es el que valida; no
  precuentear en cliente). `estado` se envía solo en PUT, nunca en POST de partido.
- 2026-10-31 (día 4): **403 llegaba como 401 en sales**. Causa raíz: `SecurityConfig` sin
  `accessDeniedHandler`; la decisión por defecto dispara `response.sendError(403)` que re-despacha a
  `/error` SIN contexto de autenticación → Spring Security acaba respondiendo 401. Fix: registrar un
  `accessDeniedHandler` que escriba `ApiError` 403 directo (patrón que ya usaba catalog). Lección:
  siempre añadir `authenticationEntryPoint` Y `accessDeniedHandler` en cada servicio.
- 2026-10-31 (día 4): **`@ExceptionHandler` silencia los 500**. Las excepciones capturadas por el
  advice NO las loguea Spring; un `catch` que solo devuelve el `ApiError` deja el error invisible en
  consola. Fix: `log.error(..., ex)` con el stack en el handler genérico. Sin eso, depurar un 500
  en producción es ciego.
- 2026-10-31 (día 4): mapear **`HttpMessageNotReadableException` → 400** (JSON malformado / fecha no
  ISO) y **`NoResourceFoundException` → 404** (ruta inexistente); sin esto ambos eran 500. El 404
  solo aplica con token válido (sin token la seguridad responde 401 antes de llegar al handler).
- 2026-10-31 (día 4): con `curl.exe` + PowerShell 5.1, el JSON con comillas/acentos por argumento
  se corrompe; siempre escribir el body a un archivo (`Set-Content -NoNewline -Encoding ASCII`) y
  pasarlo con `-d "@$file"`. Helper `req(method,url,body,auth)` redefinible por shell; tokens en
  `%TEMP%\tokU.txt`/`%TEMP%\tokA.txt` (viven 8h).
- 2026-10-31 (día 4): `mvnw package` revienta si los jars están en ejecución (archivo bloqueado en
  Windows); matar los procesos java de los 3 jars antes de empaquetar (patrón
  `CommandLine -match '<jar>'` sobre `Get-CimInstance Win32_Process`).
- 2026-10-31 (día 4): el shell de OpenCode en Windows convierte **stderr redirigido en error
  terminante** si `$ErrorActionPreference='Stop'`; para pipelines con `2>&1` largos (mvn/ng) usar
  `$ErrorActionPreference='Continue'` o evitar la redirección de stderr.
- 2026-10-31 (día 4): **PowerShell desanida arrays de 1 elemento** al pasarlos a un parámetro
  `[array]` (`@(@('sha','msg'))` → `@('sha','msg')`); un `foreach` posterior itera strings y
  `$c[0]` da el primer CARÁCTER (sha→`'6'` → `fatal: invalid reference`). Fix robusto: pasar los
  elementos como **hashtables** `@{sha='..';msg='..'}`, que nunca se desanidan (los arrays de ≥2
  elementos anidados no se desanidan, por eso el bug solo salta con 1 commit).
- 2026-10-31 (día 4): **técnica de reconstrucción de historia para PRs temáticos** sin reescribir:
  (1) guardar el estado en una rama `wip/*`; (2) `git checkout --orphan` + commit vacío como nuevo
  `main`; (3) por cada rama, `git checkout <sha-original> -- .` + `git add -A` + commit con mensaje
  en inglés; (4) para cambios aún sin commit, `git checkout wip/* -- <ruta>`. Seguro SOLO si la
  historia original no tiene borrados ni renombres (verificar con `git log --diff-filter=D/--filter=R`
  vacío); si los tiene, el `checkout -- .` deja archivos huérfanos.
- 2026-10-31 (extra frontend): **PrimeNG 22 ya NO es MIT**: desde la v22 usa licencia PrimeUI dual
  (Community/Commercial). Sin clave válida muestra un banner "Invalid PrimeUI License" sobre la app
  (dev y prod) y la clave NO puede publicarse (repo público = incumplimiento). La Community es gratis
  para individuos pero requiere registro y renovación anual. La última MIT es la 21, que no declara
  soporte para Angular 22. Para este repo se eligió **Angular Material 22** (MIT, mismos releases
  que Angular, sin claves ni banners).
- 2026-10-31 (extra frontend): tema M3 de Angular Material 22: `@use '@angular/material' as mat;` y
  `html { @include mat.theme((color: (primary: mat.$red-palette, tertiary: mat.$red-palette),
  typography: (plain-family: 'Barlow', brand-family: 'Barlow Condensed'), density: 0)); }`.
  Los tokens emitidos son `--mat-sys-*` y se sobreescriben en `:root` (ej. `--mat-sys-primary:
  #c8102e`). `@primeng/themes` ya no existe en el ecosistema Prime: es `@primeuix/themes`.
- 2026-10-31 (extra frontend): rediseño seguro = **los specs son el contrato**: conservar clases
  (`.tabla-localidades`, `.tabla-reporte`, `.agotado`, `button.boton`, `input#cantidad`), textos
  exactos ('Elegir cantidad', 'Confirmada · entrada emitida', 'TOTALES', 'Aún no tienes entradas'…)
  y `window.confirm`; con eso `ng test` sigue en 56/56 sin tocar ni un spec.
- 2026-10-31 (extra frontend): Google Fonts con fallback (`Barlow`/`Barlow Condensed` → `system-ui`)
  y sin fuente de iconos: los iconos son emoji/SVG, así que sin red la UI no muestra "iconos de
  texto" (fallback feo típico de Material Symbols/ligaduras).
- 2026-10-31 (extra frontend): `ng serve` como comando en background del shell de OpenCode: **no**
  pasarle `timeout` (por defecto no tiene); si se pasa, el server muere a los N ms aunque compile bien.
- 2026-10-31 (review): el PDF del enunciado es un **documento privado** y el repo es público: se
  quitó de HEAD (commit de borrado). Sigue en el historial (afad912 lo añadió, 50ff5b5 lo restauró
  por error); una purga real exige reescribir TODOS los SHAs + force-push y GitHub puede cachear
  copias. Lección: nunca versionar documentos privados en repos públicos; si ya está, decidir
  explícitamente entre "quitar de HEAD" (simple) y "purgar historial" (invasivo).
