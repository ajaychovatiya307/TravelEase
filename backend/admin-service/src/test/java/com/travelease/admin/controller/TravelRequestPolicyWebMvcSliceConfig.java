package com.travelease.admin.controller;

import org.mockito.Mockito;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.travelease.admin.service.TravelRequestPolicyService;

/**
 * Shared bean supplier for every {@code @WebMvcTest} slice in this module,
 * auto-imported via
 * {@code META-INF/spring/org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest.imports}
 * (test resources).
 *
 * <p>The shared {@link AdminTestApplication} bootstrap performs a full
 * component scan of this package, so <b>every</b> controller in
 * {@code com.travelease.admin.controller} is registered in <b>every</b>
 * slice context - not only the one named in the slice's
 * {@code @WebMvcTest(controllers = ...)} attribute. Each slice therefore has to
 * provide a bean for every controller's service collaborator (the pre-existing
 * slices do this with per-test {@code @MockBean}s for the sibling services).
 * When a new controller is added to the package, every pre-existing slice would
 * otherwise fail to load its context; supplying the new collaborator here
 * keeps all existing slices green without touching them. Slices that stub the
 * collaborator themselves (e.g. {@link TravelRequestPolicyControllerTest})
 * simply override this bean with their {@code @MockBean}.</p>
 */
@Configuration(proxyBeanMethods = false)
class TravelRequestPolicyWebMvcSliceConfig {

    /**
     * Mock {@link TravelRequestPolicyService} so the
     * {@link TravelRequestPolicyController} and {@link InternalPolicyController}
     * beans can be constructed in every slice; never invoked by the
     * pre-existing slices' test paths.
     */
    @Bean
    TravelRequestPolicyService travelRequestPolicyService() {
        return Mockito.mock(TravelRequestPolicyService.class);
    }
}