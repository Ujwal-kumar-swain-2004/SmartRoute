package com.smartroute;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
@WebMvcTest(Api.class)
class ApiTest {
 @Autowired MockMvc mvc;
 @MockitoBean Network network;
 @MockitoBean Planner planner;
 @Test void rejectsMissingInputs() throws Exception{mvc.perform(post("/api/routes").contentType("application/json").content("{}" )).andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").value("VALIDATION_ERROR"));}
 @Test void rejectsMalformedDate() throws Exception{mvc.perform(post("/api/routes").contentType("application/json").content("{\"departure\":\"bad\"}" )).andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").value("INVALID_JSON"));}
 @Test void providerIsDisabled() throws Exception{mvc.perform(get("/api/capabilities")).andExpect(status().isOk()).andExpect(jsonPath("$.realProviderEnabled").value(false));}
}
