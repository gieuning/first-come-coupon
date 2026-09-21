package com.gieuning.coupon;

import com.gieuning.coupon.global.config.MySqlTestContainerConfig;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
@Import(MySqlTestContainerConfig.class)
class FirstComeCouponApplicationTests {

	@Test
	void contextLoads() {
	}

}
