package com.kapresoft.devops.shell;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = "spring.shell.interactive.enabled=false")
class ShellApplicationTests {

	@Test
	void contextLoads() {
	}

}
