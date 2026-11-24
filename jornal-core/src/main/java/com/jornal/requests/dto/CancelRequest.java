package com.jornal.requests.dto;

import jakarta.validation.constraints.Size;

public record CancelRequest(@Size(max = 120) String reason) {
}
