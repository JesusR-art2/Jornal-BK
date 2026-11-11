package com.jornal.Users;

import java.util.UUID;

public record UserSummary(UUID id, String name, Rol rol) {
}
