package com.travelease.admin.service;

import java.util.Optional;

import com.travelease.admin.dto.LoginRequest;
import com.travelease.admin.dto.LoginResponse;

/** Authenticates persisted user accounts and issues compatible TravelEase JWTs. */
public interface AuthenticationService {

    Optional<LoginResponse> login(LoginRequest request);
}
