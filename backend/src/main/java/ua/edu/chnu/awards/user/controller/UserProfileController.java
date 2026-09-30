package ua.edu.chnu.awards.user.controller;

import jakarta.validation.Valid;

import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import ua.edu.chnu.awards.auth.dto.EmailChangeRequest;
import ua.edu.chnu.awards.auth.service.EmailChangeService;
import ua.edu.chnu.awards.gdpr.dto.DataExport;
import ua.edu.chnu.awards.gdpr.dto.PersonalDataFile;
import ua.edu.chnu.awards.gdpr.service.DataExportService;
import ua.edu.chnu.awards.user.dto.UserProfileResponse;
import ua.edu.chnu.awards.user.dto.UserUpdateRequest;
import ua.edu.chnu.awards.user.service.UserProfileService;

import lombok.RequiredArgsConstructor;

/**
 * The caller's own profile: reading it, correcting the names, moving to another sign-in address and exporting
 * the data held about them.
 */
@RestController
@RequestMapping("/api/v1/users/me")
@RequiredArgsConstructor
public class UserProfileController {

    private final UserProfileService profileService;
    private final EmailChangeService emailChangeService;
    private final DataExportService dataExportService;

    /**
     * Profile of the caller.
     *
     * @param jwt the access token
     * @return profile
     */
    @GetMapping
    public UserProfileResponse me(@AuthenticationPrincipal Jwt jwt) {
        return profileService.profileOf(Long.parseLong(jwt.getSubject()));
    }

    /**
     * Corrects the caller's first and last name; any other property of the body is refused.
     *
     * @param jwt     the access token
     * @param request the names to set
     * @return the profile after the change
     */
    @PatchMapping
    public UserProfileResponse update(@AuthenticationPrincipal Jwt jwt, @RequestBody UserUpdateRequest request) {
        return profileService.update(Long.parseLong(jwt.getSubject()), request);
    }

    /**
     * Starts a change of the caller's sign-in address; the link goes to the new address.
     *
     * @param jwt     the access token
     * @param request the new address and the current password
     */
    @PostMapping("/email-change")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void requestEmailChange(@AuthenticationPrincipal Jwt jwt,
                                   @Valid @RequestBody EmailChangeRequest request) {
        emailChangeService.request(Long.parseLong(jwt.getSubject()), request.newEmail(), request.currentPassword());
    }

    /**
     * Downloads everything the system holds about the caller as a JSON attachment (GDPR Article 20).
     *
     * @param jwt the access token
     * @return the export file
     */
    @GetMapping("/export")
    public ResponseEntity<PersonalDataFile> export(@AuthenticationPrincipal Jwt jwt) {
        DataExport export = dataExportService.export(Long.parseLong(jwt.getSubject()));
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION,
                ContentDisposition.attachment().filename(export.fileName()).build().toString())
            .cacheControl(CacheControl.noStore())
            .contentType(MediaType.APPLICATION_JSON)
            .body(export.content());
    }
}
