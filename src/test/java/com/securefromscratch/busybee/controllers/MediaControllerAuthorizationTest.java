package com.securefromscratch.busybee.controllers;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;

@SpringBootTest
@AutoConfigureMockMvc
class MediaControllerAuthorizationTest {
    private static final String EXISTING_IMAGE = "camera/wikipedia_Space-saving_closet.JPG";

    @Autowired
    private MockMvc mockMvc;

    @Test
    void ownerCanFetchImage() throws Exception {
        mockMvc.perform(get("/image").param("file", EXISTING_IMAGE).with(user("Dor")))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", org.hamcrest.Matchers.startsWith("image/")));
    }

    @Test
    void nonOwnerNonAssigneeGetsForbidden() throws Exception {
        mockMvc.perform(get("/image").param("file", EXISTING_IMAGE).with(user("Rony")))
                .andExpect(status().isForbidden());
    }

    @Test
    void unauthenticatedGets401NoRedirect() throws Exception {
        mockMvc.perform(get("/image").param("file", EXISTING_IMAGE))
                .andExpect(status().isUnauthorized());
    }
}
