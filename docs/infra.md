# 백 환경 기준
- Java 17
- Temurin JDK 17
- Spring Boot 4.0.6
- Gradle
- 개발 DB: H2
- 운영 또는 추후 개발 DB: RDS MySQL 예정
---

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