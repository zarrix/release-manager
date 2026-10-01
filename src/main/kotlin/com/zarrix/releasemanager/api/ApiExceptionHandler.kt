package com.zarrix.releasemanager.api

import com.zarrix.releasemanager.domain.UnknownSystemVersionException
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.HttpStatusCode
import org.springframework.http.ProblemDetail
import org.springframework.http.ResponseEntity
import org.springframework.web.ErrorResponse
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.context.request.WebRequest
import org.springframework.web.method.annotation.HandlerMethodValidationException
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler
import java.net.URI

@RestControllerAdvice
class ApiExceptionHandler : ResponseEntityExceptionHandler() {

    @ExceptionHandler(UnknownSystemVersionException::class)
    fun handleUnknownSystemVersion(exception: UnknownSystemVersionException): ProblemDetail =
        ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, exception.message).apply {
            type = UNKNOWN_SYSTEM_VERSION
            setProperty("environment", exception.environment.name)
            setProperty("systemVersion", exception.systemVersion.value)
        }

    override fun handleMethodArgumentNotValid(
        exception: MethodArgumentNotValidException,
        headers: HttpHeaders,
        status: HttpStatusCode,
        request: WebRequest,
    ): ResponseEntity<Any>? {
        val invalidFields = exception.fieldErrors
            .sortedBy { it.field }
            .associate { it.field to (it.defaultMessage ?: "is invalid") }
        return handleExceptionInternal(exception, invalidRequest(invalidFields), headers, status, request)
    }

    override fun handleHandlerMethodValidationException(
        exception: HandlerMethodValidationException,
        headers: HttpHeaders,
        status: HttpStatusCode,
        request: WebRequest,
    ): ResponseEntity<Any>? {
        val invalidFields = exception.parameterValidationResults
            .associate { result ->
                result.methodParameter.parameterName.orEmpty() to
                    result.resolvableErrors.joinToString("; ") { it.defaultMessage ?: "is invalid" }
            }
        return handleExceptionInternal(exception, invalidRequest(invalidFields), headers, status, request)
    }

    override fun handleExceptionInternal(
        exception: Exception,
        body: Any?,
        headers: HttpHeaders,
        status: HttpStatusCode,
        request: WebRequest,
    ): ResponseEntity<Any>? {
        val problem = body ?: (exception as? ErrorResponse)?.body
        if (problem is ProblemDetail && status == HttpStatus.BAD_REQUEST) {
            problem.type = INVALID_REQUEST
        }
        return super.handleExceptionInternal(exception, problem, headers, status, request)
    }

    private fun invalidRequest(invalidFields: Map<String, String>): ProblemDetail {
        val detail = invalidFields.entries.joinToString("; ") { "${it.key}: ${it.value}" }
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, detail).apply {
            setProperty("invalidFields", invalidFields)
        }
    }

    private companion object {
        val INVALID_REQUEST: URI = URI.create("/errors/invalid_request")
        val UNKNOWN_SYSTEM_VERSION: URI = URI.create("/errors/unknown_system_version")
    }
}
