package com.takehome.forms;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// @WebMvcTest loads only the web layer (this controller), not the full app context —
// no datasource/Flyway required, unlike @SpringBootTest.
@WebMvcTest(FormsController.class)
class FormsControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@Test
	void ingestReturns200() throws Exception {
		mockMvc.perform(post("/ingest")).andExpect(status().isOk());
	}
}
