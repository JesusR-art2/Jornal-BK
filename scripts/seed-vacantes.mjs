// Crea vacantes de prueba en Colombia con las cuentas de demostración (comercio y cliente) a través del gateway.
// Uso:  node scripts/seed-vacantes.mjs            (requiere docker compose arriba y scripts/seed-demo.sh)
//       API_URL=https://<core>.onrender.com node scripts/seed-vacantes.mjs   (Render; ver scripts/seed-render.mjs)
// Variables opcionales: GATEWAY (http://localhost:8000), API_URL (URL del core; reemplaza GATEWAY/api/co), DEMO_PASSWORD (Demo12345).
// Todos los datos son ficticios. Cada corrida crea vacantes nuevas, a partir de mañana.

const GATEWAY = process.env.GATEWAY ?? 'http://localhost:8000';
const API = (process.env.API_URL ?? `${GATEWAY}/api/co`).replace(/\/$/, '');
const PASSWORD = process.env.DEMO_PASSWORD ?? 'Demo12345';

async function call(path, { token, method = 'GET', body } = {}) {
  const res = await fetch(API + path, {
    method,
    headers: {
      'Content-Type': 'application/json',
      'Accept-Language': 'es-CO',
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
    },
    body: body ? JSON.stringify(body) : undefined,
  });
  const text = await res.text();
  const data = text ? JSON.parse(text) : null;
  if (!res.ok) throw new Error(`${method} ${path}: ${res.status} ${text}`);
  return data;
}

const login = async (email) => (await call('/auth/login', { method: 'POST', body: { email, password: PASSWORD } })).accessToken;

/** Fecha local de Bogotá dentro de n días (YYYY-MM-DD). */
function day(n) {
  const d = new Date(Date.now() + n * 86_400_000);
  return new Intl.DateTimeFormat('en-CA', { timeZone: 'America/Bogota' }).format(d);
}

const VACANCIES = [
  {
    who: 'comercio', slug: 'warehouse', title: 'Auxiliar de bodega para inventario', days: 3, positions: 2, pay: 85000,
    from: '07:00', to: '15:00', start: 1,
    description: 'Conteo de inventario, organización de estibas y marcación de cajas en la bodega principal.',
    items: ['Botas de seguridad', 'Guantes'],
    address: { street_address: 'Calle 13 # 68-45', neighborhood: 'Puente Aranda', city: 'Bogotá', department: 'Bogotá D.C.' },
    point: [4.6290, -74.1140],
  },
  {
    who: 'comercio', slug: 'moving', title: 'Cotero para cargue y descargue', days: 1, positions: 3, pay: 90000,
    from: '06:00', to: '14:00', start: 2,
    description: 'Cargue y descargue de un camión con mercancía de supermercado. Se requiere buena condición física.',
    items: ['Faja lumbar', 'Guantes'],
    address: { street_address: 'Avenida Carrera 86 # 12-30', neighborhood: 'Kennedy', city: 'Bogotá', department: 'Bogotá D.C.' },
    point: [4.6290, -74.1560],
  },
  {
    who: 'cliente', slug: 'cleaning', title: 'Aseo general de oficina', days: 1, positions: 1, pay: 70000,
    from: '08:00', to: '16:00', start: 1,
    description: 'Limpieza de pisos, baños y cocina de una oficina de 200 m² después de una remodelación.',
    items: [],
    address: { street_address: 'Carrera 7 # 71-21', neighborhood: 'Chapinero', city: 'Bogotá', department: 'Bogotá D.C.' },
    point: [4.6550, -74.0560],
  },
  {
    who: 'comercio', slug: 'events', title: 'Apoyo logístico para evento empresarial', days: 2, positions: 4, pay: 95000,
    from: '14:00', to: '22:00', start: 4,
    description: 'Montaje de sillas y mesas, recepción de invitados y desmontaje al finalizar el evento.',
    items: ['Camisa blanca', 'Pantalón negro'],
    address: { street_address: 'Carrera 37 # 24-67', neighborhood: 'Quinta Paredes', city: 'Bogotá', department: 'Bogotá D.C.' },
    point: [4.6300, -74.0900],
  },
  {
    who: 'comercio', slug: 'construction', title: 'Ayudante de obra', days: 7, positions: 2, pay: 100000,
    from: '07:00', to: '16:00', start: 3,
    description: 'Apoyo al maestro de obra: preparación de mezcla, traslado de materiales y limpieza del área de trabajo.',
    items: ['Botas punta de acero', 'Casco', 'Guantes'],
    address: { street_address: 'Calle 80 # 69-70', neighborhood: 'Engativá', city: 'Bogotá', department: 'Bogotá D.C.' },
    point: [4.6930, -74.0870],
  },
  {
    who: 'cliente', slug: 'events', title: 'Mesero para evento corporativo', days: 1, positions: 3, pay: 80000,
    from: '17:00', to: '23:00', start: 5,
    description: 'Servicio de bebidas y pasabocas en un evento de 150 personas. Se valora experiencia en servicio.',
    items: ['Camisa blanca', 'Pantalón negro', 'Zapatos negros'],
    address: { street_address: 'Calle 93 # 11-27', neighborhood: 'Chicó', city: 'Bogotá', department: 'Bogotá D.C.' },
    point: [4.6770, -74.0480],
  },
  {
    who: 'cliente', slug: 'gardening', title: 'Mantenimiento de jardín en conjunto residencial', days: 2, positions: 1, pay: 75000,
    from: '08:00', to: '14:00', start: 2,
    description: 'Poda de setos, corte de césped y recolección de residuos vegetales en las zonas comunes.',
    items: ['Guantes', 'Gorra o sombrero', 'Protector solar'],
    address: { street_address: 'Carrera 15 # 127-40', neighborhood: 'Usaquén', city: 'Bogotá', department: 'Bogotá D.C.' },
    point: [4.7030, -74.0420],
  },
  {
    who: 'comercio', slug: 'painting', title: 'Pintor para local comercial', days: 3, positions: 2, pay: 90000,
    from: '08:00', to: '17:00', start: 6,
    description: 'Preparación de superficies y pintura de paredes y techos de un local comercial de 120 m².',
    items: ['Overol', 'Gafas de seguridad', 'Tapabocas'],
    address: { street_address: 'Calle 19 # 4-56', neighborhood: 'La Candelaria', city: 'Bogotá', department: 'Bogotá D.C.' },
    point: [4.6030, -74.0700],
  },
];

const tokens = { comercio: await login('comercio@co.jornal.test'), cliente: await login('cliente@co.jornal.test') };
const categories = await call('/categories', { token: tokens.comercio });
const categoryId = (slug) => categories.find((c) => c.slug === slug)?.id;

for (const v of VACANCIES) {
  const last = day(v.start + v.days - 1);
  const created = await call('/vacancies', {
    token: tokens[v.who], method: 'POST',
    body: {
      title: v.title, description: v.description, categoryId: categoryId(v.slug), items: v.items,
      startLocal: `${day(v.start)}T${v.from}`, endLocal: `${last}T${v.to}`, timezone: 'America/Bogota',
      positions: v.positions, payMinor: v.pay * v.days, address: v.address, latitude: v.point[0], longitude: v.point[1],
    },
  });
  console.log(`${created.title} — ${v.who}@co.jornal.test — ${day(v.start)} (${v.days} día(s)) — ${v.positions} persona(s)`);
}
console.log(`\nListo: ${VACANCIES.length} vacantes publicadas.`);
