package com.gieuning.coupon.domain.member.controller;

import com.gieuning.coupon.domain.member.dto.request.MemberSignupRequest;
import com.gieuning.coupon.domain.member.dto.response.MemberResponse;
import com.gieuning.coupon.domain.member.entity.Member;
import com.gieuning.coupon.domain.member.exception.MemberErrorCode;
import com.gieuning.coupon.domain.member.service.MemberService;
import com.gieuning.coupon.global.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(MemberController.class)
class MemberControllerTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @MockitoBean
    MemberService memberService;

    @Test
    @DisplayName("유효한 요청이면 201과 data 봉투에 담긴 회원 정보를 반환한다")
    void signup_validRequest_returns201WithMemberResponse() throws Exception {
        // given
        MemberSignupRequest request = signupRequest("test@example.com");

        // 서비스는 목이므로 응답 DTO를 직접 만들어 쥐여준다.
        // MemberResponse는 생성자가 private이라 정식 경로인 from(Member)로 만든다.
        Member member = Member.create("test@example.com", "encoded-password", "기은");
        ReflectionTestUtils.setField(member, "id", 1L);
        given(memberService.signup(any(MemberSignupRequest.class)))
                .willReturn(MemberResponse.from(member));

        // when & then
        mockMvc.perform(post("/api/members")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.id").value(1L))
                .andExpect(jsonPath("$.data.email").value("test@example.com"))
                .andExpect(jsonPath("$.data.nickname").value("기은"))
                // 응답 계약: 비밀번호는 어떤 형태로도 응답에 실리지 않는다
                .andExpect(jsonPath("$.data.password").doesNotExist());
    }

    @Test
    @DisplayName("이메일 형식이 틀리면 400과 fieldErrors에 email 항목이 담긴다")
    void signup_invalidEmail_returns400WithFieldErrors() throws Exception {
        // given
        MemberSignupRequest request = signupRequest("not-an-email");

        // when & then — 검증은 컨트롤러 진입 전에 실패하므로 서비스 스텁이 필요 없다
        mockMvc.perform(post("/api/members")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("email"))
                .andExpect(jsonPath("$.fieldErrors[0].reason").value("이메일 형식이 올바르지 않습니다."))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    @DisplayName("중복 이메일이면 409와 DUPLICATE_EMAIL 코드를 반환한다")
    void signup_duplicateEmail_returns409() throws Exception {
        // given
        MemberSignupRequest request = signupRequest("dup@example.com");

        given(memberService.signup(any(MemberSignupRequest.class)))
                .willThrow(new BusinessException(MemberErrorCode.DUPLICATE_EMAIL));

        // when & then
        mockMvc.perform(post("/api/members")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_EMAIL"))
                .andExpect(jsonPath("$.message").value("이미 사용 중인 이메일입니다."))
                // 계약: fieldErrors는 비어 있어도 항상 존재한다 (@JsonInclude 미사용 결정 검증)
                .andExpect(jsonPath("$.fieldErrors").isArray())
                .andExpect(jsonPath("$.fieldErrors").isEmpty());
    }

    private MemberSignupRequest signupRequest(String email) {
        return MemberSignupRequest.builder()
                .email(email)
                .password("password1!")
                .nickname("기은")
                .build();
    }
}
