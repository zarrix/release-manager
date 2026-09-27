package com.zarrix.releasemanager.api

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType.APPLICATION_JSON
import org.springframework.http.MediaType.APPLICATION_PROBLEM_JSON
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.test.json.JsonCompareMode.STRICT
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@SpringBootTest
@AutoConfigureMockMvc
class ReleaseManagerAcceptanceTest(
    @Autowired private val mockMvc: MockMvc,
    @Autowired private val jdbc: JdbcClient,
) {

    @BeforeEach
    fun startFromEmptyState() {
        jdbc.sql("DELETE FROM deployment").update()
        jdbc.sql("DELETE FROM release_state").update()
    }

    @Test
    fun `replays the reference scenario`() {
        deploy("""{"name":"Service A","version":1}""").andExpect(jsonPath("$.systemVersion").value(1))
        deploy("""{"name":"Service B","version":1}""").andExpect(jsonPath("$.systemVersion").value(2))
        deploy("""{"name":"Service A","version":2}""").andExpect(jsonPath("$.systemVersion").value(3))
        deploy("""{"name":"Service B","version":1}""").andExpect(jsonPath("$.systemVersion").value(3))

        mockMvc.perform(get("/services").param("systemVersion", "2"))
            .andExpect(status().isOk)
            .andExpect(content().json("""[{"name":"Service A","version":1},{"name":"Service B","version":1}]""", STRICT))
        mockMvc.perform(get("/services").param("systemVersion", "3"))
            .andExpect(status().isOk)
            .andExpect(content().json("""[{"name":"Service A","version":2},{"name":"Service B","version":1}]""", STRICT))
    }

    @Test
    fun `deploy response names the environment it was applied to`() {
        deploy("""{"name":"Service A","version":1}""")
            .andExpect(content().json("""{"systemVersion":1,"environment":"default"}""", STRICT))
        deploy("""{"name":"Service A","version":1,"environment":"prod"}""")
            .andExpect(content().json("""{"systemVersion":1,"environment":"prod"}""", STRICT))
    }

    @Test
    fun `environments are isolated from default`() {
        deploy("""{"name":"Service A","version":1}""").andExpect(jsonPath("$.systemVersion").value(1))
        deploy("""{"name":"Service B","version":1}""").andExpect(jsonPath("$.systemVersion").value(2))
        deploy("""{"name":"Service A","version":9,"environment":"staging"}""")
            .andExpect(jsonPath("$.systemVersion").value(1))
        deploy("""{"name":"Service B","version":1}""").andExpect(jsonPath("$.systemVersion").value(2))

        mockMvc.perform(get("/services").param("systemVersion", "1").param("environment", "staging"))
            .andExpect(status().isOk)
            .andExpect(content().json("""[{"name":"Service A","version":9}]""", STRICT))
        mockMvc.perform(get("/services").param("systemVersion", "2"))
            .andExpect(status().isOk)
            .andExpect(content().json("""[{"name":"Service A","version":1},{"name":"Service B","version":1}]""", STRICT))
        mockMvc.perform(get("/services").param("systemVersion", "2").param("environment", "staging"))
            .andExpect(status().isNotFound)
    }

    @Test
    fun `unknown system version or environment is 404`() {
        deploy("""{"name":"Service A","version":1}""")

        mockMvc.perform(get("/services").param("systemVersion", "2"))
            .andExpect(status().isNotFound)
            .andExpect(content().contentTypeCompatibleWith(APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.status").value(404))
        mockMvc.perform(get("/services").param("systemVersion", "0"))
            .andExpect(status().isNotFound)
        mockMvc.perform(get("/services").param("systemVersion", "1").param("environment", "never-seen"))
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.detail").value("System version 1 does not exist in environment 'never-seen'"))
    }

    @Test
    fun `invalid input is 400 with a problem detail`() {
        postDeploy("""{"name":"","version":1}""")
            .andExpect(status().isBadRequest)
            .andExpect(content().contentTypeCompatibleWith(APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.detail").value("name: must not be blank"))
        postDeploy("""{"name":"Service A","version":0}""")
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.detail").value("version: must be greater than or equal to 1"))
        postDeploy("""{"name":"Service A"}""")
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.detail").value("version: must not be null"))
        postDeploy("""{"name":"Service A","version":1,"environment":"pr od"}""")
            .andExpect(status().isBadRequest)
        postDeploy("""{"name":"Service A","version":"one"}""")
            .andExpect(status().isBadRequest)
        postDeploy("""{not json""")
            .andExpect(status().isBadRequest)
            .andExpect(content().contentTypeCompatibleWith(APPLICATION_PROBLEM_JSON))

        mockMvc.perform(get("/services"))
            .andExpect(status().isBadRequest)
            .andExpect(content().contentTypeCompatibleWith(APPLICATION_PROBLEM_JSON))
        mockMvc.perform(get("/services").param("systemVersion", "two"))
            .andExpect(status().isBadRequest)
        mockMvc.perform(get("/services").param("systemVersion", "1").param("environment", "pr od"))
            .andExpect(status().isBadRequest)
    }

    private fun deploy(body: String) =
        postDeploy(body)
            .andExpect(status().isOk)
            .andExpect(content().contentTypeCompatibleWith(APPLICATION_JSON))

    private fun postDeploy(body: String) =
        mockMvc.perform(post("/deploy").contentType(APPLICATION_JSON).content(body))
}
