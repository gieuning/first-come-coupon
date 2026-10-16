package com.gieuning.coupon.domain.member.controller;

import com.gieuning.coupon.domain.member.dto.request.MemberSignupRequest;
import com.gieuning.coupon.domain.member.dto.response.MemberResponse;
import com.gieuning.coupon.domain.member.service.MemberService;
import com.gieuning.coupon.global.response.ApiResponse;
import com.gieuning.coupon.global.security.JwtPayload;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/members")
public class MemberController {

    private final MemberService memberService;

    @PostMapping
    public ResponseEntity<ApiResponse<MemberResponse>> signup(@Valid @RequestBody MemberSignupRequest request) {
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success(memberService.signup(request)));
    }

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<MemberResponse>> me(@AuthenticationPrincipal JwtPayload payload) {
        return ResponseEntity.ok(ApiResponse.success(memberService.getMember(payload.memberId())));
    }


}
