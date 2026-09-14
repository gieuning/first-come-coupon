package com.gieuning.coupon.domain.member.controller;

import com.gieuning.coupon.domain.member.dto.request.MemberSignupRequest;
import com.gieuning.coupon.domain.member.entity.Member;
import com.gieuning.coupon.domain.member.entity.MemberRole;
import com.gieuning.coupon.domain.member.exception.MemberErrorCode;
import com.gieuning.coupon.domain.member.repository.MemberRepository;
import com.gieuning.coupon.global.config.MySqlTestContainerConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.client.RestTestClient;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient
@Import(MySqlTestContainerConfig.class)
public class MemberApiTest {

    @Autowired
    RestTestClient restTestClient;

    @Autowired
    MemberRepository memberRepository;

    @BeforeEach
    void setUp() {
        memberRepository.deleteAll();
    }

    @Test
    @DisplayName("유효한 요청으로 가입하면 201로 응답하고 인코딩된 비밀번호로 저장된다")
    void signup_persistsToDatabase() {
        // given
        MemberSignupRequest request = validRequest();

        // when
        RestTestClient.ResponseSpec response = signup(request);

        // then
        response.expectStatus().isCreated()
                .expectBody()
                .jsonPath("$.data.email").isEqualTo(request.getEmail())
                .jsonPath("$.data.password").doesNotExist()
                .jsonPath("$.data.nickname").isEqualTo(request.getNickname());

        Member saved = memberRepository.findByEmail(request.getEmail()).orElseThrow();
        assertThat(saved.getPassword()).isNotEqualTo(request.getPassword());
        assertThat(saved.getRole()).isEqualTo(MemberRole.USER);
    }

    @Test
    @DisplayName("이미 가입된 이메일로 가입하면 409와 DUPLICATE_EMAIL로 응답하고 중복 저장되지 않는다")
    void signup_duplicateEmail_returns409() {
        // given: 이미 같은 이메일로 가입된 상태
        MemberSignupRequest request = validRequest();
        signup(request).expectStatus().isCreated();

        // when: 같은 이메일로 다시 가입 시도
        RestTestClient.ResponseSpec second = signup(request);

        // then: 예외가 HTTP 계약(409 + 에러 코드)으로 번역됐는가
        second.expectStatus().isEqualTo(HttpStatus.CONFLICT)
                .expectBody()
                .jsonPath("$.code").isEqualTo(MemberErrorCode.DUPLICATE_EMAIL.getCode());

        // then: DB — 중복 행이 안 생겼는가
        assertThat(memberRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("검증에 실패하는 요청은 400과 INVALID_REQUEST로 응답하고 저장되지 않는다")
    void signup_invalidRequest_returns400() {
        // given: 이메일 형식이 깨진 요청
        MemberSignupRequest request = MemberSignupRequest.builder()
                .email("not-an-email")
                .password("password1!")
                .nickname("기은")
                .build();

        // when
        RestTestClient.ResponseSpec response = signup(request);

        // then: 상세 케이스는 MemberControllerTest 담당 — 여기선 전 구간 스모크만
        response.expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.code").isEqualTo("INVALID_REQUEST")
                .jsonPath("$.fieldErrors[0].field").isEqualTo("email");

        // then: DB — 아무것도 저장되지 않았는가
        assertThat(memberRepository.count()).isEqualTo(0);
    }


    private RestTestClient.ResponseSpec signup(MemberSignupRequest request) {
        return restTestClient.post().uri("/api/members")
                             .contentType(MediaType.APPLICATION_JSON)
                             .body(request)
                             .exchange();
    }

    private MemberSignupRequest validRequest() {
        return MemberSignupRequest.builder()
                                  .email("test1234@naver.com")
                                  .password("password1!")
                                  .nickname("기은")
                                  .build();
    }
}
