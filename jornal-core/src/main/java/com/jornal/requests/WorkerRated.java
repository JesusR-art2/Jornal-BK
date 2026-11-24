package com.jornal.requests;

import java.util.UUID;

/** Evento interno: el cliente calificó al trabajador (workers actualiza su promedio y su foto). */
public record WorkerRated(UUID workerId, UUID requestId, int score) {
}
