package com.kapresoft.devops.shell.validator;

import jakarta.validation.ValidationException;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.BindingResult;
import org.springframework.validation.Validator;

import java.util.List;

import static org.springframework.validation.ValidationUtils.invokeValidator;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CustomValidator {

    private final List<Validator> validators;

    public CustomValidator(List<Validator> validators) {
        this.validators = validators;
    }

    public BindingResult validate(Object target, String name) {
        final BindingResult result = new BeanPropertyBindingResult(target, name);
        validators.forEach(v -> invokeValidator(v, target, result));
        return result;
    }

    public void validateOrThrow(Object target, String name) {
        final BindingResult result = this.validate(target, name);
        if (result.hasErrors()) {
            throw new ValidationException("Validation Errors: %s".formatted(result.toString()));
        }
    }
}
