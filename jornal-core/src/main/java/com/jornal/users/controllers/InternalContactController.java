package com.jornal.users.controllers;

import com.jornal.region.RegionApi;
import com.jornal.region.RegionProfileView;
import com.jornal.users.User;
import com.jornal.users.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.UUID;

/**
 * Consulta interna del contacto de un usuario (la hace el subsistema de notificaciones).
 * El dato personal no se replica fuera del país: se pide aquí cada vez que hace falta.
 * No pasa por el JWT: la protege un token interno compartido y el gateway no la expone.
 */
@RestController
@RequestMapping("/internal/contacts")
public class InternalContactController {

    private final UserRepository users;
    private final RegionApi region;
    private final byte[] token;

    public InternalContactController(UserRepository users, RegionApi region,
                                     @Value("${jornal.internal.token}") String token) {
        this.users = users;
        this.region = region;
        this.token = token.getBytes(StandardCharsets.UTF_8);
    }

    public record ContactResponse(UUID id, String name, String email, String phone,
                                  String locale, String timezone, boolean active) {
    }

    @GetMapping("/{userId}")
    public ContactResponse contact(@RequestHeader(value = "X-Internal-Token", required = false) String given,
                                   @PathVariable UUID userId) {
        if (given == null || !MessageDigest.isEqual(token, given.getBytes(StandardCharsets.UTF_8))) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }
        User user = users.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        RegionProfileView profile = region.current();
        boolean active = user.isActive() && user.getDeletedAt() == null;
        return new ContactResponse(user.getId(), user.getName(), user.getEmail(), user.getPhone(),
                user.getLocale(), profile.defaultTimezone(), active);
    }
}
