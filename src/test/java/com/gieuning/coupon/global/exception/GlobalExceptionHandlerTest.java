package com.gieuning.coupon.global.exception;

import com.gieuning.coupon.domain.member.exception.MemberErrorCode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = GlobalExceptionHandlerTest.TestController.class)
@Import(GlobalExceptionHandlerTest.TestController.class)   // 내부 클래스는 컴포넌트 스캔에 안 잡혀서 직접 등록
class GlobalExceptionHandlerTest {
    @Autowired
    MockMvc mockMvc;

    @Test
    void 비즈니스_예외가_발생하면_에러코드의_상태와_코드로_응답한다() throws Exception {
        // given — 없음 (컨트롤러가 이미 예외를 던지게 만들어놨으니)

        // when & then
        mockMvc.perform(get("/test/business"))
               .andExpect(status().isNotFound())
               .andExpect(jsonPath("$.code").value("MEMBER_NOT_FOUND"));
    }

    @Test
    void 검증에_실패하면_400과_필드별_오류를_응답한다() throws Exception {
        // given — @NotBlank가 걸린 name이 빈 문자열인 요청 바디
        String invalidBody = "{\"name\": \"\"}";

        // when & then
        mockMvc.perform(post("/test/valid")
                       .contentType(MediaType.APPLICATION_JSON)
                       .content(invalidBody))
               .andExpect(status().isBadRequest())
               .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
               .andExpect(jsonPath("$.fieldErrors[0].field").value("name"));
    }

    @Test
    void 미처리_예외가_발생하면_500과_고정_메시지로_응답한다() throws Exception {
        // given — 없음 (컨트롤러가 RuntimeException("internal detail")을 던짐)

        // when & then
        mockMvc.perform(get("/test/unexpected"))
               .andExpect(status().isInternalServerError())
               .andExpect(jsonPath("$.code").value("INTERNAL_SERVER_ERROR"))
               .andExpect(jsonPath("$.message").value("서버 오류가 발생했습니다."))
               // 내부 예외 메시지가 응답으로 누출되지 않아야 한다
               .andExpect(content().string(not(containsString("internal detail"))));
    }


    @Test
    void 프레임워크_예외도_통일된_에러_응답_형태로_변환한다() throws Exception {
        // given — GET만 열려 있는 /test/business에 POST → 405 (HttpRequestMethodNotSupportedException,
        //         부모 ResponseEntityExceptionHandler가 처리 → handleExceptionInternal의 바디 교체 분기)

        // when & then
        mockMvc.perform(post("/test/business"))
               .andExpect(status().isMethodNotAllowed())
               .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
               .andExpect(jsonPath("$.message").value("잘못된 요청입니다."));
    }

    @RestController
    static class TestController {

        @GetMapping("/test/business")
        public void business() {
            throw new BusinessException(MemberErrorCode.MEMBER_NOT_FOUND);
        }

        @PostMapping("/test/valid")
        public void valid(@Valid @RequestBody TestRequest request) {
        }

        @GetMapping("/test/unexpected")
        public void unexpected() {
            throw new RuntimeException("internal detail");
        }
    }

    @Getter
    @NoArgsConstructor
    static class TestRequest {

        @NotBlank
        private String name;
    }
}
