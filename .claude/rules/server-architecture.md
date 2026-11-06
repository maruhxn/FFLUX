---
paths:
  - "server/**"
---

# 서버 아키텍처: 모듈러 모놀리스 + 헥사고날

## 모듈

```
server/
├─ app/        # @SpringBootApplication, Security·설정, 모듈 간 어댑터(조립 지점)
├─ common/     # shared kernel: 이벤트 계약, 공통 예외, LoginMember·@PublicApi
└─ modules/{member, broadcast, recording, chat, comment, donation, realtime}
```

- `modules/*`의 Gradle 의존은 `common` 하나뿐이다. 다른 모듈이 필요하면 아래 두 통로 중 하나를 쓴다.
  - **동기 조회**: 필요한 쪽이 자기 `application.required`에 포트를 선언하고, `app`의 어댑터가 상대 모듈의 `application.provided`를 호출해 구현한다.
  - **비동기 전파**: `common`의 이벤트 계약(원시 타입과 ID만 담는다)을 `EventPublisherPort`로 발행하고, 받는 모듈의 `port.event`에서 `@TransactionalEventListener(AFTER_COMMIT)`로 받아 자기 provided 유스케이스를 호출한다.
- 모듈 경계를 넘는 참조는 ID 값(`streamerId: Long` 등)으로만 한다. 다른 모듈 엔티티와의 JPA 연관관계와 DB FK는 두지 않는다. 같은 모듈 안에서는 연관관계와 FK를 써도 된다.
- 모듈 배치: MediaMTX 인증 콜백·상태 훅·kick은 `broadcast`, 녹화 완료 훅은 `recording`, 토스 연동은 `donation`, SSE 허브는 `realtime`, Spring Security 설정은 `app`.

## 모듈 내부 패키지

```
com.maruhxn.fflux.<module>
├─ port
│  ├─ web            # Controller, request/response DTO
│  ├─ persistence    # Spring Data 리포지토리 (required 포트 구현)
│  ├─ event          # 이벤트 리스너 (인바운드)
│  └─ <external>     # mediamtx, toss 등 외부 클라이언트 / internal 훅 컨트롤러
├─ application
│  ├─ provided       # 유스케이스 인터페이스 + Command/Result DTO
│  ├─ required       # 아웃바운드 포트 인터페이스
│  └─ (root)         # 유스케이스 구현 서비스, @Transactional
└─ domain            # JPA 엔티티(=도메인 모델), enum, 값 객체, 도메인 예외
```

- 의존은 항상 밖(port)에서 안(domain)으로 흐른다. port는 application을 거쳐 domain에 닿는다.
- domain의 엔티티는 JPA `@Entity`를 그대로 도메인 모델로 쓴다. 상태 전이와 불변식 검증은 엔티티 메서드에 둔다.
- provided 유스케이스는 Result DTO를 반환한다. 엔티티는 application 밖으로 내보내지 않는다.
- 영속성 어댑터는 `interface BroadcastJpaRepository : JpaRepository<Broadcast, Long>, BroadcastRepository`처럼 required 포트를 상속한 Spring Data 인터페이스 하나로 끝낸다.
- 권한 확인(스트리머·작성자)은 application 서비스에서 한다. 현재 사용자는 web 포트가 `common`의 `member: LoginMember` 파라미터로 받아 Command에 담아 넘긴다.

## ArchUnit 규칙

`app` 모듈의 `ArchitectureTest`가 아래를 강제한다. 새 패키지나 모듈을 추가하면 이 테스트가 통과하는지 확인한다.

1. domain은 port, application을 참조하지 않는다.
2. domain은 Kotlin/Java 표준 라이브러리(Kotlin 컴파일러가 붙이는 `org.jetbrains.annotations` 포함), `jakarta.persistence`, Hibernate, `org.springframework..`, `common`, 자기 모듈 domain만 참조한다. 단, KSP가 생성한 QueryDSL Q 클래스는 이 규칙에서 제외한다.
3. application은 port를 참조하지 않는다.
4. `port.web`은 `application.provided`를 통해서만 유스케이스를 쓴다(`application.required` 참조 금지).
5. `port.persistence` 외의 port는 `@Entity` 클래스를 참조하지 않는다. enum, 값 객체 같은 그 밖의 domain 클래스는 직접 참조해도 된다.
6. 모듈끼리 서로 참조하지 않는다(`common`, `app` 제외).
7. `app`의 어댑터는 각 모듈의 `application.provided`/`application.required`만 참조한다.

## 공통 규약

- 에러 응답은 `ProblemDetail` + `@RestControllerAdvice` 하나로 통일한다: 400 검증 실패, 401 비로그인, 403 스트리머·작성자 아님, 404 대상 없음, 409 상태 위반.
- 상태 변경은 두 가지로 나눈다.
  - **판단이 필요한 상태 전이**(방송 상태 전이): 대상 행을 `SELECT … FOR UPDATE`로 잠근 뒤 엔티티 메서드가 "전이 / 이미 목표 상태(no-op) / 위반"을 판단하고 저장한다. 이미 목표 상태면 no-op으로 성공한다.
  - **값 하나를 한 번만 바꾸는 단순 갱신**(송출 토큰 1회 소비, 후원 확정 `PENDING → DONE`): 조건부 UPDATE(`WHERE <기대 상태>`)로 하고 영향 행 수로 성공 여부를 판단한다.
- 외부 요청 경로: 공개 API는 `/api/**`, MediaMTX 훅·콜백은 `/internal/**`. `/internal/**`은 Docker 내부망에서만 닿는다는 전제로 둔다.
