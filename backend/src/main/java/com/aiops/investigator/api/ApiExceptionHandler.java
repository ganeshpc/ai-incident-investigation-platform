package com.aiops.investigator.api;

import com.aiops.investigator.incident.InvalidTransitionException;
import java.util.stream.Collectors;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Turns request failures into RFC 9457 problem documents.
 *
 * <p>A problem document is a small JSON object with a title, a status, and a detail sentence.
 * {@code @RestControllerAdvice} applies these methods to every controller. The client sees the
 * sentence, not a Java stack trace.
 *
 * <p>{@code spring.mvc.problemdetails.enabled} also registers a handler. That handler answers
 * validation with the fixed sentence "Invalid request content." and does not name the field.
 * {@code @Order} puts this class ahead of it, so a missing {@code workloadId} still says which
 * field failed.
 */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ApiExceptionHandler {

  @ExceptionHandler(ApiException.class)
  public ResponseEntity<ProblemDetail> api(ApiException ex) {
    return problem(ex.status(), ex.title(), ex.getMessage());
  }

  @ExceptionHandler(InvalidTransitionException.class)
  public ResponseEntity<ProblemDetail> transition(InvalidTransitionException ex) {
    return problem(HttpStatus.CONFLICT, "Invalid status change", ex.getMessage());
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<ProblemDetail> invalid(MethodArgumentNotValidException ex) {
    String detail =
        ex.getBindingResult().getFieldErrors().stream()
            .map(error -> error.getField() + ": " + error.getDefaultMessage())
            .collect(Collectors.joining("; "));
    if (detail.isBlank()) {
      detail = "The request is invalid.";
    }
    return problem(HttpStatus.BAD_REQUEST, "Invalid request", detail);
  }

  @ExceptionHandler(HttpMessageNotReadableException.class)
  public ResponseEntity<ProblemDetail> unreadable(HttpMessageNotReadableException ex) {
    return problem(
        HttpStatus.BAD_REQUEST,
        "Invalid request",
        "The request body is not valid JSON or a field has the wrong type.");
  }

  private static ResponseEntity<ProblemDetail> problem(
      HttpStatus status, String title, String detail) {
    ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
    problem.setTitle(title);
    return ResponseEntity.status(status).body(problem);
  }
}
