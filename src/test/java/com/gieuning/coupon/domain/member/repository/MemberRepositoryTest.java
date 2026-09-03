package com.gieuning.coupon.domain.member.repository;

import com.gieuning.coupon.domain.member.entity.Member;
import com.gieuning.coupon.domain.member.entity.MemberRole;
import com.gieuning.coupon.global.config.JpaAuditingConfig;
import com.gieuning.coupon.global.config.MySqlTestContainerConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({JpaAuditingConfig.class, MySqlTestContainerConfig.class})
class MemberRepositoryTest {

    @Autowired
    MemberRepository memberRepository;

    @Test
    @DisplayName("회원을 저장하면 createdAt/updatedAt이 자동으로 채워진다")
    void auditingFieldsArePopulatedOnSave() {
        // given & when
        Member member = Member.create("test1234@naver.com", "encoded-pw", "기은");
        Member saved = memberRepository.save(member);

        // then
        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isNotNull();
    }

    @Test
    @DisplayName("create()로 만든 회원의 role은 USER다")
    void createdMemberHasUserRole() {
        // given & when
        Member member = Member.create("test@example.com", "encoded-pw", "기은");

        // then
        assertThat(member.getRole()).isEqualTo(MemberRole.USER);
    }

    @Test
    @DisplayName("중복 이메일로 저장하면 예외가 발생한다")
    void duplicateEmailThrowsException() {
        // given
        Member memberA = Member.create("test@example.com", "encoded-pw", "기은A");
        Member memberB = Member.create("test@example.com", "encoded-pw", "기은B");

        // when
        memberRepository.saveAndFlush(memberA);

        // then
        assertThatThrownBy(() -> memberRepository.saveAndFlush(memberB))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

}
