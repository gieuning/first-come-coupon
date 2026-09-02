# 로컬 개발 환경 셋업

## 요구 사항

- Java 21
- Docker

## MySQL 실행

```bash
docker run -d \
  --name coupon-mysql \
  -p 3306:3306 \
  -e MYSQL_ROOT_PASSWORD=3453 \
  -e MYSQL_DATABASE=coupon \
  mysql:8
```

기동 확인:

```bash
docker ps                    # STATUS가 Up인지 확인
docker logs coupon-mysql     # "ready for connections"가 두 번째로 찍힌 뒤 접속 가능
```

컨테이너 중지/재시작 (데이터는 유지됨):

```bash
docker stop coupon-mysql
docker start coupon-mysql
```

## 애플리케이션 실행

```bash
./gradlew bootRun
```
