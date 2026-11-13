package ru.mirea.library.security;

import ru.mirea.library.model.Role;

import java.security.Principal;
import java.util.*;

public record CurrentUser(UUID id, String login, Set<Role> roles, boolean mustChangePassword)
        implements Principal {
    @Override
    public String getName() {
        return login;
    }
}
