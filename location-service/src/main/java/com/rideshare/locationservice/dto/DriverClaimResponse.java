package com.rideshare.locationservice.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class DriverClaimResponse {

    private boolean claimed;
}