package com.zarrix.releasemanager.api

import com.zarrix.releasemanager.domain.UnknownSystemVersionException
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.HttpStatusCode
import org.springframework.http.ProblemDetail
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.context.request.WebRequest
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler

@RestControllerAdvice
class ApiExceptionHandler : ResponseEntityExceptionHandler() {

    @ExceptionHandler(UnknownSystemVersionException::class)
    fun handleUnknownSystemVersion(exception: UnknownSystemVersionException): ProblemDetail =
        ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, exception.message)

    override fun handleMethodArgumentNotValid(
        exception: MethodArgumentNotValidException,
        headers: HttpHeaders,
        status: HttpStatusCode,
        request: WebRequest,
    ): ResponseEntity<Any>? {
        val violations = exception.fieldErrors
            .sortedBy { it.field }
            .joinToString("; ") { "${it.field}: ${it.defaultMessage}" }
        val problem = ProblemDetail.forStatusAndDetail(status, violations)
        return handleExceptionInternal(exception, problem, headers, status, request)
    }
}
