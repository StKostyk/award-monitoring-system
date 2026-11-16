package ua.edu.chnu.awards.award.controller;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import ua.edu.chnu.awards.award.dto.Person;
import ua.edu.chnu.awards.award.dto.PersonDetail;
import ua.edu.chnu.awards.award.dto.PersonForm;
import ua.edu.chnu.awards.award.service.People;
import ua.edu.chnu.awards.award.service.PersonInvitations;
import ua.edu.chnu.awards.award.service.PersonRemoval;
import ua.edu.chnu.awards.common.web.PageResponse;

import lombok.RequiredArgsConstructor;

/**
 * People without an account, managed by a faculty secretary or dean within the role's scope; the services refuse
 * every other caller.
 */
@RestController
@RequestMapping("/api/v1/people")
@RequiredArgsConstructor
public class PeopleController {

    private static final String SIGNED_IN = "isAuthenticated()";

    private final People people;
    private final PersonInvitations invitations;
    private final PersonRemoval removal;

    /**
     * Adds a person.
     *
     * @param form the person
     * @return the person
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(SIGNED_IN)
    public Person add(@RequestBody PersonForm form) {
        return people.add(form);
    }

    /**
     * Lists the people of the caller's scope.
     *
     * @param q            a fragment of the name or address
     * @param organization only this department
     * @param page         0-based page
     * @param size         page size
     * @return the page, addresses masked
     */
    @GetMapping
    @PreAuthorize(SIGNED_IN)
    public PageResponse<Person> list(@RequestParam(required = false) String q,
                                     @RequestParam(required = false) Long organization,
                                     @RequestParam(defaultValue = "0") int page,
                                     @RequestParam(defaultValue = "20") int size) {
        return PageResponse.of(people.list(q, organization, page, size));
    }

    /**
     * One person with their awards.
     *
     * @param id the person
     * @return the person
     */
    @GetMapping("/{id}")
    @PreAuthorize(SIGNED_IN)
    public PersonDetail get(@PathVariable long id) {
        return people.get(id);
    }

    /**
     * Changes a person.
     *
     * @param id   the person
     * @param form the person with the version last read
     * @return the person
     */
    @PutMapping("/{id}")
    @PreAuthorize(SIGNED_IN)
    public Person change(@PathVariable long id, @RequestBody PersonForm form) {
        return people.change(id, form);
    }

    /**
     * Removes a person whose awards are all drafts.
     *
     * @param id the person
     */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize(SIGNED_IN)
    public void remove(@PathVariable long id) {
        removal.remove(id);
    }

    /**
     * Sends the invitation again.
     *
     * @param id the person
     */
    @PostMapping("/{id}/invitation")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @PreAuthorize(SIGNED_IN)
    public void invite(@PathVariable long id) {
        invitations.resend(id);
    }
}
