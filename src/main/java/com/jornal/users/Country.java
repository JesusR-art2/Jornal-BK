package com.jornal.users;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "countries")
@Getter
@Setter
@NoArgsConstructor
public class Country {

    @Id
    @Column(length = 2)
    private String code;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(name = "default_locale", nullable = false)
    private String defaultLocale;

    @Column(name = "phone_prefix", nullable = false)
    private String phonePrefix;

    @Column(name = "commission_bps", nullable = false)
    private int commissionBps;

    @Column(name = "document_types", nullable = false)
    private String documentTypes;

    @Column(nullable = false)
    private boolean enabled;




}
