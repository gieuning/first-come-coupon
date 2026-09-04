package com.gieuning.coupon.domain.member.entity;

import com.gieuning.coupon.global.common.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
public class Member extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 100)
    private String email;

    @Column(nullable = false)
    private String password;

    @Column(nullable = false, length = 50)
    private String nickname;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MemberRole role;

    private Member(String email, String encodedPassword, String nickname, MemberRole role) {
        this.email = email;
        this.password = encodedPassword;
        this.nickname = nickname;
        this.role = role;
    }

    public static Member create(String email, String encodedPassword, String nickname) {
        return new Member(email, encodedPassword, nickname, MemberRole.USER);
    }
}
