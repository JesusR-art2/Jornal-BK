package com.jornal.region;

/** Orden en que se muestran el nombre y el apellido. */
public enum NameOrder {
    GIVEN_FAMILY,
    FAMILY_GIVEN;

    public String compose(String given, String family) {
        String g = given == null ? "" : given.trim();
        String f = family == null ? "" : family.trim();
        if (f.isEmpty()) return g;
        if (g.isEmpty()) return f;
        return this == GIVEN_FAMILY ? g + " " + f : f + " " + g;
    }
}
