# P2: sexto país solo con datos (Alemania)

- Fecha: 2026-11-25T05:03:43Z
- Versión: 252d59f
- Script: `scripts/demo-pais-nuevo.sh`

```text
base jornal_de creada (el volumen es anterior a este cambio)
perfil: {"code":"DE","defaultLocale":"de-DE","currency":"EUR","datePattern":"dd.MM.yyyy","receiptType":"RECHNUNG","tax":"USt","rate":1900,"tipMode":"OPTIONAL","pagos":["CARD","PAYPAL","SEPA"]}
migración aplicada: 18 germany
cliente: {"name":"Erika Muster","countryCode":"DE","locale":"de-DE","phone":"+493012345678"}
catálogo en alemán: {"name":"Lager und Inventur","price":{"unit":"DAY","amountMinor":14000,"currency":"EUR","moneyScale":2}}
pedido ce5c970c-8800-43b2-a557-d3ea03954b69
{"status":"COMPLETED","subtotalMinor":28000,"travelFeeMinor":900,"taxMinor":4614,"tipMinor":1400,"totalMinor":30300,"currency":"EUR","receiptNumber":"RE-00000001"}
pago: {"method":"SEPA","flow":"CHARGE_REFUND","status":"CAPTURED","amountMinor":30300,"currency":"EUR"}
factura: {"title":"Invoice (Rechnung)","number":"RE-00000001","locale":null,"date":"25. Nov. 2026, 06:04","totals":{"Subtotal":"280,00 €","Travel fee":"9,00 €","USt (19 %, included)":"46,14 €","Tip":"14,00 €","Total":"303,00 €"}}
último aviso al cliente: Request completed: Your request for Lager und Inventur is complete. Thank you for using Jornal!
colas de la región: ["billing.de","core.de","dispatch.de","notifications.de","payments.de"]
```

**Resultado:** Alemania funcionó de punta a punta sin código por país: perfil, catálogo y precios desde la base; pago SEPA, asignación, avisos y factura RE en euros; los textos sin traducción salen en inglés
