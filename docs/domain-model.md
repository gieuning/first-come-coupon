# 도메인 모델

엔티티는 3개로 시작한다. 쿠폰 이벤트는 기획 단위, 발급된 쿠폰은 개별 기록이다. 둘은 이벤트 1건에 발급 기록 N건이 연결되는 1:N 관계로 나눈다.

## 테이블

### member

| 컬럼 | 타입 | 제약 | 설명 |
|---|---|---|---|
| id | BIGINT | PK, AUTO_INCREMENT | |
| email | VARCHAR(100) | NOT NULL, UNIQUE (`uk_member_email`) | 로그인 식별자 |
| password | VARCHAR(255) | NOT NULL | BCrypt 해시 저장 (평문 금지) |
| nickname | VARCHAR(50) | NOT NULL | 표시용 |
| role | VARCHAR(20) | NOT NULL | `USER` / `ADMIN` — 이벤트 생성은 ADMIN만 |
| created_at / updated_at | DATETIME | NOT NULL | 전 테이블 공통 감사 컬럼 |

### coupon_event — 쿠폰 이벤트 (기획 단위)

| 컬럼 | 타입 | 제약 | 설명 |
|---|---|---|---|
| id | BIGINT | PK, AUTO_INCREMENT | |
| name | VARCHAR(100) | NOT NULL | 이벤트명 |
| total_quantity | INT | NOT NULL | 총 발급 한도 (생성 후 불변) |
| issued_quantity | INT | NOT NULL, DEFAULT 0 | 현재까지 발급된 수 |
| start_at / end_at | DATETIME | NOT NULL | 발급 가능 기간. "몇 시 오픈"이 중요하므로 DATE가 아닌 DATETIME |
| created_at / updated_at | DATETIME | NOT NULL | |

### issued_coupon — 발급된 쿠폰 (개별 기록)

| 컬럼 | 타입 | 제약 | 설명 |
|---|---|---|---|
| id | BIGINT | PK, AUTO_INCREMENT | |
| member_id | BIGINT | NOT NULL | member 참조 (FK 제약 없음) |
| coupon_event_id | BIGINT | NOT NULL | coupon_event 참조 (FK 제약 없음) |
| used | BOOLEAN | NOT NULL, DEFAULT FALSE | 사용 여부 |
| created_at / updated_at | DATETIME | NOT NULL | created_at이 곧 발급 시각 |

복합 유니크: `uk_issued_coupon_member_event UNIQUE (member_id, coupon_event_id)`

## 설계 결정과 이유

### FK 없이 ID 컬럼으로 참조

대규모 트래픽 환경의 실무 관행(락 경합·마이그레이션 부담 감소)을 따라 FK 제약은 두지 않는다. 참조 정합성은 애플리케이션 레이어가 책임진다. JPA 연관관계 매핑도 기본으로 사용하지 않고, 조회에 꼭 필요한 곳에서만 예외적으로 허용한다.

### 1인 1장은 복합 유니크 제약으로 보장

"한 회원은 한 이벤트에서 쿠폰 1장"이라는 규칙은 애플리케이션 검사만으로 보장하기 어렵다. 조회 후 삽입하는 사이에 두 요청이 모두 "발급 이력 없음"을 확인하고 INSERT할 수 있기 때문이다(check-then-act race). DB 유니크 제약은 INSERT 시점에 원자적으로 중복 여부를 판단하므로 마지막 방어선이 된다.

### 유니크 컬럼 순서는 (member_id, coupon_event_id)

유니크 제약만 놓고 보면 컬럼 순서는 상관없다. 다만 이 제약은 복합 인덱스로도 쓰인다. 가장 자주 조회할 "내 쿠폰 목록"은 member_id만 조건으로 사용하므로, 최좌측 접두사 규칙에 맞게 member_id를 앞에 둔다.

### COUNT 대신 issued_quantity 사용

발급 기록을 매번 COUNT하면 기준이 발급 기록 하나뿐이라 정합성을 관리하기 쉽지만, 요청마다 카운트 쿼리를 실행해야 한다. issued_quantity를 따로 두면 조회는 빨라지는 대신 발급 기록 수와 어긋날 가능성이 생긴다. 두 값을 일치시키는 과정이 M2 동시성 학습의 핵심이다. total_quantity는 생성 후 바꾸지 않고 issued_quantity만 증가시키며, 남은 수량은 두 값으로 계산한다.

### 총수량 초과는 애플리케이션에서 방지

유니크 제약이 막는 것은 같은 회원의 중복 발급뿐이다. 서로 다른 회원에게 발급되는 501번째 쿠폰은 현재 스키마만으로 막을 수 없다. M1에서는 조회→비교→증가 순서의 단순한 로직으로 구현하고, M2 부하 테스트에서 문제를 재현한 뒤 단계적으로 해결한다.

### 쿠폰 혜택 컬럼은 나중에 추가

할인 종류나 금액 같은 혜택 정보는 쿠폰 사용 흐름의 요구사항이 정해진 뒤 설계한다. 정액/정률 할인, 최대 할인액, 최소 주문 금액 등이 정해지지 않은 상태에서 미리 만들면 사용 기능을 구현할 때 다시 고칠 가능성이 높다. M1~M2는 선착순 발급과 동시성에 집중하므로 혜택 정보는 요구사항을 정한 뒤 V-N 마이그레이션으로 추가한다.

### soft delete(deleted_at)는 제외

하드 딜리트를 지양하는 실무 관행은 알고 있지만 M1에는 탈퇴나 삭제 기능이 없다. soft delete를 도입하면 모든 조회에 `deleted_at IS NULL` 조건을 적용해야 한다. `uk_member_email`과도 충돌한다. 탈퇴한 회원의 이메일이 계속 남아 있어 같은 이메일로 재가입할 수 없기 때문이다. 탈퇴 기능을 구현할 때 두 문제의 해법까지 함께 설계한다.

### 1인 N장(max_per_member)은 확장 과제로 진행

올리브영식 "1인당 3장" 정책은 실재하지만, N장을 허용하면 현재의 유니크 제약을 사용할 수 없다. DB 안전망 없이 카운트로 검증해야 하며, 동시 요청까지 안전하게 처리하려면 락이나 원자적 연산이 필요하다. 관련 도구를 학습하는 M2 이후에 확장 과제로 진행한다(V2 마이그레이션 예정).
