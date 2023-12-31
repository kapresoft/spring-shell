package com.kapresoft.devops.shell.exception.service;

import org.springframework.core.NestedRuntimeException;
import org.springframework.lang.NonNull;

class ServiceException extends NestedRuntimeException {
    public ServiceException(@NonNull String msg) {
        super(msg);
    }

    public ServiceException(@NonNull String msg, @NonNull Throwable cause) {
        super(msg, cause);
    }
}
