# Prueba de localización (sección 8)

- Fecha de la corrida: 2026-11-25T05:19:03.375716900Z
- Versión: ver `git log`
- Prueba: `jornal-core/src/test/java/com/jornal/LocalizationReportTest.java` (script `scripts/prueba-localizacion.sh`)
- Pedido D4: 2 jornadas de bodega + 1 recargo de desplazamiento + propina según el país. Reloj fijo a las 20:30 locales del 15/11/2026.
- Valores de impuestos, precios y tarifas: de los perfiles regionales, marcados VERIFICAR.

## Resumen

| País | Idioma | Hora mostrada | Instante UTC guardado | Total | Propina |
| --- | --- | --- | --- | --- | --- |
| CO | es-CO | 15/11/2026 8:30 p. m. | 2026-11-16T01:30:00Z | $ 195.000 | $ 17.000 |
| BR | pt-BR | 15/11/2026 20:30 | 2026-11-15T23:30:00Z | R$ 384,00 | R$ 34,00 |
| US | en-US | 11/15/2026 8:30 PM | 2026-11-16T01:30:00Z | $367.40 | $45.00 |
| FR | fr-FR | 15/11/2026 20:30 | 2026-11-15T19:30:00Z | 281,00 € | 13,00 € |
| JP | ja-JP | 2026/11/15 20:30 | 2026-11-15T11:30:00Z | ￥29,000 | ￥0 |

Colombia y EE. UU. cruzan la medianoche en UTC: el pedido de las 20:30 del 15/11 queda guardado el 16/11 en UTC. El 15/11 es feriado nacional en Brasil; hoy no hay recargo por festivo, así que el precio no cambia.

## CO

| Elemento | Resultado |
| --- | --- |
| 1. Idioma y textos | es-CO; categoría «Bodega e inventario»; mensaje «La fecha y hora del trabajo deben estar en el futuro» |
| 2. Montos | subtotal $ 170.000; desplazamiento $ 8.000; impuesto $ 28.420 (incluido); propina $ 17.000; **total $ 195.000** |
| 3. Fecha y hora | 15/11/2026 8:30 p. m. (America/Bogota); guardado 2026-11-16T01:30:00Z |
| 4. Dirección y teléfono | Calle 26 # 68-35, Salitre, Bogotá, Cundinamarca, 111321; +573001234567 |
| 5. Impuesto y comprobante | IVA 19 % incluido; comprobante DIAN_E_INVOICE (FE) |
| 6. Medios de pago | PSE (CHARGE_REFUND), NEQUI (CHARGE_REFUND), CARD (AUTH_CAPTURE) |
| 7. Propina | opcional; sugerida [10] %, se usó 10 % |
| Separadores | CLDR «.» y «,» = perfil |
| Festivo el 15/11/2026 | no |

## BR

| Elemento | Resultado |
| --- | --- |
| 1. Idioma y textos | pt-BR; categoría «Armazém e inventário»; mensaje «A data e hora do trabalho devem estar no futuro» |
| 2. Montos | subtotal R$ 340,00; desplazamiento R$ 10,00; impuesto R$ 16,67 (incluido); propina R$ 34,00; **total R$ 384,00** |
| 3. Fecha y hora | 15/11/2026 20:30 (America/Sao_Paulo); guardado 2026-11-15T23:30:00Z |
| 4. Dirección y teléfono | Rua Augusta, 1500, Consolação, São Paulo, SP, 01304-001; +5511912345678 |
| 5. Impuesto y comprobante | ISS 5 % incluido; comprobante NFS_E (NFSE) |
| 6. Medios de pago | PIX (CHARGE_REFUND), CARD (AUTH_CAPTURE), BOLETO (ASYNC_VOUCHER) |
| 7. Propina | opcional; sugerida [10] %, se usó 10 % |
| Separadores | CLDR «.» y «,» = perfil |
| Festivo el 15/11/2026 | sí: Proclamação da República (sin recargo por festivo configurado) |

## US

| Elemento | Resultado |
| --- | --- |
| 1. Idioma y textos | en-US; categoría «Warehouse and inventory»; mensaje «The scheduled time must be in the future» |
| 2. Montos | subtotal $300.00; desplazamiento $10.00; impuesto $12.40 (se suma); propina $45.00; **total $367.40** |
| 3. Fecha y hora | 11/15/2026 8:30 PM (America/New_York); guardado 2026-11-16T01:30:00Z |
| 4. Dirección y teléfono | 350 5th Ave, Suite 100, New York, NY, 10118; +12127363100 |
| 5. Impuesto y comprobante | Sales tax (NY State) 4 % se suma; comprobante SALES_RECEIPT (RCPT) |
| 6. Medios de pago | CARD (AUTH_CAPTURE), PAYPAL (AUTH_CAPTURE) |
| 7. Propina | se pide; sugeridas [15, 18, 20] %, se usó 15 % |
| Separadores | CLDR «,» y «.» = perfil |
| Festivo el 15/11/2026 | no |

## FR

| Elemento | Resultado |
| --- | --- |
| 1. Idioma y textos | fr-FR; categoría «Entrepôt et inventaire»; mensaje «La date et l’heure du travail doivent être dans le futur» |
| 2. Montos | subtotal 260,00 €; desplazamiento 8,00 €; impuesto 44,67 € (incluido); propina 13,00 €; **total 281,00 €** |
| 3. Fecha y hora | 15/11/2026 20:30 (Europe/Paris); guardado 2026-11-15T19:30:00Z |
| 4. Dirección y teléfono | 8 rue de Rivoli, 75004, Paris; +33612345678 |
| 5. Impuesto y comprobante | TVA 20 % incluido; comprobante FACTURE (FA) |
| 6. Medios de pago | CARD (AUTH_CAPTURE), PAYPAL (AUTH_CAPTURE) |
| 7. Propina | opcional; sugerida [5, 10] %, se usó 5 % |
| Separadores | CLDR «U+202F espacio fino» y «,» = perfil |
| Festivo el 15/11/2026 | no |

## JP

| Elemento | Resultado |
| --- | --- |
| 1. Idioma y textos | ja-JP; categoría «倉庫・棚卸し»; mensaje «作業日時は未来の日時を指定してください» |
| 2. Montos | subtotal ￥28,000; desplazamiento ￥1,000; impuesto ￥2,636 (incluido); propina ￥0; **total ￥29,000** |
| 3. Fecha y hora | 2026/11/15 20:30 (Asia/Tokyo); guardado 2026-11-15T11:30:00Z |
| 4. Dirección y teléfono | 100-0001, 東京都, 千代田区, 千代田, 1-1; +819012345678 |
| 5. Impuesto y comprobante | 消費税 10 % incluido; comprobante QUALIFIED_INVOICE (INV) |
| 6. Medios de pago | CARD (AUTH_CAPTURE), PAYPAY (CHARGE_REFUND), KONBINI (ASYNC_VOUCHER) |
| 7. Propina | ausente (no se muestra ni se acepta) |
| Separadores | CLDR «,» y «.» = perfil |
| Festivo el 15/11/2026 | no |

**Resultado:** los 5 países pasan: hora local y UTC, totales, separadores CLDR iguales al perfil, dirección y teléfono válidos con las reglas de cada país.
