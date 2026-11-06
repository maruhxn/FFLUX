---
paths:
  - "server/**/src/test/**"
---

# 서버 Kotlin + Spring Boot 코드 컨벤션

## 1. 트랜잭션

| 규칙 | 근거 |
|---|---|
| 커밋 뒤(또는 직전·롤백 뒤)에 할 일은 Spring 이벤트를 발행하고 `@TransactionalEventListener(phase = ...)`로 받는다. `TransactionSynchronizationManager.registerSynchronization`으로 콜백을 직접 등록하지 않는다 | 선언적이라 비즈니스 로직과 후처리가 분리되고 테스트가 쉬움. 내부적으로 같은 메커니즘이라 기능 손실 없음 |
| `AFTER_COMMIT` 리스너에서 DB에 쓰려면 `@Transactional(propagation = REQUIRES_NEW)`를 붙인다 | 커밋 후엔 기존 트랜잭션에 참여할 수 없어 쓰기가 반영 안 됨. Spring 6.1부터는 REQUIRES_NEW / NOT_SUPPORTED 외의 `@Transactional`을 붙이면 기동 시 에러 남 |
| `@Transactional` 메서드를 같은 클래스 내부에서 호출하지 않는다. 필요하면 별도 빈으로 분리한다 | 프록시 기반이라 self-invocation은 프록시를 안 거쳐서 트랜잭션이 적용 안 됨 |
| 조회 전용 메서드는 `@Transactional(readOnly = true)`를 붙인다 | Hibernate가 flush와 dirty checking을 생략함. 읽기 전용 DB 라우팅 구성 시 기준으로도 쓰임 |
| 트랜잭션 안에서 외부 API 호출, 메시지 발행을 하지 않는다 | 응답 대기 동안 DB 커넥션을 붙잡아 커넥션 풀 고갈 원인이 됨. 롤백돼도 외부 호출은 취소 안 됨 |
| 비즈니스 예외는 `RuntimeException`을 상속한다. checked 예외를 던져야 하면 `rollbackFor`를 명시한다 | Spring은 기본적으로 unchecked 예외에만 롤백함. **Kotlin은 checked 구분이 없어서** `IOException` 같은 걸 던져도 컴파일러가 안 잡아주고 조용히 커밋됨 |

## 2. JPA + Kotlin

| 규칙 | 근거 |
|---|---|
| Entity를 `data class`로 만들지 않는다 | 자동 생성되는 `equals/hashCode/toString`이 LAZY 연관관계를 건드려 의도치 않은 쿼리, 순환 참조로 인한 StackOverflow를 일으킬 수 있음 |
| `kotlin("plugin.jpa")`를 적용하고, `allOpen`에 `@Entity`, `@MappedSuperclass`, `@Embeddable`을 등록한다 | JPA는 기본 생성자가 필요하고, LAZY 프록시를 만들려면 클래스가 open이어야 함. Kotlin은 기본이 final |
| `@ManyToOne`, `@OneToOne`은 `fetch = FetchType.LAZY`를 명시한다 | JPA 스펙상 이 둘의 기본값이 EAGER라 N+1의 흔한 원인이 됨 |
| `spring.jpa.open-in-view=false`로 설정한다 | 켜두면 요청 끝까지 커넥션을 유지함. Spring Boot도 기본값(true)일 때 기동 시 경고를 띄움 |
| Controller에서 Entity를 그대로 반환하지 않고 응답 DTO로 변환한다 | API 스펙이 DB 스키마에 묶이고, 직렬화 중 LAZY 로딩이나 순환 참조가 발생함 |

## 3. 의존성 주입 / 설정

| 규칙 | 근거 |
|---|---|
| 의존성은 주 생성자의 `private val`로 주입한다. `@Autowired lateinit var` 필드 주입을 쓰지 않는다 | 불변 보장, 테스트에서 직접 생성 가능, 의존성 과다가 눈에 보임 |
| 설정값은 `@ConfigurationProperties` + data class로 묶는다. `@Value`를 흩뿌리지 않는다 | 타입 안전, 그룹화, 검증(`@Validated`) 가능. Boot 3부터 생성자가 하나면 `@ConstructorBinding` 없이 불변 바인딩됨 |
| 현재 시간은 `Clock`을 주입받아 쓴다. `LocalDateTime.now()`를 직접 호출하지 않는다 | 테스트에서 시간 고정이 가능해짐 |

## 4. API / 예외 / 외부 호출

| 규칙 | 근거 |
|---|---|
| 예외 → 응답 변환은 `@RestControllerAdvice` 한 곳에서 처리하고 `ProblemDetail`을 쓴다 | 응답 형식 통일. Spring 6부터 표준 에러 포맷(RFC 7807 계열)을 기본 지원 |
| 새로 작성하는 동기 HTTP 클라이언트는 `RestClient`를 쓴다 | Spring 6.1에서 추가된 fluent API. `RestTemplate`은 유지보수 모드 |

## 5. Kotlin 코드 스타일 / 테스트

| 규칙 | 근거 |
|---|---|
| 프로덕션 코드에서 `!!`를 쓰지 않는다. `?:`로 명시적 예외를 던진다 | `!!`는 원인 없는 NPE를 냄. `?: throw NotFoundException(id)`가 디버깅에 훨씬 유리 |
| 로그 메시지에 문자열 템플릿 대신 플레이스홀더(`{}`)나 람다 로깅을 쓴다 | 템플릿은 로그 레벨이 꺼져 있어도 문자열을 먼저 만듦 |
| 계층별 테스트는 `@WebMvcTest`, `@DataJpaTest` 같은 슬라이스 테스트를 우선한다. `@SpringBootTest`는 통합 시나리오에만 쓴다 | 전체 컨텍스트 로딩은 느리고 실패 원인 파악이 어려움 |