package com.jornal.users;

import java.util.UUID;

public record UserAccessChangedEvent(UUID userId, boolean eligible) {
}
