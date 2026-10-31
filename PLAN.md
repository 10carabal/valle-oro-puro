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
- Puertos: auth 8081 · catalog 8082 · sales 8083 · PG 5432 · Angular 4200

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
- [ ] 0.5 Flyway por servicio (`spring.flyway.schemas` = auth/catalog/sales) + seed de datos de ejemplo
    en las migraciones de cada servicio (usuarios USER/ADMIN, 3 partidos, localidades con cupo)
  - Verificación: al arrancar cada servicio sus migraciones aplican sin error y el seed deja datos

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

### Bloque 2 — catalog-service 🅿️ (agente B)
- [ ] 2.1 Módulo `catalog-service`: entidades `partidos` y `localidades` + migraciones
    (`partidos(rival, fecha, hora, escenario, estado)`, `localidades(nombre, precio, cupo, vendidos)`,
    `CHECK vendidos <= cupo`, índices `partidos(fecha, estado)`, `localidades(partido_id)`)
  - Verificación: `./mvnw -q test` pasa; `psql` muestra tablas + índices + CHECK
- [ ] 2.2 CRUD de partidos/localidades con rol ADMIN (`POST/PUT/DELETE` → 403 sin rol ADMIN)
  - Verificación: con token USER un PUT → 403; con token ADMIN → 200
- [ ] 2.3 **JPQL parametrizada**: filtros por fecha y/o estado + disponibilidad por localidad
  - Verificación: test de repositorio con los 3 combinaciones (solo fecha, solo estado, ambas)
- [ ] 2.4 Endpoint de inventario `POST /api/catalog/localidades/{id}/reservar` (UPDATE atómico,
    0 filas → 409) y `.../liberar` (compensación). Este es el corazón del caso crítico
  - Verificación: test de integración con hilos: N llamadas sobre cupo=1 → exactamente 1 éxito

## Día 2 — sales-service + caso crítico

### Bloque 3 — sales-service (primero lo intocable)
- [ ] 3.1 Módulo `sales-service`: entidades `reservas` (con `partido_id` + `rival_snapshot` desnormalizado,
    estado PENDING/CONFIRMED/REJECTED/CANCELLED) y `entradas` (`codigo_unico UNIQUE`); migraciones + índices
    `reservas(user_id)`, `reservas(localidad_id, estado)`
  - Verificación: `./mvnw -q test` pasa; tablas e índices visibles en `psql`
- [ ] 3.2 Saga de reserva: `POST /reservas` (PENDING → llama `catalog` con `RestClient` → UPDATE atómico;
    si 409 → REJECTED/409) → `POST /reservas/{id}/pagar` (pago simulado → CONFIRMED + entrada con UUID)
    → cancelación libera cupo (compensación)
  - Verificación: flujo completo por curl: reserva → pagar → entrada con código; cancelar libera (`vendidos` baja)
- [ ] 3.3 **Native Query** de reporte: cantidad vendida y total recaudado por partido y localidad
    (`COUNT`, `SUM`, `GROUP BY` sobre `reservas`+`entradas` dentro del esquema `sales`) + endpoint admin
  - Verificación: con seed, el reporte devuelve las filas esperadas (test con assert exacto)
- [ ] 3.4 **Tests de concurrencia (Testcontainers)**: 2 hilos y 20 hilos comprando la última entrada
    vía el flujo de `sales` → exactamente `cupo` éxitos y el resto 409, `vendidos == cupo`
  - Verificación: `./mvnw test` en verde, ambas pruebas; resultados anotados aquí
- [ ] 3.5 Cancelación de partido (ADMIN) → cancela reservas/entradas asociadas y libera cupos
  - Verificación: curl cancelar partido → reservas CANCELLED en el reporte

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
- [ ] 4.4 Flujo de compra: seleccionar localidad → cantidad → reserva → pago simulado → pantalla con
    código de entrada; 409 mostrado amigable cuando no hay cupo
  - Bloqueada hasta que sales-service (bloque 3) esté commiteado; el botón en el detalle ya está
    preparado con comentario `tarea 4.4`
  - Verificación: comprar la última entrada en 2 pestañas → una confirma y la otra ve el 409
  - Resultado parcial: `ng build` SUCCESS · `ng test` 21/21 en verde ✅ 2026-10-28 (agente C)

### Bloque 5 — Angular admin 🅿️ (agente B)
- [x] 5.1 Login/registro (formularios reactivos + validaciones)
  - Verificación: registro + login funcionan y guardan el token ✅ (formularios con @Email,
    minLength 6, errores 401/409 con mensaje del ApiError; token en localStorage `basket_token` /
    `basket_usuario`; login con returnUrl)
- [ ] 5.2 Panel admin: CRUD de partidos, edición de precios/cupos
  - El shell `/admin` protegido con children stubs (`admin/partidos`, `admin/reportes`) ya existe
    (lazy loading): solo sustituir los stubs
  - Verificación: crear y editar un partido desde la UI, el cambio refleja en la lista pública
- [ ] 5.3 Reporte de ventas por partido (Native Query) en el panel admin
  - Verificación: el reporte muestra cantidad y total del seed

## Día 4 — Entrega
- [ ] 6.1 E2E manual completo: registro → listar → filtrar → comprar → código → admin → reporte → cancelar
  - Verificación: recorrido sin errores; notas de cualquier bug → arreglo
- [ ] 6.2 Script demo de concurrencia (`infra/demo-concurrencia.mjs`): N peticiones HTTP paralelas
    sobre la última entrada; imprime éxitos/conflictos
  - Verificación: contra el stack corriendo imprime "1 éxito / N-1 conflictos"
- [ ] 6.3 README: arquitectura (diagrama Mermaid), requisitos, variables de entorno, cómo ejecutar,
    qué está implementado, **JPQL vs Native Query (justificación)**, **estrategia de concurrencia +
    porqué frente a optimista/pesimista**, cómo correr las pruebas
  - Verificación: alguien puede clonar y levantar siguiendo solo el README
- [ ] 6.4 Springdoc (Swagger) en los 3 servicios, enlazado en el README *(recortable)*
  - Verificación: `/swagger-ui.html` responde en los 3
- [ ] 6.5 Pasada final: `./mvnw test` en los 4 módulos + `ng build` + `ng test` en verde
  - Verificación: todo verde, resultados anotados aquí
- [ ] 6.6 Repo GitHub + commit final con el README
  - Verificación: enlace del repo responde y clona limpio

---

## Registro de resultados

- *(ir anotando aquí resultados de verificaciones, fechas y hallazgos)*
