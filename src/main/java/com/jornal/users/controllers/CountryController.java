package com.jornal.users.controllers;


import com.jornal.users.CountryRepository;
import com.jornal.users.dto.CountryResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/countries")
@RequiredArgsConstructor
public class CountryController {

    private final CountryRepository repository;

    @GetMapping
    public List<CountryResponse> list(){
        return repository.findByEnabledTrueOrderByName().stream()
                .map(c -> new CountryResponse(c.getCode(), c.getName(), c.getCurrency(),
                        c.getDefaultLocale(), c.getPhonePrefix(),
                        List.of(c.getDocumentTypes().split(","))))
                .toList();
    }

}
