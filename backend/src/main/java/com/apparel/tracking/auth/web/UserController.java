package com.apparel.tracking.auth.web;

import java.security.Principal;
import java.util.List;

import com.apparel.tracking.auth.dto.AuthenticatedUserDto;
import com.apparel.tracking.auth.dto.UserRequest;
import com.apparel.tracking.auth.service.UserService;

import io.swagger.v3.oas.annotations.tags.Tag;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Administration of user accounts. The URL rules restrict writes to ADMIN. */
@RestController
@RequestMapping("/api/users")
@Tag(name = "Users")
public class UserController {

    private final UserService users;

    public UserController(UserService users) {
        this.users = users;
    }

    @GetMapping
    public List<AuthenticatedUserDto> list() {
        return users.list();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AuthenticatedUserDto create(@Valid @RequestBody UserRequest request) {
        return users.create(request);
    }

    @PutMapping("/{id}")
    public AuthenticatedUserDto update(@PathVariable Long id, @Valid @RequestBody UserRequest request) {
        return users.update(id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id, Principal principal) {
        users.delete(id, principal.getName());
        return ResponseEntity.noContent().build();
    }
}
