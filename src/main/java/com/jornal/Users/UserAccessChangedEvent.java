package com.jornal.Users;

import java.util.UUID;

public record UserAccessChangedEvent(UUID userId, boolean eligible) {
}
