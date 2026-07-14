# 백 환경 기준
- Java 17
- Temurin JDK 17
- Spring Boot 4.0.6
- Gradle
- 로컬 DB: Docker PostgreSQL
- 개발 서버 DB: H2 또는 서버 환경 설정 기준
- 운영 또는 추후 개발 DB: RDS 예정
---

# 로컬 개발 DB

로컬 개발은 `local` profile과 Docker PostgreSQL을 사용한다.

```bash
docker compose -f docker-compose.local.yml up -d postgres
./gradlew bootRun --args='--spring.profiles.active=local'
```

기본 연결 정보는 다음과 같다.

| 항목 | 기본값 |
| --- | --- |
| Host | `localhost` |
| Port | `5433` |
| Database | `tour_local` |
| Username | `tour` |
| Password | `tour` |

`5432` 포트는 다른 로컬 프로젝트에서 이미 사용할 수 있으므로 이 프로젝트의 기본 host port는 `5433`으로 둔다. 필요하면 `.env`에서 `LOCAL_POSTGRES_PORT`를 바꾼다.

환경변수 예시는 `.env.example`을 기준으로 만들고, 실제 `.env`는 Git에 올리지 않는다.

## 로컬 Swagger

Swagger UI와 OpenAPI JSON은 `local` profile에서만 활성화한다. 로컬 DB가 비어 있으면
기본값으로 일정 생성용 장소 fixture 8개를 적재하고, 질문과 답변 seed도 함께 준비한다.

```bash
docker compose -f docker-compose.local.yml up -d postgres
./gradlew bootRun --args='--spring.profiles.active=local'
```

- Swagger UI: `http://localhost:8080/swagger-ui.html`
- OpenAPI JSON: `http://localhost:8080/v3/api-docs`

Swagger의 `POST /api/v1/schedules`에는 자동 추천 1일 일정과 현재 로컬 장소 ID를
동적으로 연결한 3박 4일 일정 예제가 있다. `mustVisitPlaceIds`에 장소를 직접 지정하려면 먼저
`GET /api/v1/places`를 실행해 현재 로컬 DB의 장소 ID를 사용한다. 기존 장소 데이터가
있으면 fixture를 추가 적재하지 않는다. 더미 장소를 끄려면
`DUMMY_PLACE_SEED_ENABLED=false`를 설정한다.

백엔드를 `18080` 포트로 실행하면 Swagger URL의 포트도 `18080`으로 변경한다.

TourAPI 장소 적재는 기본 비활성화 상태다. 로컬 PostgreSQL에 적재하려면 `.env`에 `TOUR_API_KEY`를 설정하고 필요한 페이지 수를 조정한 뒤 실행한다.

```bash
TOUR_API_PLACE_INGESTION_ENABLED=true \
TOUR_API_MAX_PAGES=1 \
TOUR_API_MAX_REQUESTS_PER_DAY=900 \
./gradlew bootRun --args='--spring.profiles.active=local'
```

기본값은 부산 `areaCode=6`, content type `12,14,15,28,32,38,39`, 페이지당 100건이다. 일일 요청 한도 1,000건 중 100건을 운영 여유로 남기고 적재에는 최대 900건을 예약한다. API Key가 로그나 문서에 남지 않도록 전체 요청 URL을 기록하지 않는다.

장소 적재는 매일 전체 상세정보를 다시 저장하지 않는다. 먼저 `areaBasedList2`의 `modifiedtime`과 기본정보를 확인하고 신규·변경·재시도 시각이 도래한 실패 장소만 `detailCommon2`, `detailIntro2`, `detailImage2`로 보강한다. 변경 없는 장소는 `last_seen_at`만 갱신한다. 상세 동기화 실패는 1일, 2일, 4일, 최대 7일 간격으로 재시도한다. 날짜별 요청 사용량은 DB에 원자적으로 예약하므로 같은 날 재시작하거나 작업이 재실행되어도 설정된 한도를 넘지 않는다.

개발 서버는 시작 시 적재인 `TOUR_API_PLACE_INGESTION_ENABLED`를 끄고, `TOUR_API_INGESTION_SCHEDULER_ENABLED=true`로 매일 04:00 KST 증분 동기화만 실행한다. 시작 시 적재를 켜면 배포할 때마다 일일 예산을 사용할 수 있으므로 개발 배포에서는 사용하지 않는다.

## TourAPI 공급자 계약

연동 기준은 공공데이터포털의 `한국관광공사_국문 관광정보 서비스_GW`와
`https://apis.data.go.kr/B551011/KorService2` 계약이다.

| 오퍼레이션 | 필수 조회 조건 | 현재 사용하는 선택 조건 |
| --- | --- | --- |
| `areaBasedList2` | `serviceKey`, `MobileOS`, `MobileApp` | `_type`, `areaCode`, `contentTypeId`, `pageNo`, `numOfRows`, `arrange` |
| `detailCommon2` | `serviceKey`, `MobileOS`, `MobileApp`, `contentId` | `_type` |
| `detailIntro2` | `serviceKey`, `MobileOS`, `MobileApp`, `contentId`, `contentTypeId` | `_type` |
| `detailImage2` | `serviceKey`, `MobileOS`, `MobileApp`, `contentId` | `_type`, `imageYN` |

`detailCommon2`에는 구 계약의 `contentTypeId`, `defaultYN`, `firstImageYN`,
`areacodeYN`, `catcodeYN`, `addrinfoYN`, `mapinfoYN`, `overviewYN`을 전달하지 않는다.
`detailImage2`에는 구 계약의 `subImageYN`을 전달하지 않는다.

공급자 오류는 정상 응답의 `response.header.resultCode` 또는 요청 검증 오류의 최상위
`resultCode`로 반환될 수 있다. 두 형식 모두 `0000`만 성공으로 처리한다.

로컬 DB 컨테이너를 중지하려면 다음을 사용한다.

```bash
docker compose -f docker-compose.local.yml down
```

데이터 볼륨까지 제거하려면 다음을 사용한다.

```bash
docker compose -f docker-compose.local.yml down -v
```

# application-prod.yml

운영 환경 설정 파일

이 파일에는 DB 주소, 계정, 비밀번호 등 민감 정보가 들어갈 수 있으므로 Git에 올리지 않음

.gitignore에 다음 항목을 추가

src/main/resources/application-prod.yml

---

현재 백엔드 서버는 Docker 이미지로 빌드한 뒤, 개발 서버 VM에서 컨테이너로 실행

---

# Nginx reverse proxy

user/front --> nginx(80) --> springboot_container(8080)
cors-->nginx에서 처리할 예정

---

# 깃헙CICD

- github/workflows/deploy-dev.yml
  - 개발 서버만 구성, 배포 서버는 추후 GCP로 할 예정 마이그레이션 할 예정임


---

# naming & vm

추후 aws --> gcp 로 이전 할 예정임

현재 구조 : Linux VM Docker Nginx Docker Hub GitHub Actions SSH 배포

추후 마이그레이션시 값 변경 : 

VM IP 주소

SSH 접속 사용자

SSH Key

방화벽 또는 보안 그룹 설정

---
# 주의사항

외부 사용자는 80 또는 443으로 접근한다.

Spring Boot의 8080 포트는 가능하면 외부에 직접 공개하지 않는다.

Nginx가 80 포트에서 요청을 받고 내부 8080으로 전달한다.


VM 생성

SSH 접속 확인

80, 22, 8080 포트 정책 확인

Docker 설치

Nginx 설치

Nginx 설정 적용

Docker Hub 로그인

GitHub Secrets 등록

GitHub Actions 수동 배포 테스트

DuckDNS 연결

HTTPS 인증서 적용

추후 RDS MySQL 연결

---

# 주의

배포 설정 파일에는 실제 비밀번호나 키를 직접 작성하지 않음

민감 정보는 GitHub Secrets 또는 서버 환경변수로 관리
