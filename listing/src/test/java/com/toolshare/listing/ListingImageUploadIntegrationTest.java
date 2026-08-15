package com.toolshare.listing;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.toolshare.identity.authorization.Role;
import com.toolshare.identity.domain.IdentityAccount;
import com.toolshare.identity.infrastructure.persistence.IdentityAccountRepository;
import com.toolshare.identity.infrastructure.security.JwtTokenService;
import com.toolshare.listing.api.dto.ListingImageUploadResponse;
import com.toolshare.listing.config.ListingStorageProperties;
import com.toolshare.listing.domain.Category;
import com.toolshare.listing.domain.ListingLocation;
import com.toolshare.listing.domain.ToolListing;
import com.toolshare.listing.infrastructure.persistence.CategoryRepository;
import com.toolshare.listing.infrastructure.persistence.ToolListingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "toolshare.storage.local.root=${java.io.tmpdir}/toolshare-listing-upload-tests"
})
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class ListingImageUploadIntegrationTest {

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

    @Autowired
    private ListingStorageProperties listingStorageProperties;

    @BeforeEach
    void cleanState() throws IOException {
        toolListingRepository.deleteAll();
        categoryRepository.deleteAll();
        identityAccountRepository.deleteAll();

        Path root = Path.of(listingStorageProperties.getStorage().getLocal().getRoot()).toAbsolutePath().normalize();
        if (Files.exists(root)) {
            try (var paths = Files.walk(root)) {
                paths.sorted((left, right) -> right.compareTo(left))
                        .filter(path -> !path.equals(root))
                        .forEach(path -> {
                            try {
                                Files.deleteIfExists(path);
                            } catch (IOException ex) {
                                throw new IllegalStateException(ex);
                            }
                        });
            }
        }
    }

    @Test
    @Transactional
    void owner_uploads_jpeg_successfully_without_creating_tool_image_records() throws Exception {
        IdentityAccount owner = activeAccount("owner-upload@example.com", Role.OWNER);
        Category category = categoryRepository.save(new Category("drills", "Drills"));
        ToolListing listing = toolListingRepository.save(new ToolListing(
                owner.getId(),
                category,
                new ListingLocation("12 Nguyen Trai", null, "Ward 2", "District 5", "Ho Chi Minh City", "VN"),
                "Cordless Drill",
                "Reliable tool for home renovation",
                200000,
                800000,
                "VND",
                true,
                List.of()
        ));

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "drill-front.jpg",
                "image/jpeg",
                "jpeg-bytes".getBytes(StandardCharsets.UTF_8)
        );

        String response = mockMvc.perform(multipart("/api/v1/listings/" + listing.getId() + "/images")
                        .file(file)
                        .header("Authorization", "Bearer " + tokenFor(owner))
                        .header("X-Trace-Id", "upload-trace-1")
                        .contentType(MediaType.MULTIPART_FORM_DATA))
                .andExpect(status().isCreated())
                .andExpect(header().string("X-Trace-Id", "upload-trace-1"))
                .andExpect(jsonPath("$.storageKey").exists())
                .andReturn()
                .getResponse()
                .getContentAsString();

        ListingImageUploadResponse uploadResponse = objectMapper.readValue(response, ListingImageUploadResponse.class);
        Path storedFile = Path.of(listingStorageProperties.getStorage().getLocal().getRoot()).toAbsolutePath().normalize().resolve(uploadResponse.storageKey()).normalize();

        assertThat(uploadResponse.storageKey()).startsWith("listings/" + listing.getId() + "/images/");
        assertThat(uploadResponse.storageKey()).endsWith(".jpg");
        assertThat(uploadResponse.storageKey()).doesNotContain("..");
        assertThat(Files.exists(storedFile)).isTrue();
        assertThat(Files.readString(storedFile)).isEqualTo("jpeg-bytes");
        assertThat(toolListingRepository.findById(listing.getId()).orElseThrow().getImages()).isEmpty();
    }

    @Test
    void owner_uploads_png_successfully() throws Exception {
        IdentityAccount owner = activeAccount("owner-upload-png@example.com", Role.OWNER);
        Category category = categoryRepository.save(new Category("saws", "Saws"));
        ToolListing listing = toolListingRepository.save(new ToolListing(
                owner.getId(),
                category,
                new ListingLocation("99 Le Loi", null, "Ward 1", "District 1", "Ho Chi Minh City", "VN"),
                "Circular Saw",
                "Portable saw",
                150000,
                500000,
                "VND",
                true,
                List.of()
        ));

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "saw-side.png",
                "image/png",
                new byte[]{1, 2, 3}
        );

        mockMvc.perform(multipart("/api/v1/listings/" + listing.getId() + "/images")
                        .file(file)
                        .header("Authorization", "Bearer " + tokenFor(owner)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.storageKey").exists());
    }

    @Test
    void unauthenticated_upload_is_unauthorized() throws Exception {
        IdentityAccount owner = activeAccount("seed-owner@example.com", Role.OWNER);
        ToolListing listing = seedListing(owner.getEmail(), "unauth-listing", owner.getId());

        mockMvc.perform(multipart("/api/v1/listings/" + listing.getId() + "/images")
                        .file(new MockMultipartFile("file", "drill.jpg", "image/jpeg", new byte[]{1})))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void renter_upload_is_forbidden() throws Exception {
        IdentityAccount owner = activeAccount("owner-forbidden@example.com", Role.OWNER);
        IdentityAccount renter = activeAccount("renter-forbidden@example.com", Role.RENTER);
        ToolListing listing = seedListing(owner.getEmail(), "forbidden-renter", owner.getId());

        mockMvc.perform(multipart("/api/v1/listings/" + listing.getId() + "/images")
                        .file(new MockMultipartFile("file", "drill.jpg", "image/jpeg", new byte[]{1}))
                        .header("Authorization", "Bearer " + tokenFor(renter)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void non_owner_upload_is_forbidden() throws Exception {
        IdentityAccount owner = activeAccount("owner-one@example.com", Role.OWNER);
        IdentityAccount otherOwner = activeAccount("owner-two@example.com", Role.OWNER);
        ToolListing listing = seedListing(owner.getEmail(), "forbidden-owner", owner.getId());

        mockMvc.perform(multipart("/api/v1/listings/" + listing.getId() + "/images")
                        .file(new MockMultipartFile("file", "drill.jpg", "image/jpeg", new byte[]{1}))
                        .header("Authorization", "Bearer " + tokenFor(otherOwner)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void nonexistent_listing_is_not_found() throws Exception {
        IdentityAccount owner = activeAccount("owner-missing@example.com", Role.OWNER);

        mockMvc.perform(multipart("/api/v1/listings/" + UUID.randomUUID() + "/images")
                        .file(new MockMultipartFile("file", "drill.jpg", "image/jpeg", new byte[]{1}))
                        .header("Authorization", "Bearer " + tokenFor(owner)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void missing_file_is_validation_error() throws Exception {
        IdentityAccount owner = activeAccount("owner-missing-file@example.com", Role.OWNER);
        ToolListing listing = seedListing(owner.getEmail(), "missing-file", owner.getId());

        mockMvc.perform(multipart("/api/v1/listings/" + listing.getId() + "/images")
                        .header("Authorization", "Bearer " + tokenFor(owner))
                        .header("X-Trace-Id", "missing-file-trace"))
                .andExpect(status().isBadRequest())
                .andExpect(header().string("X-Trace-Id", "missing-file-trace"))
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void empty_file_is_validation_error() throws Exception {
        IdentityAccount owner = activeAccount("owner-empty-file@example.com", Role.OWNER);
        ToolListing listing = seedListing(owner.getEmail(), "empty-file", owner.getId());

        mockMvc.perform(multipart("/api/v1/listings/" + listing.getId() + "/images")
                        .file(new MockMultipartFile("file", "empty.jpg", "image/jpeg", new byte[0]))
                        .header("Authorization", "Bearer " + tokenFor(owner))
                        .header("X-Trace-Id", "empty-file-trace"))
                .andExpect(status().isBadRequest())
                .andExpect(header().string("X-Trace-Id", "empty-file-trace"))
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void unsupported_mime_is_validation_error() throws Exception {
        IdentityAccount owner = activeAccount("owner-bad-mime@example.com", Role.OWNER);
        ToolListing listing = seedListing(owner.getEmail(), "bad-mime", owner.getId());

        mockMvc.perform(multipart("/api/v1/listings/" + listing.getId() + "/images")
                        .file(new MockMultipartFile("file", "drill.txt", "text/plain", new byte[]{1, 2}))
                        .header("Authorization", "Bearer " + tokenFor(owner)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void oversized_file_is_validation_error() throws Exception {
        IdentityAccount owner = activeAccount("owner-oversize@example.com", Role.OWNER);
        ToolListing listing = seedListing(owner.getEmail(), "oversize", owner.getId());

        byte[] oversized = new byte[(int) listingStorageProperties.getUpload().getMaxFileSize().toBytes() + 1];

        mockMvc.perform(multipart("/api/v1/listings/" + listing.getId() + "/images")
                        .file(new MockMultipartFile("file", "drill.jpg", "image/jpeg", oversized))
                        .header("Authorization", "Bearer " + tokenFor(owner)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
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

    private ToolListing seedListing(String ownerEmail, String categoryKey, UUID ownerAccountId) {
        Category category = categoryRepository.save(new Category(categoryKey, categoryKey.toUpperCase()));
        return toolListingRepository.save(new ToolListing(
                ownerAccountId,
                category,
                new ListingLocation("12 Nguyen Trai", null, "Ward 2", "District 5", "Ho Chi Minh City", "VN"),
                "Cordless Drill",
                "Reliable tool for home renovation",
                200000,
                800000,
                "VND",
                true,
                List.of()
        ));
    }

    private ToolListing seedListing(String ownerEmail, String categoryKey) {
        IdentityAccount owner = identityAccountRepository.findByEmail(ownerEmail).orElseThrow();
        return seedListing(ownerEmail, categoryKey, owner.getId());
    }
}