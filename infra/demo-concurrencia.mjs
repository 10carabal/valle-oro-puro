#!/usr/bin/env node
/**
 * infra/demo-concurrencia.mjs — TAREA 6.2 (agente H)
 *
 * Demuestra end-to-end, contra el stack corriendo, el caso crítico de
 * concurrencia (la ÚLTIMA entrada): lanza N POST /api/sales/reservas en
 * paralelo sobre la localidad con menos cupo y comprueba que exactamente UNA
 * compra gana (201) y las N-1 restantes reciben 409. La no sobreventa la
 * garantiza el UPDATE atómico de catalog; aquí SOLO se demuestra por HTTP.
 *
 * Uso:  node infra/demo-concurrencia.mjs [N]   (N por defecto 10, entero >= 2)
 * Env:  AUTH_URL · CATALOG_URL · SALES_URL · DEMO_EMAIL · DEMO_PASSWORD
 * Cero dependencias: fetch nativo de Node (>= 18, probado con Node 24).
 */

// ── Configuración por variables de entorno con defaults locales ──────────────
const AUTH_URL = process.env.AUTH_URL ?? 'http://localhost:8081';
const CATALOG_URL = process.env.CATALOG_URL ?? 'http://localhost:8082';
const SALES_URL = process.env.SALES_URL ?? 'http://localhost:8083';
const EMAIL = process.env.DEMO_EMAIL ?? 'usuario@valle.test';
const PASSWORD = process.env.DEMO_PASSWORD ?? 'usuario123';

const SUGERENCIA = '¿Está el stack corriendo? (ver README: compose + 3 servicios)';
const USAGE = 'Uso: node infra/demo-concurrencia.mjs [N]  (entero >= 2; por defecto 10)';

// ── Utilidades ───────────────────────────────────────────────────────────────
/** Mensajes de fallo claros + exit 1. */
function fallar(...mensajes) {
  for (const m of mensajes) console.error(m);
  process.exit(1);
}

/** Lee N de la línea de comandos: por defecto 10; inválido → usage + exit 1. */
function leerN() {
  const arg = process.argv[2];
  if (arg === undefined) return 10;
  const n = Number(arg);
  if (!Number.isInteger(n) || n < 2) fallar(`❌ Argumento inválido: "${arg}".`, USAGE);
  return n;
}

/** fetch con timeout que devuelve {status, texto, datos}; lanza SOLO en fallo de red. */
async function pedir(url, opciones = {}) {
  const res = await fetch(url, { ...opciones, signal: AbortSignal.timeout(15_000) });
  const texto = await res.text();
  let datos = null;
  try {
    datos = texto ? JSON.parse(texto) : null;
  } catch {
    /* cuerpo no JSON: se conserva en texto */
  }
  return { status: res.status, texto, datos };
}

// ── Pasos del demo ───────────────────────────────────────────────────────────
/** (a) Login en auth → token JWT. */
async function login() {
  try {
    const r = await pedir(`${AUTH_URL}/api/auth/login`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ email: EMAIL, password: PASSWORD }),
    });
    if (r.status !== 200 || !r.datos?.token) {
      fallar(`❌ Login rechazado (HTTP ${r.status}) con ${EMAIL}.`, `   ${SUGERENCIA}`);
    }
    console.log(`✔ Login OK — ${r.datos.email} (rol ${r.datos.rol}).`);
    return r.datos.token;
  } catch (e) {
    fallar(`❌ No se pudo contactar con auth en ${AUTH_URL}: ${e.message}`, `   ${SUGERENCIA}`);
  }
}

/** (b) Localidad con MENOR disponibles > 0 entre partidos NO cancelados ("la última entrada"). */
async function elegirUltimaEntrada() {
  let partidos;
  try {
    const r = await pedir(`${CATALOG_URL}/api/catalog/partidos`);
    if (r.status !== 200) throw new Error(`HTTP ${r.status}`);
    partidos = r.datos;
  } catch (e) {
    fallar(`❌ No se pudo leer el catálogo (${CATALOG_URL}/api/catalog/partidos): ${e.message}`, `   ${SUGERENCIA}`);
  }
  // Aplanamos las localidades conservando el partido al que pertenecen.
  const candidatas = (Array.isArray(partidos) ? partidos : [])
    .filter((p) => p.estado !== 'CANCELADO')
    .flatMap((p) => (p.localidades ?? []).map((l) => ({ ...l, partido: p })))
    .filter((l) => l.disponibles > 0);

  if (candidatas.length === 0) {
    fallar(
      '❌ Ninguna localidad de partidos no cancelados tiene cupo disponible.',
      '   Libera cupo (o regenera el seed) y vuelve a intentarlo.',
    );
  }
  // Elegimos la de menor disponibilidad: esa es "la última entrada".
  return candidatas.reduce((m, l) => (l.disponibles < m.disponibles ? l : m));
}

/** (c) Barrera real: preparamos las N peticiones y las lanzamos en un SOLO bucle síncrono. */
async function dispararEnParalelo(token, localidadId, n) {
  const lanzadas = [];
  for (let i = 0; i < n; i++) {
    // Cada fetch arranca la petición al invocarse; al no haber await entre
    // llamadas, las N salen casi simultáneas (barrera de paralelismo).
    lanzadas.push(
      pedir(`${SALES_URL}/api/sales/reservas`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token}` },
        body: JSON.stringify({ localidadId, cantidad: 1 }),
      }).then(
        (r) => ({ i: i + 1, status: r.status, detalle: r.texto }),
        (e) => ({ i: i + 1, status: 0, detalle: `error de red: ${e.message}` }),
      ),
    );
  }
  return Promise.all(lanzadas);
}

/** (e) Disponibilidad final vía endpoint público de localidad. */
async function disponibilidadFinal(localidadId) {
  try {
    const r = await pedir(`${CATALOG_URL}/api/catalog/localidades/${localidadId}`);
    if (r.status !== 200) throw new Error(`HTTP ${r.status}`);
    const d = r.datos;
    // LocalidadInfo expone cupo+vendidos (no trae "disponibles"): lo calculamos.
    return d.disponibles ?? d.cupo - d.vendidos;
  } catch (e) {
    fallar(`❌ No se pudo consultar la localidad ${localidadId} en catalog: ${e.message}`, `   ${SUGERENCIA}`);
  }
}

// ── Programa principal ───────────────────────────────────────────────────────
async function main() {
  const n = leerN();
  console.log(`\n=== Demo de concurrencia — ${n} peticiones sobre la ÚLTIMA entrada ===\n`);

  const token = await login(); // (a)
  const l = await elegirUltimaEntrada(); // (b)
  console.log(
    `Localidad elegida: id=${l.id} (${l.nombre} · ${l.partido.rival} ${l.partido.fecha}) — disponibles al inicio: ${l.disponibles}`,
  );

  console.log(`\n⏳ Disparando ${n} POST /api/sales/reservas en paralelo…\n`);
  const respuestas = await dispararEnParalelo(token, l.id, n); // (c)

  // (d) Conteo por status.
  const exitos = respuestas.filter((r) => r.status === 201).length;
  const conflictos = respuestas.filter((r) => r.status === 409).length;
  const otros = respuestas.filter((r) => r.status !== 201 && r.status !== 409);

  if (otros.length > 0) {
    console.error(`⚠️ ${otros.length} respuesta(s) inesperada(s) para diagnóstico:`);
    for (const r of otros) {
      console.error(`   - petición #${r.i} · status=${r.status} · ${r.detalle.slice(0, 300)}`);
    }
    console.error('');
  }

  const finales = await disponibilidadFinal(l.id); // (e)

  // (f) Resumen final.
  console.log(
    `Peticiones: ${n}  ·  Éxitos (201): ${exitos}  ·  Conflictos (409): ${conflictos}  ·  Otros: ${otros.length}`,
  );
  console.log(`Disponibles al final: ${finales}`);

  if (exitos === 1 && conflictos === n - 1) {
    console.log('✅ Caso crítico verificado: exactamente UNA compra ganó la última entrada.');
    process.exitCode = 0;
  } else {
    console.error(`⚠️ RESULTADO INESPERADO: se esperaba 1 éxito y ${n - 1} conflictos (otros: ${otros.length}).`);
    process.exitCode = 1;
  }
}

main().catch((e) => fallar(`❌ Error inesperado: ${e.message}`));
