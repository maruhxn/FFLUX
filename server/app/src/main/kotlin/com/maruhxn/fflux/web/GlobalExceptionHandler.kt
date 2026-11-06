package com.maruhxn.fflux.web

import com.maruhxn.fflux.common.error.BadRequestException
import com.maruhxn.fflux.common.error.BusinessException
import com.maruhxn.fflux.common.error.ConflictException
import com.maruhxn.fflux.common.error.ForbiddenException
import com.maruhxn.fflux.common.error.NotFoundException
import com.maruhxn.fflux.common.error.UnauthorizedException
import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler

/**
 * 모든 에러 응답을 ProblemDetail 하나로 통일한다.
 * 검증 실패 같은 Spring MVC 표준 예외는 [ResponseEntityExceptionHandler]가 400 ProblemDetail로 바꾼다.
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
        return ProblemDetail.forStatusAndDetail(status, e.message)
    }
}
