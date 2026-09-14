package com.gieuning.coupon.domain.member.service;

import com.gieuning.coupon.domain.member.dto.request.MemberSignupRequest;
import com.gieuning.coupon.domain.member.dto.response.MemberResponse;
import com.gieuning.coupon.domain.member.entity.Member;
import com.gieuning.coupon.domain.member.exception.MemberErrorCode;
import com.gieuning.coupon.domain.member.repository.MemberRepository;
import com.gieuning.coupon.global.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class MemberServiceTest {

    @Mock
    private MemberRepository memberRepository;
    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private MemberService memberService;


    @Test
    void 이메일이_중복이_아니면_회원을_저장하고_MemberResponse_반환한다() {
        // given
        MemberSignupRequest request = signupRequest();
        Member member = Member.create(request.getEmail(), "encoded-password", request.getNickname());

        // repository.save() 이후 JPA가 식별자를 채운 상태를 모사한다.
        ReflectionTestUtils.setField(member, "id", 1L);

        given(passwordEncoder.encode(request.getPassword())).willReturn("encoded-password");
        given(memberRepository.existsByEmail(request.getEmail())).willReturn(false);
        given(memberRepository.save(any(Member.class))).willReturn(member);

        // when
        MemberResponse response = memberService.signup(request);

        // then
        ArgumentCaptor<Member> memberCaptor = ArgumentCaptor.forClass(Member.class);
        // capture()는 any()처럼 매처 자리에 들어가되, 통과시키면서 인자를 챙겨둔다
        then(memberRepository).should().save(memberCaptor.capture());

        // 서비스가 save에 건넸던 바로 그 객체 — "주문서대로 요리했는지" 검사
        Member capturedMember = memberCaptor.getValue();
        assertThat(capturedMember.getEmail()).isEqualTo(request.getEmail());
        assertThat(capturedMember.getNickname()).isEqualTo(request.getNickname());
        assertThat(capturedMember.getPassword()).isEqualTo("encoded-password");
        assertThat(capturedMember.getPassword()).isNotEqualTo(request.getPassword()); // 평문 저장 금지
        assertThat(response.getId()).isEqualTo(1L);
        assertThat(response.getEmail()).isEqualTo(member.getEmail());
        assertThat(response.getNickname()).isEqualTo(member.getNickname());
    }

    @Test
    void 이메일이_중복이면_예외를_던진다() {
        // given
        MemberSignupRequest request = signupRequest();

        given(memberRepository.existsByEmail(request.getEmail())).willReturn(true);

        // when & then
        assertThatThrownBy(() -> memberService.signup(request))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(MemberErrorCode.DUPLICATE_EMAIL);

        then(memberRepository).should(never()).save(any(Member.class));
    }

    @Test
    void 저장중_무결성위반이_발생하면_DUPLICATE_EMAIL로_번역한다() {
        // given
        MemberSignupRequest request = signupRequest();

        DataIntegrityViolationException cause = new DataIntegrityViolationException("uk_member_email");

        given(memberRepository.existsByEmail(request.getEmail())).willReturn(false);
        given(passwordEncoder.encode(request.getPassword())).willReturn("encoded-password");
        given(memberRepository.save(any(Member.class))).willThrow(cause);

        // when
        BusinessException thrown = catchThrowableOfType(BusinessException.class,
                () -> memberService.signup(request));

        // then
        assertThat(thrown.getErrorCode()).isEqualTo(MemberErrorCode.DUPLICATE_EMAIL);
        assertThat(thrown.getCause()).isSameAs(cause);

    }

    private MemberSignupRequest signupRequest() {
        return MemberSignupRequest.builder()
                .email("test@example.com")
                .password("password1!")
                .nickname("테스터")
                .build();
    }

}
