package com.travelease.expense.integration;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import com.travelease.expense.client.PolicyConfigClient;
import com.travelease.expense.client.TravelRequestClient;
import com.travelease.expense.entity.ExpenseClaim;
import com.travelease.expense.entity.ExpenseClaimStatus;
import com.travelease.expense.repository.ExpenseClaimRepository;
import com.travelease.expense.repository.ExpenseDocumentRepository;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * #7: the real expense-service servlet container's multipart limits, as configured in application.yml
 * (spring.servlet.multipart.max-file-size=5MB), over real HTTP with a real signed JWT.
 * MockMvc bypasses multipart limits, so this test needs an embedded server.
 */
@SpringBootTest(classes = ExpenseIntegrationTestConfig.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {
        "server.servlet.context-path=",
        "spring.datasource.url=jdbc:h2:mem:expense-upload-limit-it;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect"
})
@DisplayName("Expense receipt upload size limit over real HTTP (#7)")
class ExpenseUploadLimitIT {

    private static final long OWNER = 1L;
    private static final int FIVE_MB = 5 * 1024 * 1024;

    @TempDir
    static Path storageRoot;

    @DynamicPropertySource
    static void storage(DynamicPropertyRegistry registry) {
        registry.add("travelease.storage.base-dir", () -> storageRoot.toString());
    }

    @Autowired
    private TestRestTemplate rest;

    @Autowired
    private ExpenseClaimRepository repository;

    @Autowired
    private ExpenseDocumentRepository documentRepository;

    @Value("${security.jwt.secret}")
    private String jwtSecret;

    @MockBean
    private TravelRequestClient travelRequestClient;

    @MockBean
    private PolicyConfigClient policyConfigClient;

    private long requestId;

    @BeforeEach
    void draftClaim() {
        ExpenseClaim claim = new ExpenseClaim();
        claim.setEmployeeId(OWNER);
        claim.setTravelRequestId(100L);
        claim.setAmount(new java.math.BigDecimal("4000.00"));
        claim.setCurrency("INR");
        claim.setCategory("LODGING");
        claim.setExpenseDate(LocalDate.of(2027, 1, 12));
        claim.setStatus(ExpenseClaimStatus.DRAFT);
        claim.setDuplicateFlag(false);
        claim.setDeleted(false);
        claim.setCreatedAt(Instant.now());
        claim.setCreatedBy("it");
        claim.setUpdatedAt(Instant.now());
        claim.setUpdatedBy("it");
        requestId = repository.saveAndFlush(claim).getId();
    }

    @AfterEach
    void cleanUp() {
        documentRepository.deleteAll();
        repository.deleteAll();
    }

    private String token() throws Exception {
        SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), new JWTClaimsSet.Builder()
                .subject(String.valueOf(OWNER))
                .claim("roles", List.of("EMPLOYEE"))
                .expirationTime(new Date(System.currentTimeMillis() + 60_000L))
                .build());
        jwt.sign(new MACSigner(jwtSecret.getBytes(StandardCharsets.UTF_8)));
        return jwt.serialize();
    }

    private ResponseEntity<String> upload(int sizeBytes) throws Exception {
        byte[] content = new byte[sizeBytes];
        Arrays.fill(content, (byte) 7);
        HttpHeaders fileHeaders = new HttpHeaders();
        fileHeaders.setContentType(MediaType.APPLICATION_PDF);
        MultiValueMap<String, Object> form = new LinkedMultiValueMap<>();
        form.add("file", new HttpEntity<>(new ByteArrayResource(content) {
            @Override
            public String getFilename() {
                return "scan.pdf";
            }
        }, fileHeaders));
        form.add("fileName", "scan.pdf");

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token());
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        return rest.exchange("/api/expenses/" + requestId + "/documents", HttpMethod.POST,
                new HttpEntity<>(form, headers), String.class);
    }

    @Test
    @DisplayName("a normal small upload succeeds")
    void smallUpload() throws Exception {
        assertThat(upload(10 * 1024).getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    @DisplayName("a file of exactly 5 MB (the documented limit) is accepted")
    void exactlyFiveMegabytes() throws Exception {
        assertThat(upload(FIVE_MB).getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    @DisplayName("one byte over 5 MB returns 400 VALIDATION_ERROR on 'file', not a 500")
    void overLimitIsValidationError() throws Exception {
        ResponseEntity<String> response = upload(FIVE_MB + 1);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).contains("VALIDATION_ERROR").contains("\"field\":\"file\"")
                .contains("5 MB");
        assertThat(documentRepository.findByExpenseClaimIdAndDeletedFalse(requestId)).isEmpty();
    }
}
