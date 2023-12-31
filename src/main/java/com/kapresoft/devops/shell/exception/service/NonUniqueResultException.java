package com.kapresoft.devops.shell.exception.service;

import org.springframework.lang.NonNull;

public class NonUniqueResultException extends ServiceException {
    public NonUniqueResultException(@NonNull String name) {
        super("Non-unique result: Multiple entries match the provided criteria: %s".formatted(name));
    }

}
