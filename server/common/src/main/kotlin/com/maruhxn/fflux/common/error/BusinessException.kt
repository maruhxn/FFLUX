package com.maruhxn.fflux.common.error

/**
 * 모듈이 던지는 비즈니스 예외. `app`의 전역 예외 처리기가 종류별로 HTTP 상태와 ProblemDetail로 바꾼다.
 * [message]는 그대로 응답의 `detail`이 되므로 사용자에게 보여도 되는 문장만 담는다.
 */
sealed class BusinessException(override val message: String) : RuntimeException(message)

/** 400: 요청 값이 규칙에 맞지 않음. [errors](칸 이름 → 메시지)는 응답의 `errors`로 나간다. */
class BadRequestException(message: String, val errors: Map<String, String> = emptyMap()) : BusinessException(message) {

    /** 한 칸([field])이 틀렸을 때. */
    constructor(message: String, field: String) : this(message, mapOf(field to message))

    companion object {
        /**
         * 여러 칸의 값 객체를 만든 결과를 모아, 하나라도 [BadRequestException]으로 실패했으면 모든 칸의 에러를 담아 한 번에 던진다.
         * 그 밖의 예외는 그대로 다시 던진다. 통과하면 각 결과에서 `getOrThrow()`로 값을 꺼낸다.
         */
        fun throwIfAny(results: List<Result<*>>) {
            val failures = results.mapNotNull { it.exceptionOrNull() }
            if (failures.isEmpty()) return
            failures.firstOrNull { it !is BadRequestException }?.let { throw it }
            val badRequests = failures.filterIsInstance<BadRequestException>()
            throw BadRequestException(badRequests.first().message, badRequests.fold(emptyMap()) { acc, e -> acc + e.errors })
        }
    }
}

/** 401: 로그인이 필요함. */
class UnauthorizedException(message: String) : BusinessException(message)

/** 403: 스트리머·작성자가 아님. */
class ForbiddenException(message: String) : BusinessException(message)

/** 404: 대상이 없음. */
class NotFoundException(message: String) : BusinessException(message)

/** 409: 현재 상태에서 할 수 없는 요청. */
class ConflictException(message: String) : BusinessException(message)
