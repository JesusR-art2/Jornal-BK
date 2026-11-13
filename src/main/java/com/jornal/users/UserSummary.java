package com.jornal.users;

import java.util.UUID;

public record UserSummary(UUID id, String name, Rol rol, boolean verified) {
}
