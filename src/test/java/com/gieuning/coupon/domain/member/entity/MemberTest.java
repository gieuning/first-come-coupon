package com.gieuning.coupon.domain.member.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MemberTest {

    @Test
    @DisplayName("create()로 만든 회원의 role은 USER다")
    void createdMemberHasUserRole() {
        // given & when
        Member member = Member.create("test@example.com", "encoded-pw", "기은");

        // then
        assertThat(member.getRole()).isEqualTo(MemberRole.USER);
    }

}
