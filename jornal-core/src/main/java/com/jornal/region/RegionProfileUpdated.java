package com.jornal.region;

/** Se publica cuando cambia un perfil regional; los consumidores aplican solo versiones más nuevas. */
public record RegionProfileUpdated(String code, long version) {
}
