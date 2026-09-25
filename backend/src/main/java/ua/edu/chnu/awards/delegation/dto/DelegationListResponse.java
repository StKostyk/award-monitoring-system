package ua.edu.chnu.awards.delegation.dto;

import java.util.List;

/**
 * The two sides of the caller's delegation page.
 *
 * @param given    delegations the caller handed out, newest first
 * @param received delegations handed to the caller, newest first
 */
public record DelegationListResponse(List<DelegationResponse> given, List<DelegationResponse> received) {
}
