package com.jornal.region.services;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Pattern;

/**
 * Estrategias de validación de documentos, elegidas por nombre desde el perfil regional
 * ({@code region_document_types.validator}). No hay reglas por país en el código: el perfil
 * decide qué estrategia usa cada tipo de documento.
 */
@Component
public class DocumentValidators {

    public static final String REGEX = "regex";

    private static final Map<String, Predicate<String>> CHECKSUMS = Map.of(
            "nit-dv", DocumentValidators::nitWithCheckDigit,
            "cpf", DocumentValidators::cpf,
            "cnpj", DocumentValidators::cnpj,
            "luhn", DocumentValidators::luhn,
            "jp-mynumber", DocumentValidators::jpMyNumber,
            "jp-corporate", DocumentValidators::jpCorporateNumber);

    public Set<String> names() {
        var names = new java.util.HashSet<>(CHECKSUMS.keySet());
        names.add(REGEX);
        return names;
    }

    public boolean exists(String validator) {
        return REGEX.equals(validator) || CHECKSUMS.containsKey(validator);
    }

    /**
     * Normaliza (mayúsculas, sin espacios; sin separadores para los validadores con dígito de control)
     * y devuelve el número si es válido, o {@code null} si no lo es.
     */
    public String validate(String validator, String regex, String number) {
        if (number == null || number.isBlank()) return null;
        String upper = number.trim().toUpperCase(java.util.Locale.ROOT).replaceAll("\\s+", "");
        if (REGEX.equals(validator)) {
            return regex == null || Pattern.matches(regex, upper) ? upper : null;
        }
        Predicate<String> check = CHECKSUMS.get(validator);
        if (check == null) return null;
        String digits = upper.replaceAll("[.\\-/]", "");
        if (!digits.matches("\\d+")) return null;
        if (regex != null && !Pattern.matches(regex, digits)) return null;
        return check.test(digits) ? digits : null;
    }

    /** NIT de Colombia: el último dígito es el dígito de verificación (módulo 11 con pesos primos). */
    static boolean nitWithCheckDigit(String d) {
        if (d.length() < 6 || d.length() > 16) return false;
        int[] weights = {3, 7, 13, 17, 19, 23, 29, 37, 41, 43, 47, 53, 59, 67, 71};
        String base = d.substring(0, d.length() - 1);
        int sum = 0;
        for (int i = 0; i < base.length(); i++) {
            int digit = base.charAt(base.length() - 1 - i) - '0';
            sum += digit * weights[i];
        }
        int r = sum % 11;
        int dv = r > 1 ? 11 - r : r;
        return dv == d.charAt(d.length() - 1) - '0';
    }

    static boolean cpf(String d) {
        if (d.length() != 11 || allSame(d)) return false;
        return cpfDigit(d, 9) == d.charAt(9) - '0' && cpfDigit(d, 10) == d.charAt(10) - '0';
    }

    private static int cpfDigit(String d, int length) {
        int sum = 0;
        for (int i = 0; i < length; i++) {
            sum += (d.charAt(i) - '0') * (length + 1 - i);
        }
        return (sum * 10) % 11 % 10;
    }

    static boolean cnpj(String d) {
        if (d.length() != 14 || allSame(d)) return false;
        int[] w1 = {5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2};
        int[] w2 = {6, 5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2};
        return cnpjDigit(d, w1) == d.charAt(12) - '0' && cnpjDigit(d, w2) == d.charAt(13) - '0';
    }

    private static int cnpjDigit(String d, int[] weights) {
        int sum = 0;
        for (int i = 0; i < weights.length; i++) {
            sum += (d.charAt(i) - '0') * weights[i];
        }
        int r = sum % 11;
        return r < 2 ? 0 : 11 - r;
    }

    static boolean luhn(String d) {
        int sum = 0;
        boolean twice = false;
        for (int i = d.length() - 1; i >= 0; i--) {
            int n = d.charAt(i) - '0';
            if (twice) {
                n *= 2;
                if (n > 9) n -= 9;
            }
            sum += n;
            twice = !twice;
        }
        return d.length() > 1 && sum % 10 == 0;
    }

    /** My Number (12 dígitos): el último es el dígito de control. */
    static boolean jpMyNumber(String d) {
        if (d.length() != 12) return false;
        int sum = 0;
        for (int n = 1; n <= 11; n++) {
            int p = d.charAt(11 - n) - '0';
            int q = n <= 6 ? n + 1 : n - 5;
            sum += p * q;
        }
        int r = sum % 11;
        int check = r <= 1 ? 0 : 11 - r;
        return check == d.charAt(11) - '0';
    }

    /** Número corporativo (13 dígitos): el primero es el dígito de control. */
    static boolean jpCorporateNumber(String d) {
        if (d.length() != 13) return false;
        String base = d.substring(1);
        int sum = 0;
        for (int n = 1; n <= 12; n++) {
            int p = base.charAt(12 - n) - '0';
            sum += p * (n % 2 == 1 ? 1 : 2);
        }
        int check = 9 - (sum % 9);
        return check == d.charAt(0) - '0';
    }

    private static boolean allSame(String d) {
        return d.chars().distinct().count() == 1;
    }
}
