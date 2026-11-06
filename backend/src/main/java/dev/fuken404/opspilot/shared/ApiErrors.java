package dev.fuken404.opspilot.shared;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Errores en formato RFC 9457, sin detalles internos. */
@RestControllerAdvice
class ApiErrors {

    @ExceptionHandler(NotFoundException.class)
    ProblemDetail notFound(NotFoundException error) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, error.getMessage());
    }

    @ExceptionHandler(ConflictException.class)
    ProblemDetail conflict(ConflictException error) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, error.getMessage());
    }

    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    ProblemDetail staleUpdate() {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT,
            "Alguien más modificó este registro. Recarga y vuelve a intentarlo.");
    }

    @ExceptionHandler(AccessDeniedException.class)
    ProblemDetail forbidden() {
        return ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, "Tu rol no permite esta acción.");
    }
}
