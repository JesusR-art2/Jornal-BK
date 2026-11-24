package com.jornal.requests.services;

import java.util.UUID;

/** Quién ejecuta una acción. {@code role} viene del JWT: APPLICANT, BUSINESS, WORKER, ADMIN o SYSTEM. */
public record Actor(UUID id, String role) {

    public static final Actor SYSTEM = new Actor(null, "SYSTEM");

    public static Actor service(String name) {
        return new Actor(null, name);
    }

    public boolean isAdmin() {
        return "ADMIN".equals(role);
    }

    public boolean isClient() {
        return "APPLICANT".equals(role) || "BUSINESS".equals(role);
    }

    public boolean isWorker() {
        return "WORKER".equals(role);
    }

    /** Texto del historial: CLIENT:id, WORKER:id, ADMIN:id, SYSTEM o el nombre del subsistema. */
    public String label() {
        if (id == null) return role;
        return (isClient() ? "CLIENT" : role) + ":" + id;
    }
}
