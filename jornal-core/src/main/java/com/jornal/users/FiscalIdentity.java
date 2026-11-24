package com.jornal.users;

/** Datos del adquirente para el comprobante: nombre y, si es comercio, razón social e identificación fiscal. */
public record FiscalIdentity(String name, String legalName, String taxIdType, String taxIdNumber) {
}
