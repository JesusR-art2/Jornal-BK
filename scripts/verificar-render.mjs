// Verifica un despliegue tipo Render (sin gateway): salud, CORS hacia el frontend y el flujo de vacantes
// llamando directamente al core y a notificaciones, como lo hará el frontend.
//
//   CORE_URL=https://<core>.onrender.com NOTIFICATIONS_URL=https://<notifications>.onrender.com \
//   ORIGIN=https://<frontend>.onrender.com node scripts/verificar-render.mjs
//
// Por defecto apunta a la simulación local (infra/docker-compose.render.yml). Crea cuentas de prueba nuevas.
// Si PSQL_CONTAINER está definido (simulación local), adelanta la hora de fin y comprueba el pago automático.
import { execSync } from 'node:child_process';

const CORE = process.env.CORE_URL ?? 'http://localhost:18080';
const NOTI = process.env.NOTIFICATIONS_URL ?? 'http://localhost:18083';
const ORIGIN = process.env.ORIGIN ?? 'https://jornal-frontend.onrender.com';
const PSQL = process.env.PSQL_CONTAINER;   // por ejemplo jornal-render-db-1
const wait = (ms) => new Promise((r) => setTimeout(r, ms));
let failures = 0;
const check = (ok, text) => { console.log(`${ok ? 'OK  ' : 'FALLA'} ${text}`); if (!ok) failures++; };

async function call(base, path, { token, method = 'GET', body } = {}) {
  const res = await fetch(base + path, {
    method,
    headers: { 'Content-Type': 'application/json', 'Accept-Language': 'es-CO', Origin: ORIGIN,
      ...(token ? { Authorization: `Bearer ${token}` } : {}) },
    body: body ? JSON.stringify(body) : undefined,
  });
  const text = await res.text();
  return { status: res.status, data: text ? JSON.parse(text) : null, headers: res.headers };
}

async function preflight(base, path) {
  const res = await fetch(base + path, { method: 'OPTIONS', headers: {
    Origin: ORIGIN, 'Access-Control-Request-Method': 'POST',
    'Access-Control-Request-Headers': 'authorization,content-type,accept-language,x-correlation-id' } });
  return res.headers.get('access-control-allow-origin') === ORIGIN;
}

async function account(rol) {
  const email = `render-${rol.toLowerCase()}-${Date.now()}@example.test`;
  const reg = await call(CORE, '/auth/register', { method: 'POST', body: {
    email, password: 'Clave12345', givenName: 'Prueba', familyName: 'Render', rol, acceptedTerms: true } });
  if (reg.status !== 201) throw new Error(`registro ${rol}: ${reg.status} ${JSON.stringify(reg.data)}`);
  const login = await call(CORE, '/auth/login', { method: 'POST', body: { email, password: 'Clave12345' } });
  return { id: reg.data.id, token: login.data.accessToken };
}

// 1. Salud y CORS
check((await call(CORE, '/actuator/health')).data?.status === 'UP', `core sano (${CORE})`);
check((await call(NOTI, '/actuator/health')).data?.status === 'UP', `notificaciones sano (${NOTI})`);
check(await preflight(CORE, '/vacancies'), `CORS del core permite ${ORIGIN}`);
check(await preflight(NOTI, '/notifications/me'), `CORS de notificaciones permite ${ORIGIN}`);

// 2. Flujo de vacantes
const employer = await account('APPLICANT');
const worker = await account('WORKER');
const cats = (await call(CORE, '/categories?q=warehouse', { token: worker.token })).data;
await call(CORE, '/workers/me', { token: worker.token, method: 'PUT', body: {
  bio: 'Bodega', workRadiusKm: 10, categoryIds: [cats[0].id], baseLatitude: 4.65, baseLongitude: -74.08 } });
await call(CORE, '/vacancies/settings', { token: employer.token, method: 'PUT', body: { autoPayout: true } });
const day = new Intl.DateTimeFormat('en-CA', { timeZone: 'America/Bogota' }).format(new Date(Date.now() + 2 * 86_400_000));
const vac = await call(CORE, '/vacancies', { token: employer.token, method: 'POST', body: {
  title: 'Auxiliar de bodega', description: 'Cargar y descargar el camión', categoryId: cats[0].id, items: ['Guantes'],
  startLocal: `${day}T08:00`, endLocal: `${day}T16:00`, timezone: 'America/Bogota', positions: 1, payMinor: 120000,
  address: { street_address: 'Calle 26 # 68-35', city: 'Bogotá', department: 'Bogotá D.C.' }, latitude: 4.6584, longitude: -74.0937 } });
check(vac.status === 201, 'publicar vacante');
const app = await call(CORE, `/vacancies/${vac.data.id}/apply`, { token: worker.token, method: 'POST', body: {} });
check(app.status === 201, 'postularse');
check((await call(CORE, `/vacancies/${vac.data.id}/selections`, { token: employer.token, method: 'POST',
  body: { applicationIds: [app.data.id] } })).status === 200, 'contratar');
check((await call(CORE, `/vacancies/${vac.data.id}/attendance`, { token: worker.token, method: 'POST',
  body: { attending: true } })).data?.status === 'CONFIRMED', 'confirmar asistencia');

// 3. Avisos (RabbitMQ -> notificaciones -> consulta del contacto al core)
let inbox = [];
for (let i = 0; i < 30 && !inbox.some((n) => n.action === 'CONFIRM_ATTENDANCE'); i++) {
  await wait(1000);
  inbox = (await call(NOTI, '/notifications/me?limit=10', { token: worker.token })).data ?? [];
}
check(inbox.some((n) => n.vacancyId === vac.data.id && n.template === 'vacancy.published'), 'aviso de vacante cercana');
check(inbox.some((n) => n.action === 'CONFIRM_ATTENDANCE'), 'aviso de contratación con botón de asistencia');

// 4. Pago automático (solo en la simulación local: se adelanta la hora de fin en la base)
if (PSQL) {
  execSync(`docker exec ${PSQL} psql -U render_user -d jornal -c "UPDATE core_co.vacancies SET starts_at = now() - interval '9 hours', ends_at = now() - interval '1 hour' WHERE id = '${vac.data.id}'"`);
  const done = await call(CORE, `/vacancies/${vac.data.id}/applications/${app.data.id}/complete`, { token: employer.token, method: 'POST', body: {} });
  check(done.data?.payoutStatus === 'PROCESSING', 'finalizar dispara el pago automático');
  let status;
  for (let i = 0; i < 30 && status !== 'PAID'; i++) {
    await wait(1000);
    status = (await call(CORE, '/vacancies/applications/mine', { token: worker.token })).data.items.find((a) => a.id === app.data.id)?.payoutStatus;
  }
  check(status === 'PAID', 'el subsistema de pagos pagó al trabajador');
}

console.log(failures ? `\n${failures} comprobación(es) fallaron` : '\nTodo correcto');
process.exit(failures ? 1 : 0);
