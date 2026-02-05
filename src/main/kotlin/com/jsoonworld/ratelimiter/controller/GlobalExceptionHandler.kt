package com.jsoonworld.ratelimiter.controller

import com.jsoonworld.ratelimiter.exception.UnsupportedAlgorithmException
import com.jsoonworld.ratelimiter.model.RateLimitAlgorithm
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException
import org.springframework.web.server.ServerWebInputException

@RestControllerAdvice
class GlobalExceptionHandler {

    private val logger = LoggerFactory.getLogger(javaClass)

    @ExceptionHandler(UnsupportedAlgorithmException::class)
    fun handleUnsupportedAlgorithm(ex: UnsupportedAlgorithmException): ResponseEntity<ErrorResponse> {
        logger.warn("Unsupported algorithm requested: ${ex.algorithm.name}")

        return ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .body(
                ErrorResponse(
                    error = "UNSUPPORTED_ALGORITHM",
                    message = ex.message ?: "Unsupported algorithm",
                    requestedAlgorithm = ex.algorithm.name,
                    supportedAlgorithms = ex.supportedAlgorithms.map { it.name }
                )
            )
    }

    @ExceptionHandler(IllegalArgumentException::class)
    fun handleIllegalArgument(ex: IllegalArgumentException): ResponseEntity<ErrorResponse> {
        logger.warn("Invalid request: ${ex.message}")

        return ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .body(
                ErrorResponse(
                    error = "INVALID_REQUEST",
                    message = ex.message ?: "Invalid request"
                )
            )
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException::class)
    fun handleTypeMismatch(ex: MethodArgumentTypeMismatchException): ResponseEntity<ErrorResponse> {
        val paramName = ex.name
        val invalidValue = ex.value?.toString() ?: "null"

        logger.warn("Invalid parameter '$paramName': $invalidValue")

        val message = if (ex.requiredType == RateLimitAlgorithm::class.java) {
            "Invalid algorithm '$invalidValue'. Supported algorithms: ${RateLimitAlgorithm.implementedAlgorithms().map { it.name }}"
        } else {
            "Invalid value '$invalidValue' for parameter '$paramName'"
        }

        return ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .body(
                ErrorResponse(
                    error = "INVALID_PARAMETER",
                    message = message
                )
            )
    }

    @ExceptionHandler(ServerWebInputException::class)
    fun handleWebInputException(ex: ServerWebInputException): ResponseEntity<ErrorResponse> {
        val reason = ex.reason ?: "Invalid input"
        logger.warn("Invalid web input: $reason")

        val message = if (reason.contains("RateLimitAlgorithm")) {
            "Invalid algorithm value. Supported algorithms: ${RateLimitAlgorithm.implementedAlgorithms().map { it.name }}"
        } else {
            reason
        }

        return ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .body(
                ErrorResponse(
                    error = "INVALID_PARAMETER",
                    message = message
                )
            )
    }

    @ExceptionHandler(Exception::class)
    fun handleGenericException(ex: Exception): ResponseEntity<ErrorResponse> {
        logger.error("Unexpected error occurred", ex)

        return ResponseEntity
            .status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(
                ErrorResponse(
                    error = "INTERNAL_ERROR",
                    message = "An unexpected error occurred"
                )
            )
    }
}

data class ErrorResponse(
    val error: String,
    val message: String,
    val requestedAlgorithm: String? = null,
    val supportedAlgorithms: List<String>? = null
)
