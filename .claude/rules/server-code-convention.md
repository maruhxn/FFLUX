---
paths:
  - "server/**"
---

# 서버 Kotlin + Spring Boot 코드 컨벤션

## 1. 트랜잭션

| 규칙 | 근거 |
|---|---|
| 커밋 뒤(또는 직전·롤백 뒤)에 할 일은 Spring 이벤트를 발행하고 `@TransactionalEventListener(phase = ...)`로 받는다. `TransactionSynchronizationManager.registerSynchronization`으로 콜백을 직접 등록하지 않는다 | 선언적이라 비즈니스 로직과 후처리가 분리되고 테스트가 쉬움. 내부적으로 같은 메커니즘이라 기능 손실 없음 |
| `AFTER_COMMIT` 리스너에서 DB에 쓰려면 `@Transactional(propagation = REQUIRES_NEW)`를 붙인다 | 커밋 후엔 기존 트랜잭션에 참여할 수 없어 쓰기가 반영 안 됨. Spring 6.1부터는 REQUIRES_NEW / NOT_SUPPORTED 외의 `@Transactional`을 붙이면 기동 시 에러 남 |
| `AFTER_COMMIT` 리스너가 다른 빈의 provided 유스케이스를 호출할 때도 그 쓰기가 새 트랜잭션에서 실행되게 한다(리스너에 `REQUIRES_NEW`를 붙이거나 호출 대상이 `REQUIRES_NEW`로 열리게) | 커밋이 끝난 트랜잭션에 `REQUIRED`로 참여하면 예외 없이 쓰기가 사라짐. 모듈 간 비동기 전파가 모두 이 경로라 놓치기 쉬움 |
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
| enum 필드는 `@Enumerated(EnumType.STRING)`으로 저장한다 | 기본값 ORDINAL은 enum 순서가 바뀌면 저장된 값의 의미가 달라짐 |
| 조건부 UPDATE(상태 전이, 토큰 소비, 후원 확정)는 `@Modifying(clearAutomatically = true, flushAutomatically = true)` 쿼리로 쓰고, 반환한 영향 행 수(`Int`)로 성공 여부를 판단한다 | 벌크 UPDATE는 영속성 컨텍스트를 거치지 않아, 옵션 없이 쓰면 같은 트랜잭션에서 다시 조회할 때 바뀌기 전 엔티티가 보임 |
| soft delete 조건 `deleted_at IS NULL`은 쿼리마다 직접 쓴다. Hibernate `@SQLRestriction` 같은 자동 필터는 쓰지 않는다 | 조건부 UPDATE에는 어차피 직접 써야 하고, 자동 필터와 섞이면 어디에 조건이 걸렸는지 알기 어려움 |
| 스키마는 Flyway 마이그레이션으로 관리하고 `spring.jpa.hibernate.ddl-auto=validate`로 둔다. 적용된 마이그레이션 파일은 고치지 않고 새 버전을 추가한다 | 재시작 후에도 데이터가 남는 운영 DB를 다룸. `update`는 컬럼 삭제·이름 변경을 반영하지 못하고 변경 이력이 남지 않음 |

## 3. 쿼리 작성

| 규칙 | 근거 |
|---|---|
| 단순 조회는 Spring Data 메서드 이름 쿼리, 고정된 조건부 UPDATE와 짧은 고정 쿼리는 `@Query`(JPQL)로 쓴다 | 가장 짧고, 쿼리가 메서드 선언 옆에 보임 |
| 조건이 입력에 따라 달라지는 동적 쿼리, 커서 페이지네이션, 여러 조건 조합은 QueryDSL로 쓴다. 문자열을 이어 붙여 JPQL을 만들지 않는다 | 컴파일 시점에 필드 이름과 타입을 검사함. 문자열 조합은 오타와 인젝션 위험이 있음 |
| QueryDSL은 유지보수되는 OpenFeign 포크(`io.github.openfeign.querydsl`)를 쓰고, Q 클래스는 KSP로 생성한다 | 원본 `com.querydsl`은 갱신이 멈춤. kapt는 유지보수 모드라 Kotlin에서는 KSP가 맞음. 도입 시점에 Spring Boot·Hibernate 버전과의 호환을 확인한다 |
| QueryDSL 코드는 `port.persistence`에만 둔다. Spring Data 커스텀 리포지토리(`XxxRepositoryCustom` + `XxxRepositoryImpl`)로 만들어 required 포트를 구현하는 리포지토리가 상속하게 한다 | application과 domain이 쿼리 기술을 모르게 유지함(헥사고날 의존 방향) |
| Q 클래스는 domain 패키지에 생성되므로 `ArchitectureTest`의 domain 의존 규칙에서 생성된 Q 클래스를 제외한다 | Q 클래스는 `com.querydsl`을 참조해 domain 허용 목록 규칙에 걸림 |

## 4. 의존성 주입 / 설정

| 규칙 | 근거 |
|---|---|
| 의존성은 주 생성자의 `private val`로 주입한다. `@Autowired lateinit var` 필드 주입을 쓰지 않는다 | 불변 보장, 테스트에서 직접 생성 가능, 의존성 과다가 눈에 보임 |
| 설정값은 `@ConfigurationProperties` + data class로 묶는다. `@Value`를 흩뿌리지 않는다 | 타입 안전, 그룹화, 검증(`@Validated`) 가능. Boot 3부터 생성자가 하나면 `@ConstructorBinding` 없이 불변 바인딩됨 |
| 현재 시간은 `Clock`을 주입받아 쓴다. `LocalDateTime.now()`를 직접 호출하지 않고 `LocalDateTime.now(clock)`을 쓴다 | 테스트에서 시간 고정이 가능해짐 |
| 시간대는 `Asia/Seoul` 하나로 고정한다: JVM 기본 시간대, `Clock` 빈, MySQL `time_zone`, Hibernate `hibernate.jdbc.time_zone`. 시각 타입은 `LocalDateTime`을 쓴다 | 저장한 시각과 현재 시각을 비교하는 곳(토큰 만료 등)에서 시간대가 하나라도 어긋나면 9시간 차이가 남 |

## 5. API / 예외 / 외부 호출

| 규칙 | 근거 |
|---|---|
| 예외 → 응답 변환은 `@RestControllerAdvice` 한 곳에서 처리하고 `ProblemDetail`을 쓴다 | 응답 형식 통일. Spring 6부터 표준 에러 포맷(RFC 7807 계열)을 기본 지원 |
| 새로 작성하는 동기 HTTP 클라이언트는 `RestClient`를 쓴다 | Spring 6.1에서 추가된 fluent API. `RestTemplate`은 유지보수 모드 |
| 송출 토큰, 비밀번호, 결제 시크릿 키, 세션 ID는 로그와 예외 메시지에 남기지 않는다 | 로그는 저장소 밖으로 퍼지기 쉬움. 프로젝트 보안 규칙을 코드 수준에서 지키기 위함 |

## 6. Kotlin 코드 스타일 / 테스트

| 규칙 | 근거 |
|---|---|
| 프로덕션 코드에서 `!!`를 쓰지 않는다. `?:`로 명시적 예외를 던진다 | `!!`는 원인 없는 NPE를 냄. `?: throw NotFoundException(id)`가 디버깅에 훨씬 유리 |
| 로그 메시지에 문자열 템플릿 대신 플레이스홀더(`{}`)나 람다 로깅을 쓴다 | 템플릿은 로그 레벨이 꺼져 있어도 문자열을 먼저 만듦 |
| 인가 매트릭스, 방송 상태 전이, 송출 토큰 소비, 후원 멱등은 Testcontainers MySQL로 앱 전체를 띄워 HTTP 경계(`/api/**`, `/internal/**`)에서 검증한다(`.claude/rules/server-testing.md`). 그 밖의 계층별 검증은 `@WebMvcTest`, `@DataJpaTest` 같은 슬라이스 테스트를 우선한다 | 권한과 상태 규칙은 Security 필터, 조건부 UPDATE, 유니크 제약이 실제로 맞물려야 의미가 있음. 그 외에는 전체 컨텍스트 로딩이 느리고 실패 원인 파악이 어려움 |
| 앱 전체를 띄우는 통합 테스트는 테스트마다 테이블을 비워(truncate) 격리한다. 테스트 메서드에 `@Transactional`을 붙여 롤백에 기대지 않는다 | HTTP로 호출하면 요청이 다른 스레드·트랜잭션에서 실행돼 테스트 롤백이 적용되지 않음 |
| Testcontainers MySQL 컨테이너는 테스트 전체에서 하나만 띄워 재사용하고 `@ServiceConnection`으로 연결한다 | 테스트 클래스마다 컨테이너를 띄우면 느림 |
| 테스트 이름은 백틱으로 동작을 한국어로 적는다. 예: `` `READY 방송을 종료하면 409` `` | 인가 매트릭스처럼 비슷한 테스트가 많을 때 실패한 항목을 바로 알아볼 수 있음 |
