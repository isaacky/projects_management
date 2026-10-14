package com.pmplatform.platform.web;

import com.pmplatform.platform.tenant.MissingTenantException;
import com.pmplatform.platform.user.UnknownUserException;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

/** Maps exceptions to RFC 9457 problem details. */
@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(NotFoundException.class)
    ProblemDetail notFound(NotFoundException e) {
        return problem(HttpStatus.NOT_FOUND, "Not found", e.getMessage());
    }

    @ExceptionHandler(BusinessRuleException.class)
    ProblemDetail businessRule(BusinessRuleException e) {
        return problem(HttpStatus.CONFLICT, "Business rule violated", e.getMessage());
    }

    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    ProblemDetail concurrentEdit(ObjectOptimisticLockingFailureException e) {
        return problem(HttpStatus.CONFLICT, "Changed by someone else",
                "This record was changed by someone else. Reload it and try again.");
    }

    /** Usually two people creating the same numbered record at once; the unique keys stop the duplicate. */
    @ExceptionHandler(DataIntegrityViolationException.class)
    ProblemDetail integrity(DataIntegrityViolationException e) {
        return problem(HttpStatus.CONFLICT, "Saved at the same time",
                "Someone else saved a matching record at the same moment, or a referenced record is missing. "
                        + "Reload and try again.");
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    ProblemDetail tooLarge(MaxUploadSizeExceededException e) {
        return problem(HttpStatus.valueOf(413), "File too large",
                "Each file can be up to 200 MB, and one upload up to 600 MB in total.");
    }

    @ExceptionHandler(UnknownUserException.class)
    ProblemDetail unknownUser(UnknownUserException e) {
        return problem(HttpStatus.FORBIDDEN, "Account not set up", e.getMessage());
    }

    @ExceptionHandler(ForbiddenActionException.class)
    ProblemDetail forbidden(ForbiddenActionException e) {
        return problem(HttpStatus.FORBIDDEN, "Not allowed", e.getMessage());
    }

    @ExceptionHandler(MissingTenantException.class)
    ProblemDetail missingTenant(MissingTenantException e) {
        return problem(HttpStatus.BAD_REQUEST, "Tenant required", e.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ProblemDetail invalid(MethodArgumentNotValidException e) {
        ProblemDetail pd = problem(HttpStatus.BAD_REQUEST, "Validation failed",
                "One or more fields are invalid");
        Map<String, String> errors = new LinkedHashMap<>();
        for (FieldError fe : e.getBindingResult().getFieldErrors()) {
            errors.putIfAbsent(fe.getField(), fe.getDefaultMessage());
        }
        pd.setProperty("errors", errors);
        return pd;
    }

    private static ProblemDetail problem(HttpStatus status, String title, String detail) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(status, detail);
        pd.setTitle(title);
        return pd;
    }
}
