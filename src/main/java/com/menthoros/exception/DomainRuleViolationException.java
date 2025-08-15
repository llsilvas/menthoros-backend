package com.menthoros.exception;

public class DomainRuleViolationException extends RuntimeException {
    public DomainRuleViolationException(String message) {
        super(message);
    }
}
