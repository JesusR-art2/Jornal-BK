package com.jornal.users.controllers;

import com.jornal.users.dto.BusinessRequest;
import com.jornal.users.dto.BusinessResponse;
import com.jornal.users.services.BusinessService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/users/me/business")
@PreAuthorize("hasRole('BUSINESS')")
@RequiredArgsConstructor
public class BusinessController {

    private final BusinessService businessService;

    @GetMapping
    public BusinessResponse get(@AuthenticationPrincipal Jwt jwt) {
        return businessService.get(userId(jwt));
    }

    @PutMapping
    public BusinessResponse update(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody BusinessRequest request) {
        return businessService.update(userId(jwt), request);
    }

    private UUID userId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }
}
