package com.jornal.requests.dto;

import com.jornal.requests.RequestStatus;

import java.time.Instant;

public record HistoryResponse(RequestStatus from, RequestStatus to, Instant at, String actor, String reason) {
}
