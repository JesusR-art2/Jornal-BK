// Carga los datos de prueba en el despliegue de Render (o en cualquier core), llamando a la API (no a la base).
// En cada región:
//   1. Cuentas de demostración: cliente@<región>.jornal.test, comercio@<región>.jornal.test (con identificación
//      fiscal válida para el país) y trabajador@<región>.jornal.test, con la clave Demo12345 (DEMO_PASSWORD).
//      Si ya existen, se dejan como están.
//   2. Trabajador verificado (con la cuenta de administrador), con categoría de bodega, ubicación base y disponible.
//   3. Vacantes de prueba: en Colombia las 8 de scripts/seed-vacantes.mjs; en las demás, 3 en el idioma del país.
//      Cada corrida publica vacantes nuevas.
//
// Uso:
//   ADMIN_EMAIL=... ADMIN_PASSWORD=... node scripts/seed-render.mjs
//
// Variables:
//   CORE_URL        URL del core; {region} se reemplaza por co, br... (por defecto https://jornal-core-{region}.onrender.com).
//                   Sin {region} es un solo core y solo se carga Colombia (o la región de REGIONS).
//   REGIONS         regiones a cargar, separadas por coma (por defecto co,br,us,fr,jp,de). Las que no respondan se saltan.
//   ADMIN_EMAIL     administrador inicial (el del Blueprint, el mismo en todas las regiones). Admite {region}.
//   ADMIN_PASSWORD  su contraseña.
//   DEMO_PASSWORD   clave de las cuentas de demostración (Demo12345).
// Todos los datos son ficticios. En el plan gratuito el primer llamado a cada core puede tardar cerca de un minuto.
import { spawnSync } from 'node:child_process';
import { fileURLToPath } from 'node:url';

const CORE_TEMPLATE = (process.env.CORE_URL ?? 'https://jornal-core-{region}.onrender.com').replace(/\/$/, '');
const ADMIN_EMAIL = process.env.ADMIN_EMAIL;
const ADMIN_PASSWORD = process.env.ADMIN_PASSWORD;
const PASSWORD = process.env.DEMO_PASSWORD ?? 'Demo12345';
const REGIONS = (process.env.REGIONS ?? (CORE_TEMPLATE.includes('{region}') ? 'co,br,us,fr,jp,de' : 'co'))
  .split(',').map((r) => r.trim().toLowerCase()).filter(Boolean);
if (!ADMIN_EMAIL || !ADMIN_PASSWORD) {
  console.error('Faltan variables: ADMIN_EMAIL y ADMIN_PASSWORD');
  process.exit(1);
}
if (!CORE_TEMPLATE.includes('{region}') && REGIONS.length > 1) {
  console.error('CORE_URL apunta a un solo core: use {region} en la URL o una sola región en REGIONS');
  process.exit(1);
}

// Datos ficticios por país: idioma, teléfono, dirección (según el esquema del país), identificación fiscal,
// ubicación base del trabajador y 3 vacantes cerca de ella (pago por día en unidades menores de la moneda).
const DATA = {
  co: {
    lang: 'es-CO', phone: '300 765 4321', point: [4.65, -74.08], legalName: 'Comercio Demo SAS',
    tax: { taxIdType: 'NIT', taxIdNumber: '900123456-8' },
    address: { street_address: 'Calle 26 # 68-35', city: 'Bogotá', department: 'Bogotá D.C.' },
    // Vacantes: scripts/seed-vacantes.mjs
  },
  br: {
    lang: 'pt-BR', phone: '(11) 98765-4321', point: [-23.5558, -46.6623], legalName: 'Comércio Demo Ltda',
    tax: { taxIdType: 'CNPJ', taxIdNumber: '11.222.333/0001-81' },
    address: { street: 'Rua Augusta', number: '1500', neighborhood: 'Consolação', city: 'São Paulo', state: 'SP', postal_code: '01304-001' },
    vacancies: [
      { who: 'comercio', slug: 'warehouse', title: 'Auxiliar de estoque para inventário', days: 2, positions: 2, pay: 18000,
        from: '07:00', to: '15:00', start: 1, items: ['Botas de segurança', 'Luvas'],
        description: 'Contagem de estoque, organização de paletes e etiquetagem de caixas no depósito.',
        address: { street: 'Rua Augusta', number: '1500', neighborhood: 'Consolação', city: 'São Paulo', state: 'SP', postal_code: '01304-001' },
        point: [-23.5558, -46.6623] },
      { who: 'cliente', slug: 'cleaning', title: 'Limpeza geral de escritório', days: 1, positions: 1, pay: 15000,
        from: '08:00', to: '16:00', start: 2, items: [],
        description: 'Limpeza de pisos, banheiros e copa de um escritório de 200 m² após uma reforma.',
        address: { street: 'Avenida Paulista', number: '1578', neighborhood: 'Bela Vista', city: 'São Paulo', state: 'SP', postal_code: '01310-200' },
        point: [-23.5614, -46.6559] },
      { who: 'comercio', slug: 'events', title: 'Apoio logístico para evento corporativo', days: 1, positions: 4, pay: 20000,
        from: '14:00', to: '22:00', start: 4, items: ['Camisa branca', 'Calça preta'],
        description: 'Montagem de mesas e cadeiras, recepção dos convidados e desmontagem ao final do evento.',
        address: { street: 'Rua Vergueiro', number: '1000', neighborhood: 'Liberdade', city: 'São Paulo', state: 'SP', postal_code: '01504-000' },
        point: [-23.5700, -46.6400] },
    ],
  },
  us: {
    lang: 'en-US', phone: '(212) 736-3100', point: [40.7484, -73.9857], legalName: 'Demo Business LLC',
    tax: { taxIdType: 'EIN', taxIdNumber: '12-3456789' },
    address: { line1: '350 5th Ave', city: 'New York', state: 'NY', postal_code: '10118' },
    vacancies: [
      { who: 'comercio', slug: 'warehouse', title: 'Warehouse associate for inventory count', days: 2, positions: 2, pay: 16000,
        from: '07:00', to: '15:00', start: 1, items: ['Safety boots', 'Gloves'],
        description: 'Count stock, organize pallets and label boxes in the main warehouse.',
        address: { line1: '4 Pennsylvania Plaza', city: 'New York', state: 'NY', postal_code: '10001' },
        point: [40.7505, -73.9934] },
      { who: 'cliente', slug: 'cleaning', title: 'Office deep cleaning', days: 1, positions: 1, pay: 14000,
        from: '08:00', to: '16:00', start: 2, items: [],
        description: 'Clean floors, restrooms and kitchen of a 2,000 sq ft office after a renovation.',
        address: { line1: '350 5th Ave', city: 'New York', state: 'NY', postal_code: '10118' },
        point: [40.7484, -73.9857] },
      { who: 'comercio', slug: 'events', title: 'Event staff for a corporate party', days: 1, positions: 4, pay: 18000,
        from: '14:00', to: '22:00', start: 4, items: ['White shirt', 'Black pants'],
        description: 'Set up tables and chairs, welcome guests and break down after the event.',
        address: { line1: '11 Wall St', city: 'New York', state: 'NY', postal_code: '10005' },
        point: [40.7069, -74.0113] },
    ],
  },
  fr: {
    lang: 'fr-FR', phone: '06 98 76 54 32', point: [48.8559, 2.3601], legalName: 'Commerce Démo SARL',
    tax: { taxIdType: 'SIRET', taxIdNumber: '12345678900015' },
    address: { street_address: '8 rue de Rivoli', postal_code: '75004', city: 'Paris' },
    vacancies: [
      { who: 'comercio', slug: 'warehouse', title: 'Préparateur pour inventaire en entrepôt', days: 2, positions: 2, pay: 14000,
        from: '07:00', to: '15:00', start: 1, items: ['Chaussures de sécurité', 'Gants'],
        description: 'Comptage du stock, rangement des palettes et étiquetage des cartons dans l’entrepôt.',
        address: { street_address: '8 rue de Rivoli', postal_code: '75004', city: 'Paris' },
        point: [48.8559, 2.3601] },
      { who: 'cliente', slug: 'cleaning', title: 'Nettoyage complet de bureaux', days: 1, positions: 1, pay: 12000,
        from: '08:00', to: '16:00', start: 2, items: [],
        description: 'Nettoyage des sols, sanitaires et cuisine d’un bureau de 200 m² après travaux.',
        address: { street_address: '10 place de la Bourse', postal_code: '75002', city: 'Paris' },
        point: [48.8687, 2.3412] },
      { who: 'comercio', slug: 'events', title: 'Renfort logistique pour un événement d’entreprise', days: 1, positions: 4, pay: 15000,
        from: '14:00', to: '22:00', start: 4, items: ['Chemise blanche', 'Pantalon noir'],
        description: 'Installation des tables et chaises, accueil des invités et démontage en fin de soirée.',
        address: { street_address: '15 rue de la Roquette', postal_code: '75011', city: 'Paris' },
        point: [48.8545, 2.3730] },
    ],
  },
  jp: {
    lang: 'ja-JP', phone: '090-8765-4321', point: [35.6812, 139.7671], legalName: 'デモ商事株式会社',
    tax: { taxIdType: 'CORPORATE_NUMBER', taxIdNumber: '8700110005901' },
    address: { postal_code: '100-0001', prefecture: '東京都', city: '千代田区', town: '千代田', block: '1-1' },
    vacancies: [
      { who: 'comercio', slug: 'warehouse', title: '倉庫の棚卸しスタッフ', days: 2, positions: 2, pay: 12000,
        from: '07:00', to: '15:00', start: 1, items: ['安全靴', '軍手'],
        description: '倉庫での在庫数え、パレットの整理、段ボールのラベル貼り。',
        address: { postal_code: '100-0005', prefecture: '東京都', city: '千代田区', town: '丸の内', block: '1-9-1' },
        point: [35.6812, 139.7671] },
      { who: 'cliente', slug: 'cleaning', title: 'オフィスの清掃', days: 1, positions: 1, pay: 10000,
        from: '08:00', to: '16:00', start: 2, items: [],
        description: '改装後のオフィス（200㎡）の床、トイレ、給湯室の清掃。',
        address: { postal_code: '104-0061', prefecture: '東京都', city: '中央区', town: '銀座', block: '4-6-16' },
        point: [35.6717, 139.7650] },
      { who: 'comercio', slug: 'events', title: '企業イベントの運営スタッフ', days: 1, positions: 4, pay: 13000,
        from: '14:00', to: '22:00', start: 4, items: ['白いシャツ', '黒いズボン'],
        description: 'テーブルと椅子の設営、来場者の受付、終了後の撤収。',
        address: { postal_code: '100-0001', prefecture: '東京都', city: '千代田区', town: '千代田', block: '1-1' },
        point: [35.6852, 139.7528] },
    ],
  },
  de: {
    lang: 'de-DE', phone: '0151 23456789', point: [52.5200, 13.4050], legalName: 'Demo Handel GmbH',
    tax: { taxIdType: 'USTID', taxIdNumber: 'DE123456789' },
    address: { street_address: 'Alexanderplatz 1', postal_code: '10178', city: 'Berlin' },
    vacancies: [
      { who: 'comercio', slug: 'warehouse', title: 'Lagerhelfer für die Inventur', days: 2, positions: 2, pay: 14000,
        from: '07:00', to: '15:00', start: 1, items: ['Sicherheitsschuhe', 'Handschuhe'],
        description: 'Bestände zählen, Paletten ordnen und Kartons im Hauptlager etikettieren.',
        address: { street_address: 'Alexanderplatz 1', postal_code: '10178', city: 'Berlin' },
        point: [52.5219, 13.4132] },
      { who: 'cliente', slug: 'cleaning', title: 'Grundreinigung eines Büros', days: 1, positions: 1, pay: 12000,
        from: '08:00', to: '16:00', start: 2, items: [],
        description: 'Reinigung von Böden, Sanitärräumen und Küche eines 200 m² großen Büros nach einer Renovierung.',
        address: { street_address: 'Friedrichstraße 43', postal_code: '10117', city: 'Berlin' },
        point: [52.5076, 13.3904] },
      { who: 'comercio', slug: 'events', title: 'Aushilfe für eine Firmenveranstaltung', days: 1, positions: 4, pay: 15000,
        from: '14:00', to: '22:00', start: 4, items: ['Weißes Hemd', 'Schwarze Hose'],
        description: 'Aufbau von Tischen und Stühlen, Empfang der Gäste und Abbau nach der Veranstaltung.',
        address: { street_address: 'Unter den Linden 77', postal_code: '10117', city: 'Berlin' },
        point: [52.5163, 13.3809] },
    ],
  },
};

function client(base, lang) {
  return async function call(path, { token, method = 'GET', body } = {}) {
    const res = await fetch(base + path, {
      method,
      headers: { 'Content-Type': 'application/json', 'Accept-Language': lang, ...(token ? { Authorization: `Bearer ${token}` } : {}) },
      body: body ? JSON.stringify(body) : undefined,
      signal: AbortSignal.timeout(120_000),
    });
    const text = await res.text();
    let data = null;
    try { data = text ? JSON.parse(text) : null; } catch { data = text; }
    return { status: res.status, data };
  };
}

/** Llamado que debe responder 2xx; si no, corta la región con el error del servidor. */
async function must(call, what, path, options) {
  const r = await call(path, options);
  if (r.status < 200 || r.status >= 300) throw new Error(`${what}: ${r.status} ${JSON.stringify(r.data)}`);
  return r.data;
}

/** Fecha local (YYYY-MM-DD) dentro de n días en la zona horaria dada. */
const dayIn = (zone, n) => new Intl.DateTimeFormat('en-CA', { timeZone: zone }).format(new Date(Date.now() + n * 86_400_000));

async function seedRegion(region) {
  const d = DATA[region];
  if (!d) throw new Error(`región sin datos de demostración: ${region}`);
  const base = CORE_TEMPLATE.replace('{region}', region);
  const call = client(base, d.lang);
  console.log(`\n=== ${region.toUpperCase()} — ${base} (puede tardar si el servicio estaba dormido)`);

  const health = await call('/actuator/health').catch((e) => ({ status: 0, data: String(e) }));
  if (health.data?.status !== 'UP') throw new Error(`el core no está sano: ${health.status} ${JSON.stringify(health.data)}`);
  const profile = await must(call, 'perfil regional', '/region-profile');

  // 1. Cuentas
  const users = [
    { email: `cliente@${region}.jornal.test`, givenName: 'Cliente', rol: 'APPLICANT', address: { label: 'Casa', fields: d.address } },
    { email: `comercio@${region}.jornal.test`, givenName: 'Comercio', rol: 'BUSINESS',
      business: { legalName: d.legalName, ...d.tax, billingAddress: { fields: d.address } } },
    { email: `trabajador@${region}.jornal.test`, givenName: 'Trabajador', rol: 'WORKER' },
  ];
  for (const u of users) {
    const r = await call('/auth/register', { method: 'POST', body: {
      email: u.email, password: PASSWORD, givenName: u.givenName, familyName: 'Demo', rol: u.rol, phone: d.phone,
      acceptedTerms: true, ...(u.address ? { address: u.address } : {}), ...(u.business ? { business: u.business } : {}) } });
    if (r.status === 201) console.log(`OK   ${u.email} registrado`);
    else if (r.status === 409) console.log(`OK   ${u.email} ya existía`);
    else throw new Error(`registro de ${u.email}: ${r.status} ${JSON.stringify(r.data)}`);
  }

  // 2. Trabajador listo para recibir avisos
  const login = async (email, password) => (await must(call, `inicio de sesión de ${email}`, '/auth/login',
    { method: 'POST', body: { email, password } })).accessToken;
  const admin = await login(ADMIN_EMAIL.replace('{region}', region), ADMIN_PASSWORD);
  const worker = await login(`trabajador@${region}.jornal.test`, PASSWORD);
  const me = await must(call, 'perfil del trabajador', '/users/me', { token: worker });
  await must(call, 'verificar al trabajador', `/admin/users/${me.id}/verification`,
    { token: admin, method: 'PATCH', body: { state: 'VERIFIED' } });
  const categories = await must(call, 'catálogo', '/categories', { token: worker });
  const categoryId = (slug) => {
    const c = categories.find((x) => x.slug === slug);
    if (!c) throw new Error(`no existe la categoría ${slug}`);
    return c.id;
  };
  await must(call, 'perfil de trabajador', '/workers/me', { token: worker, method: 'PUT', body: {
    bio: 'Trabajador de demostración', workRadiusKm: 20, categoryIds: [categoryId('warehouse')],
    baseLatitude: d.point[0], baseLongitude: d.point[1] } });
  await must(call, 'disponibilidad', '/workers/me/availability', { token: worker, method: 'PATCH', body: { available: true } });
  console.log(`OK   trabajador verificado, con bodega, ubicación base (${d.point.join(', ')}) y disponible`);

  // 3. Vacantes de prueba
  console.log('Vacantes:');
  if (region === 'co') {
    const seed = fileURLToPath(new URL('./seed-vacantes.mjs', import.meta.url));
    const run = spawnSync(process.execPath, [seed], { stdio: 'inherit', env: { ...process.env, API_URL: base, DEMO_PASSWORD: PASSWORD } });
    if (run.status !== 0) throw new Error('scripts/seed-vacantes.mjs falló');
    return;
  }
  const tokens = { comercio: await login(`comercio@${region}.jornal.test`, PASSWORD), cliente: await login(`cliente@${region}.jornal.test`, PASSWORD) };
  const zone = profile.defaultTimezone;
  for (const v of d.vacancies) {
    const created = await must(call, `vacante «${v.title}»`, '/vacancies', { token: tokens[v.who], method: 'POST', body: {
      title: v.title, description: v.description, categoryId: categoryId(v.slug), items: v.items,
      startLocal: `${dayIn(zone, v.start)}T${v.from}`, endLocal: `${dayIn(zone, v.start + v.days - 1)}T${v.to}`, timezone: zone,
      positions: v.positions, payMinor: v.pay * v.days, address: v.address, latitude: v.point[0], longitude: v.point[1] } });
    console.log(`${created.title} — ${v.who}@${region}.jornal.test — ${dayIn(zone, v.start)} (${v.days} día(s)) — ${v.positions} persona(s)`);
  }
  console.log(`Listo: ${d.vacancies.length} vacantes publicadas.`);
}

const failed = [];
for (const region of REGIONS) {
  try {
    await seedRegion(region);
  } catch (e) {
    console.log(`FALLA ${region.toUpperCase()}: ${e.message}`);
    failed.push(region);
  }
}
const ok = REGIONS.filter((r) => !failed.includes(r));
console.log(`\nRegiones cargadas: ${ok.join(', ') || 'ninguna'}${failed.length ? ` — con fallas: ${failed.join(', ')}` : ''}`);
process.exit(failed.length ? 1 : 0);
