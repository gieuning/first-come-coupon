package com.gieuning.coupon.global.config;

import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

// 설정을 한 곳에 고정해 테스트 클래스들이 스프링 컨텍스트(와 MySQL 컨테이너)를 공유하게 한다
// Redis 컨테이너는 아직 쓰는 통합 테스트가 없어 미포함 — 로그인이 Redis를 쓰게 되면 @Import에 추가
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient
@ActiveProfiles("test")
@Import(MySqlTestContainerConfig.class)
public @interface IntegrationTest {
}
