package com.jornal.region;

/** Estrategia de pago que elige el medio de pago del perfil. */
public enum PaymentFlow { AUTH_CAPTURE, CHARGE_REFUND, ASYNC_VOUCHER }
