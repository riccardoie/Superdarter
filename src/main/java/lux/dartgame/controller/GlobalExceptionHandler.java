package lux.dartgame.controller;

import lombok.extern.slf4j.Slf4j;
import lux.dartgame.dto.ErrorResponse;
import lux.dartgame.exception.AccessDeniedException;
import lux.dartgame.exception.EmailAlreadyExistsException;
import lux.dartgame.exception.GameModeNotFoundException;
import lux.dartgame.exception.GameNotFoundException;
import lux.dartgame.exception.GameConflictException;
import lux.dartgame.exception.FriendConflictException;
import lux.dartgame.exception.FriendRequestNotFoundException;
import lux.dartgame.exception.InvalidScoreException;
import lux.dartgame.exception.InvalidTurnException;
import lux.dartgame.exception.RoleNotFoundException;
import lux.dartgame.exception.UsernameAlreadyExistsException;
import lux.dartgame.exception.UsernameNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

@Slf4j
@RestControllerAdvice
public final class GlobalExceptionHandler {

    // Message returned instead of an internal failure, so no server detail leaks.

    private static final String GENERIC_ERROR_MESSAGE =
            "An unexpected error occurred. Please contact support.";

    @ExceptionHandler(UsernameAlreadyExistsException.class)
    public ResponseEntity<ErrorResponse> handleUsernameAlreadyExists(
            final UsernameAlreadyExistsException e) {
        log.warn("Username already exists: {}", e.getMessage());
        return respond(HttpStatus.CONFLICT, e.getMessage());
    }

    @ExceptionHandler(EmailAlreadyExistsException.class)
    public ResponseEntity<ErrorResponse> handleEmailAlreadyExists(
            final EmailAlreadyExistsException e) {
        log.warn("Email already exists: {}", e.getMessage());
        return respond(HttpStatus.CONFLICT, e.getMessage());
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ErrorResponse> handleBadCredentials(
            final BadCredentialsException e) {
        log.warn("Bad credentials attempt");
        return respond(HttpStatus.UNAUTHORIZED, "Invalid username or password.");
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationErrors(
            final MethodArgumentNotValidException e) {
        log.warn("Validation error: {}", e.getMessage());
        return respond(HttpStatus.BAD_REQUEST, e.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .collect(Collectors.joining(", ")));
    }

    @ExceptionHandler(InvalidScoreException.class)
    public ResponseEntity<ErrorResponse> handleInvalidScore(final InvalidScoreException e) {
        log.warn("Invalid score: {}", e.getMessage());
        return respond(HttpStatus.NOT_ACCEPTABLE, e.getMessage());
    }

    @ExceptionHandler(RoleNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleRoleNotFound(final RoleNotFoundException e) {
        log.error("Role not found", e);
        return respond(HttpStatus.INTERNAL_SERVER_ERROR, e.getMessage());
    }

    @ExceptionHandler(UsernameNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleUsernameNotFound(
            final UsernameNotFoundException e) {
        log.warn("Username not found: {}", e.getMessage());
        return respond(HttpStatus.NOT_FOUND, e.getMessage());
    }

    @ExceptionHandler(GameConflictException.class)
    public ResponseEntity<ErrorResponse> handleGameConflict(final GameConflictException e) {
        return respond(HttpStatus.CONFLICT, e.getMessage());
    }

    @ExceptionHandler(FriendConflictException.class)
    public ResponseEntity<ErrorResponse> handleFriendConflict(final FriendConflictException e) {
        return respond(HttpStatus.CONFLICT, e.getMessage());
    }

    @ExceptionHandler(FriendRequestNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleFriendRequestNotFound(
            final FriendRequestNotFoundException e) {
        return respond(HttpStatus.NOT_FOUND, e.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleBadRequest(final IllegalArgumentException e) {
        return respond(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleUnreadableBody(
            final HttpMessageNotReadableException e) {
        return respond(HttpStatus.BAD_REQUEST, "Invalid request body");
    }

    @ExceptionHandler(GameModeNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleGameModeNotFound(
            final GameModeNotFoundException e) {
        log.warn("Gametype not found: {}", e.getMessage());
        return respond(HttpStatus.NOT_FOUND, e.getMessage());
    }

    @ExceptionHandler(GameNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleGameNotFound(final GameNotFoundException e) {
        log.warn("Game not found: {}", e.getMessage());
        return respond(HttpStatus.NOT_FOUND, e.getMessage());
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(final AccessDeniedException e) {
        log.warn("Access denied: {}", e.getMessage());
        return respond(HttpStatus.FORBIDDEN, e.getMessage());
    }

    @ExceptionHandler(InvalidTurnException.class)
    public ResponseEntity<ErrorResponse> handleInvalidTurn(final InvalidTurnException e) {
        log.warn("Invalid turn: {}", e.getMessage());
        return respond(HttpStatus.FORBIDDEN, e.getMessage());
    }

    // Catch-all for anything not handled above, so every failure shares one shape
    // instead of falling through to the framework's default error body. The stack
    // trace is logged only and never returned to the client.
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGenericException(final Exception e) {
        log.error("Internal error caught: ", e);
        return respond(HttpStatus.INTERNAL_SERVER_ERROR, GENERIC_ERROR_MESSAGE);
    }

    private ResponseEntity<ErrorResponse> respond(final HttpStatus status, final String message) {
        return ResponseEntity.status(status).body(ErrorResponse.of(status, message));
    }
}
