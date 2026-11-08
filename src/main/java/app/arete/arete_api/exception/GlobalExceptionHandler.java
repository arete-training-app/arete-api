package app.arete.arete_api.exception;

import app.arete.arete_api.util.Constants;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    @ExceptionHandler(NotFoundException.class)
    public ProblemDetail handleNotFoundException(final NotFoundException exception) {
        return warn(HttpStatus.NOT_FOUND, exception);
    }

    @ExceptionHandler(ConflictException.class)
    public ProblemDetail handleConflictException(final ConflictException exception) {
        return warn(HttpStatus.CONFLICT, exception);
    }

    @ExceptionHandler({
            BadRequestException.class,
            MethodArgumentTypeMismatchException.class,
            ConstraintViolationException.class
    })
    public ProblemDetail handleBadRequestException(final Exception exception) {
        return warn(HttpStatus.BAD_REQUEST, exception);
    }

    /**
     * Logged at debug only: an expired access token ends in a 401 on every refresh cycle of a normal session.
     */
    @ExceptionHandler({UnauthorizedException.class, AuthenticationException.class})
    public ProblemDetail handleUnauthorizedException(final Exception exception) {
        log.debug("{}: {}", HttpStatus.UNAUTHORIZED, exception.getMessage());
        return ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, exception.getMessage());
    }

    @ExceptionHandler({ForbiddenException.class, AccessDeniedException.class})
    public ProblemDetail handleForbiddenException(final Exception exception) {
        return warn(HttpStatus.FORBIDDEN, exception);
    }

    @ExceptionHandler(TooManyRequestsException.class)
    public ProblemDetail handleTooManyRequestsException(final TooManyRequestsException exception) {
        return warn(HttpStatus.TOO_MANY_REQUESTS, exception);
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail handleException(final Exception exception) {
        log.error("Unhandled exception", exception);
        return ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR, Constants.GENERIC_ERROR_DETAIL);
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            final MethodArgumentNotValidException exception,
            final HttpHeaders headers,
            final HttpStatusCode status,
            final WebRequest request) {
        final Map<String, String> errorsByField = exception.getBindingResult().getFieldErrors().stream()
                .collect(Collectors.toMap(
                        FieldError::getField,
                        fieldError -> String.valueOf(fieldError.getDefaultMessage()),
                        (first, second) -> first));
        log.warn("{}: {} {}", HttpStatus.BAD_REQUEST, Constants.VALIDATION_FAILED_DETAIL, errorsByField);
        final ProblemDetail problemDetail =
                ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, Constants.VALIDATION_FAILED_DETAIL);
        problemDetail.setProperty(Constants.ERRORS_PROPERTY, errorsByField);
        return ResponseEntity.badRequest().body(problemDetail);
    }

    /**
     * Client errors are logged without a stack trace: the cause is in the request, not in the code.
     */
    private static ProblemDetail warn(final HttpStatus status, final Exception exception) {
        log.warn("{}: {}", status, exception.getMessage());
        return ProblemDetail.forStatusAndDetail(status, exception.getMessage());
    }
}
