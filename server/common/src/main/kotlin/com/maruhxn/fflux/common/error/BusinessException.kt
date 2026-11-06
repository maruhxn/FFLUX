package com.maruhxn.fflux.common.error

/**
 * 모듈이 던지는 비즈니스 예외. `app`의 전역 예외 처리기가 종류별로 HTTP 상태와 ProblemDetail로 바꾼다.
 * [message]는 그대로 응답의 `detail`이 되므로 사용자에게 보여도 되는 문장만 담는다.
 */
sealed class BusinessException(override val message: String) : RuntimeException(message)

/** 400: 요청 값이 규칙에 맞지 않음. */
class BadRequestException(message: String) : BusinessException(message)

/** 401: 로그인이 필요함. */
class UnauthorizedException(message: String) : BusinessException(message)

/** 403: 스트리머·작성자가 아님. */
class ForbiddenException(message: String) : BusinessException(message)

/** 404: 대상이 없음. */
class NotFoundException(message: String) : BusinessException(message)

/** 409: 현재 상태에서 할 수 없는 요청. */
class ConflictException(message: String) : BusinessException(message)
