package com.jornal.requests.controllers;

import com.jornal.requests.RequestStatus;
import com.jornal.requests.dto.RequestResponse;
import com.jornal.requests.services.RequestService;
import com.jornal.shared.PageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/requests")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminRequestController {

    private final RequestService requests;

    @GetMapping
    public PageResponse<RequestResponse> list(@RequestParam(required = false) RequestStatus status,
                                              @RequestParam(defaultValue = "0") int page,
                                              @RequestParam(defaultValue = "20") int size) {
        return requests.all(status, RequestController.pageable(page, size));
    }
}
