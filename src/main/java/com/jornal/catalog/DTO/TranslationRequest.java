package com.jornal.catalog.DTO;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record TranslationRequest(@NotBlank @Size(max = 100) String name,
                                 @Size(max = 500) String description){
}
