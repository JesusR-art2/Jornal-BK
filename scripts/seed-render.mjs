// Carga los datos de prueba en el despliegue de Render (o en cualquier core), llamando a la API (no a la base):
//   1. Cuentas de demostración: cliente@co.jornal.test, comercio@co.jornal.test (con NIT) y trabajador@co.jornal.test,
//      todas con la clave Demo12345 (DEMO_PASSWORD). Si ya existen, se dejan como están.
//   2. Trabajador verificado (con la cuenta de administrador), con categoría, ubicación base en Bogotá y disponible.
//   3. Las 8 vacantes de prueba de scripts/seed-vacantes.mjs.
//
// Uso:
//   CORE_URL=https://jornal-core-co.onrender.com ADMIN_EMAIL=... ADMIN_PASSWORD=... node scripts/seed-render.mjs
// ADMIN_EMAIL/ADMIN_PASSWORD: el administrador que se definió al crear el Blueprint. Todos los datos son ficticios.
// En el plan gratuito el primer llamado puede tardar cerca de un minuto (el servicio despierta).
import { spawnSync } from 'node:child_process';
import { fileURLToPath } from 'node:url';

const CORE = (process.env.CORE_URL ?? '').replace(/\/$/, '');
const ADMIN_EMAIL = process.env.ADMIN_EMAIL;
const ADMIN_PASSWORD = process.env.ADMIN_PASSWORD;
const PASSWORD = process.env.DEMO_PASSWORD ?? 'Demo12345';
if (!CORE || !ADMIN_EMAIL || !ADMIN_PASSWORD) {
  console.error('Faltan variables: CORE_URL, ADMIN_EMAIL y ADMIN_PASSWORD');
  process.exit(1);
}

async function call(path, { token, method = 'GET', body } = {}) {
  const res = await fetch(CORE + path, {
    method,
    headers: { 'Content-Type': 'application/json', 'Accept-Language': 'es-CO', ...(token ? { Authorization: `Bearer ${token}` } : {}) },
    body: body ? JSON.stringify(body) : undefined,
    signal: AbortSignal.timeout(120_000),
  });
  const text = await res.text();
  return { status: res.status, data: text ? JSON.parse(text) : null };
}

async function login(email, password) {
  const r = await call('/auth/login', { method: 'POST', body: { email, password } });
  if (r.status !== 200) throw new Error(`inicio de sesión de ${email}: ${r.status} ${JSON.stringify(r.data)}`);
  return r.data.accessToken;
}

const ADDRESS = { street_address: 'Calle 26 # 68-35', city: 'Bogotá', department: 'Bogotá D.C.' };
const USERS = [
  { email: 'cliente@co.jornal.test', givenName: 'Cliente', rol: 'APPLICANT', address: { label: 'Casa', fields: ADDRESS } },
  { email: 'comercio@co.jornal.test', givenName: 'Comercio', rol: 'BUSINESS',
    business: { legalName: 'Comercio Demo SAS', taxIdType: 'NIT', taxIdNumber: '900123456-8', billingAddress: { fields: ADDRESS } } },
  { email: 'trabajador@co.jornal.test', givenName: 'Trabajador', rol: 'WORKER' },
];

console.log(`Core: ${CORE} (puede tardar si el servicio estaba dormido)`);
const health = await call('/actuator/health');
if (health.data?.status !== 'UP') throw new Error(`el core no está sano: ${health.status}`);

// 1. Cuentas
for (const u of USERS) {
  const r = await call('/auth/register', { method: 'POST', body: {
    email: u.email, password: PASSWORD, givenName: u.givenName, familyName: 'Demo', rol: u.rol, phone: '300 765 4321',
    acceptedTerms: true, ...(u.address ? { address: u.address } : {}), ...(u.business ? { business: u.business } : {}) } });
  if (r.status === 201) console.log(`OK   ${u.email} registrado`);
  else if (r.status === 409) console.log(`OK   ${u.email} ya existía`);
  else throw new Error(`registro de ${u.email}: ${r.status} ${JSON.stringify(r.data)}`);
}

// 2. Trabajador listo para recibir avisos
const admin = await login(ADMIN_EMAIL, ADMIN_PASSWORD);
const worker = await login('trabajador@co.jornal.test', PASSWORD);
const me = (await call('/users/me', { token: worker })).data;
await call(`/admin/users/${me.id}/verification`, { token: admin, method: 'PATCH', body: { state: 'VERIFIED' } });
const category = (await call('/categories?q=warehouse', { token: worker })).data[0].id;
await call('/workers/me', { token: worker, method: 'PUT', body: {
  bio: 'Trabajador de demostración', workRadiusKm: 20, categoryIds: [category], baseLatitude: 4.65, baseLongitude: -74.08 } });
await call('/workers/me/availability', { token: worker, method: 'PATCH', body: { available: true } });
console.log('OK   trabajador verificado, con bodega, ubicación base en Bogotá y disponible');

// 3. Vacantes de prueba
console.log('\nVacantes:');
const seed = fileURLToPath(new URL('./seed-vacantes.mjs', import.meta.url));
const run = spawnSync(process.execPath, [seed], { stdio: 'inherit', env: { ...process.env, API_URL: CORE, DEMO_PASSWORD: PASSWORD } });
process.exit(run.status ?? 1);
