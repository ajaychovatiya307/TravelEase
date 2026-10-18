package com.travelease.admin.controller;

import com.travelease.admin.service.UserAdminService;
import com.travelease.admin.service.UserAdminService.UserContact;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Internal-only contact lookup for notification e-mail delivery (#17). Protected by the internal
 * service chain (/internal/**): end-user tokens cannot read other users' e-mail addresses.
 */
@RestController
@RequestMapping("/internal/users")
public class InternalUserContactController {

    private final UserAdminService userAdminService;

    public InternalUserContactController(UserAdminService userAdminService) {
        this.userAdminService = userAdminService;
    }

    /** E-mail and active flag only; nothing else about the account leaves admin-service. */
    @GetMapping("/{userId}/contact")
    public UserContact contact(@PathVariable Long userId) {
        return userAdminService.getContact(userId);
    }
}
