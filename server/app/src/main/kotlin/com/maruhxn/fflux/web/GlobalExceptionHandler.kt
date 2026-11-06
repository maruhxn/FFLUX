package com.maruhxn.fflux.web

import com.maruhxn.fflux.common.error.BadRequestException
import com.maruhxn.fflux.common.error.BusinessException
import com.maruhxn.fflux.common.error.ConflictException
import com.maruhxn.fflux.common.error.ForbiddenException
import com.maruhxn.fflux.common.error.NotFoundException
import com.maruhxn.fflux.common.error.UnauthorizedException
import org.springframework.dao.DataIntegrityViolationException
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

/**
 * 모든 에러 응답을 ProblemDetail 하나로 통일한다.
 * Spring MVC 표준 예외는 [ResponseEntityExceptionHandler]가 ProblemDetail로 바꾸고,
 * 요청 본문 검증 실패는 어느 칸이 틀렸는지 `errors`(필드 → 메시지)로 함께 내려준다.
 */
@RestControllerAdvice
class GlobalExceptionHandler : ResponseEntityExceptionHandler() {

    @ExceptionHandler(BusinessException::class)
    fun handleBusinessException(e: BusinessException): ProblemDetail {
        val status = when (e) {
            is BadRequestException -> HttpStatus.BAD_REQUEST
            is UnauthorizedException -> HttpStatus.UNAUTHORIZED
            is ForbiddenException -> HttpStatus.FORBIDDEN
            is NotFoundException -> HttpStatus.NOT_FOUND
            is ConflictException -> HttpStatus.CONFLICT
        }
        val problem = ProblemDetail.forStatusAndDetail(status, e.message)
        if (e is BadRequestException && e.errors.isNotEmpty()) problem.setProperty("errors", e.errors)
        return problem
    }

    /** 동시에 같은 값을 넣어 유니크 제약에 걸린 경우(예: 같은 아이디로 동시 가입). */
    @ExceptionHandler(DataIntegrityViolationException::class)
    fun handleDataIntegrityViolation(e: DataIntegrityViolationException): ProblemDetail =
        ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, "이미 존재하는 값입니다.")

    override fun handleMethodArgumentNotValid(
        ex: MethodArgumentNotValidException,
        headers: HttpHeaders,
        status: HttpStatusCode,
        request: WebRequest,
    ): ResponseEntity<Any>? {
        val errors = ex.bindingResult.fieldErrors.associate { it.field to (it.defaultMessage ?: "값이 올바르지 않습니다.") }
        val problem = ProblemDetail.forStatusAndDetail(status, errors.values.firstOrNull() ?: "요청 값이 올바르지 않습니다.")
        problem.setProperty("errors", errors)
        return handleExceptionInternal(ex, problem, headers, status, request)
    }
}
