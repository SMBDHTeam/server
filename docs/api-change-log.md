# API 변경 이력

API 계약이 변경될 때마다 최신 항목을 위에 추가한다.

## 작성 형식

```md
## YYYY-MM-DD

- API: `METHOD /api/v1/...`
- 구분: 추가 | 변경 | 삭제 | 수정
- 이전:
- 이후:
- 이유:
- 호환성 파괴: 예 | 아니오
- 관련 PR 또는 이슈:
```

## 2026-07-02

- API: `GET /api/v1/durunubi/routes`, `GET /api/v1/durunubi/courses`, `POST /api/v1/schedules`
- 구분: 추가
- 이전: 두루누비는 1차 스프린트 제외 항목
- 이후: 두루누비 길·코스 조회 API를 추가하고, 일정 생성 요청에 `preferredDurunubiCourseIds`를 선택값으로 추가
- 이유: 걷기길·자전거길 기반 여행 코스 추천을 1차 범위에 포함
- 호환성 파괴: 아니오
- 관련 PR 또는 이슈: 없음

## 2026-06-25

- API: 1차 스프린트 전체 API
- 구분: 추가
- 이전: 저장소 내 기준 API 명세 없음
- 이후: `docs/API_SPEC.md`와 `docs/API_FIELD_GUIDE.md`를 기준 계약으로 지정
- 이유: 백엔드 구현과 AI Agent 작업 기준 통일
- 호환성 파괴: 아니오
- 관련 PR 또는 이슈: 없음
