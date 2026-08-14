package com.toolshare.listing;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.toolshare.identity.authorization.Role;
import com.toolshare.identity.domain.IdentityAccount;
import com.toolshare.identity.infrastructure.persistence.IdentityAccountRepository;
import com.toolshare.identity.infrastructure.security.JwtTokenService;
import com.toolshare.listing.domain.Category;
import com.toolshare.listing.domain.ToolListing;
import com.toolshare.listing.infrastructure.persistence.CategoryRepository;
import com.toolshare.listing.infrastructure.persistence.ToolListingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ListingApiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JwtTokenService jwtTokenService;

    @Autowired
    private IdentityAccountRepository identityAccountRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private ToolListingRepository toolListingRepository;

    @BeforeEach
    void cleanState() {
        toolListingRepository.deleteAll();
        categoryRepository.deleteAll();
        identityAccountRepository.deleteAll();
    }

    @Test
    void unauthenticated_requests_are_rejected() throws Exception {
        mockMvc.perform(get("/api/v1/categories"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/v1/listings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void admin_can_manage_categories() throws Exception {
        IdentityAccount admin = activeAccount("admin@example.com", Role.ADMIN);
        String token = tokenFor(admin);

        String createPayload = """
                {
                  \"key\": \"compressors\",
                  \"displayName\": \"Compressors\"
                }
                """;

        mockMvc.perform(post("/api/v1/categories")
                        .header("Authorization", "Bearer " + token)
                        .header("X-Trace-Id", "cat-create-trace")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createPayload))
                .andExpect(status().isCreated())
                .andExpect(header().string("X-Trace-Id", "cat-create-trace"))
                .andExpect(jsonPath("$.key").value("compressors"));

        Category category = categoryRepository.findByKey("compressors").orElseThrow();

        mockMvc.perform(get("/api/v1/categories")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].displayName").value("Compressors"));

        mockMvc.perform(patch("/api/v1/categories/{categoryId}", category.getId())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"key\":\"compressors\",\"displayName\":\"Air Compressors\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName").value("Air Compressors"));
    }

    @Test
    void non_admin_category_management_is_forbidden() throws Exception {
        IdentityAccount owner = activeAccount("owner@example.com", Role.OWNER);

        mockMvc.perform(post("/api/v1/categories")
                        .header("Authorization", "Bearer " + tokenFor(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"key\":\"compressors\",\"displayName\":\"Compressors\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void category_validation_uses_shared_error_contract() throws Exception {
        IdentityAccount admin = activeAccount("admin-validate@example.com", Role.ADMIN);

        mockMvc.perform(post("/api/v1/categories")
                        .header("Authorization", "Bearer " + tokenFor(admin))
                        .header("X-Trace-Id", "bad-category-trace")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"key\":\"INVALID KEY\",\"displayName\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(header().string("X-Trace-Id", "bad-category-trace"))
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.traceId").value("bad-category-trace"));
    }

    @Test
    void owner_can_create_read_update_and_transition_own_listing() throws Exception {
        IdentityAccount admin = activeAccount("admin-listing@example.com", Role.ADMIN);
        IdentityAccount owner = activeAccount("owner-listing@example.com", Role.OWNER);
        String adminToken = tokenFor(admin);
        String ownerToken = tokenFor(owner);

        mockMvc.perform(post("/api/v1/categories")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"key\":\"drills\",\"displayName\":\"Drills\"}"))
                .andExpect(status().isCreated());

        Category category = categoryRepository.findByKey("drills").orElseThrow();

        String createPayload = listingPayload(category.getId(), "Cordless Drill");

        String createResponse = mockMvc.perform(post("/api/v1/listings")
                        .header("Authorization", "Bearer " + ownerToken)
                        .header("X-Trace-Id", "listing-create-trace")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createPayload))
                .andExpect(status().isCreated())
                .andExpect(header().string("X-Trace-Id", "listing-create-trace"))
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.ownerAccountId").value(owner.getId().toString()))
                .andReturn().getResponse().getContentAsString();

        UUID listingId = objectMapper.readTree(createResponse).get("id").traverse(objectMapper).readValueAs(UUID.class);

        mockMvc.perform(get("/api/v1/listings/{listingId}", listingId)
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Cordless Drill"));

        mockMvc.perform(patch("/api/v1/listings/{listingId}", listingId)
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(listingPayload(category.getId(), "Cordless Drill Updated")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Cordless Drill Updated"));

        mockMvc.perform(post("/api/v1/listings/{listingId}/submit-for-review", listingId)
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING_REVIEW"));

        mockMvc.perform(post("/api/v1/listings/{listingId}/activate", listingId)
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));

        mockMvc.perform(post("/api/v1/listings/{listingId}/pause", listingId)
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PAUSED"));
    }

    @Test
    void owner_cannot_modify_another_owners_listing() throws Exception {
        IdentityAccount admin = activeAccount("admin-ownership@example.com", Role.ADMIN);
        IdentityAccount ownerOne = activeAccount("owner-one@example.com", Role.OWNER);
        IdentityAccount ownerTwo = activeAccount("owner-two@example.com", Role.OWNER);

        mockMvc.perform(post("/api/v1/categories")
                        .header("Authorization", "Bearer " + tokenFor(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"key\":\"saws\",\"displayName\":\"Saws\"}"))
                .andExpect(status().isCreated());

        Category category = categoryRepository.findByKey("saws").orElseThrow();

        String createResponse = mockMvc.perform(post("/api/v1/listings")
                        .header("Authorization", "Bearer " + tokenFor(ownerOne))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(listingPayload(category.getId(), "Circular Saw")))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        UUID listingId = objectMapper.readTree(createResponse).get("id").traverse(objectMapper).readValueAs(UUID.class);

        mockMvc.perform(patch("/api/v1/listings/{listingId}", listingId)
                        .header("Authorization", "Bearer " + tokenFor(ownerTwo))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(listingPayload(category.getId(), "Hijacked Saw")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void renter_cannot_create_listing() throws Exception {
        IdentityAccount admin = activeAccount("admin-renter@example.com", Role.ADMIN);
        IdentityAccount renter = activeAccount("renter@example.com", Role.RENTER);

        mockMvc.perform(post("/api/v1/categories")
                        .header("Authorization", "Bearer " + tokenFor(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"key\":\"mixers\",\"displayName\":\"Mixers\"}"))
                .andExpect(status().isCreated());

        Category category = categoryRepository.findByKey("mixers").orElseThrow();

        mockMvc.perform(post("/api/v1/listings")
                        .header("Authorization", "Bearer " + tokenFor(renter))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(listingPayload(category.getId(), "Paint Mixer")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void admin_can_suspend_active_listing() throws Exception {
        IdentityAccount admin = activeAccount("admin-suspend@example.com", Role.ADMIN);
        IdentityAccount owner = activeAccount("owner-suspend@example.com", Role.OWNER);

        mockMvc.perform(post("/api/v1/categories")
                        .header("Authorization", "Bearer " + tokenFor(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"key\":\"grinders\",\"displayName\":\"Grinders\"}"))
                .andExpect(status().isCreated());

        Category category = categoryRepository.findByKey("grinders").orElseThrow();

        String createResponse = mockMvc.perform(post("/api/v1/listings")
                        .header("Authorization", "Bearer " + tokenFor(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(listingPayload(category.getId(), "Angle Grinder")))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        UUID listingId = objectMapper.readTree(createResponse).get("id").traverse(objectMapper).readValueAs(UUID.class);

        mockMvc.perform(post("/api/v1/listings/{listingId}/submit-for-review", listingId)
                        .header("Authorization", "Bearer " + tokenFor(owner)))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/listings/{listingId}/activate", listingId)
                        .header("Authorization", "Bearer " + tokenFor(owner)))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/listings/{listingId}/suspend", listingId)
                        .header("Authorization", "Bearer " + tokenFor(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUSPENDED"));
    }

    @Test
    void invalid_transition_returns_conflict() throws Exception {
        IdentityAccount admin = activeAccount("admin-invalid-transition@example.com", Role.ADMIN);
        IdentityAccount owner = activeAccount("owner-invalid-transition@example.com", Role.OWNER);

        mockMvc.perform(post("/api/v1/categories")
                        .header("Authorization", "Bearer " + tokenFor(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"key\":\"cutters\",\"displayName\":\"Cutters\"}"))
                .andExpect(status().isCreated());

        Category category = categoryRepository.findByKey("cutters").orElseThrow();

        String createResponse = mockMvc.perform(post("/api/v1/listings")
                        .header("Authorization", "Bearer " + tokenFor(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(listingPayload(category.getId(), "Tile Cutter")))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        UUID listingId = objectMapper.readTree(createResponse).get("id").traverse(objectMapper).readValueAs(UUID.class);

        mockMvc.perform(post("/api/v1/listings/{listingId}/pause", listingId)
                        .header("Authorization", "Bearer " + tokenFor(owner))
                        .header("X-Trace-Id", "invalid-transition-trace"))
                .andExpect(status().isConflict())
                .andExpect(header().string("X-Trace-Id", "invalid-transition-trace"))
                .andExpect(jsonPath("$.code").value("CONFLICT"))
                .andExpect(jsonPath("$.traceId").value("invalid-transition-trace"));
    }

    @Test
    void listing_validation_and_missing_category_behave_correctly() throws Exception {
        IdentityAccount owner = activeAccount("owner-validation@example.com", Role.OWNER);

        mockMvc.perform(post("/api/v1/listings")
                        .header("Authorization", "Bearer " + tokenFor(owner))
                        .header("X-Trace-Id", "listing-validation-trace")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  \"title\": \"\",
                                  \"description\": \"\",
                                  \"dailyRateAmount\": -1,
                                  \"depositAmount\": -5,
                                  \"currency\": \"VN\",
                                  \"deliveryAvailable\": true,
                                  \"location\": {
                                    \"addressLine1\": \"\",
                                    \"ward\": \"\",
                                    \"district\": \"\",
                                    \"city\": \"\",
                                    \"countryCode\": \"VNM\"
                                  }
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(header().string("X-Trace-Id", "listing-validation-trace"))
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        mockMvc.perform(post("/api/v1/listings")
                        .header("Authorization", "Bearer " + tokenFor(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(listingPayload(UUID.randomUUID(), "Unknown Category Listing")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    private IdentityAccount activeAccount(String email, Role role) {
        IdentityAccount account = identityAccountRepository.save(IdentityAccount.register(email, "hashed-password"));
        account.addRole(role);
        account.markEmailVerified();
        account.activate();
        return identityAccountRepository.save(account);
    }

    private String tokenFor(IdentityAccount account) {
        return jwtTokenService.generateAccessToken(account.getId(), account.getEmail());
    }

    private String listingPayload(UUID categoryId, String title) {
        return """
                {
                  \"categoryId\": \"%s\",
                  \"location\": {
                    \"addressLine1\": \"12 Nguyen Trai\",
                    \"addressLine2\": \"Floor 3\",
                    \"ward\": \"Ward 2\",
                    \"district\": \"District 5\",
                    \"city\": \"Ho Chi Minh City\",
                    \"countryCode\": \"VN\"
                  },
                  \"title\": \"%s\",
                  \"description\": \"Reliable tool for home renovation\",
                  \"dailyRateAmount\": 200000,
                  \"depositAmount\": 800000,
                  \"currency\": \"VND\",
                  \"deliveryAvailable\": true,
                  \"images\": [
                    {
                      \"storageKey\": \"listing-images/tool-front.jpg\",
                      \"displayOrder\": 0
                    }
                  ]
                }
                """.formatted(categoryId, title);
    }
}