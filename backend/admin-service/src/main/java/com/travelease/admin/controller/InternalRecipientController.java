package com.travelease.admin.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.travelease.admin.service.EmployeeManagerRoutingService;

/** Internal recipient lookup used by downstream services for notification routing. */
@RestController
@RequestMapping("/internal/recipients")
public class InternalRecipientController {

    private final EmployeeManagerRoutingService routingService;

    public InternalRecipientController(EmployeeManagerRoutingService routingService) {
        this.routingService = routingService;
    }

    @GetMapping("/finance")
    public List<Long> findFinanceRecipients() {
        return routingService.findActiveFinanceUserIds();
    }
}
