package com.jornal.users;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CountryRepository  extends JpaRepository<Country, String> {

    Optional<Country> findByCodeAndEnabledTrue(String code);
    List<Country> findByEnabledTrueOrderByName();

}
