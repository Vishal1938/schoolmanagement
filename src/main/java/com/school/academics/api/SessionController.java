package com.school.academics.api;

import java.util.List;

import com.school.academics.app.AcademicSessionService;
import com.school.common.security.HasPermission;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Academic sessions. Reading them is open to any logged-in user; changing them is an admin job. */
@RestController
@RequestMapping("/sessions")
@Tag(name = "Academics", description = "Sessions, classes, subjects and teaching assignments")
public class SessionController {

	private final AcademicSessionService sessions;

	public SessionController(AcademicSessionService sessions) {
		this.sessions = sessions;
	}

	@GetMapping
	@PreAuthorize(HasPermission.AUTHENTICATED)
	@Operation(summary = "List the academic sessions, newest first")
	public List<SessionResponse> list() {
		return sessions.list().stream().map(SessionResponse::from).toList();
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	@PreAuthorize(HasPermission.ACADEMICS_MANAGE)
	@Operation(summary = "Add an academic session",
			description = "Created inactive unless it is the first session of the deployment. Use the activate "
					+ "endpoint to make it current.")
	public SessionResponse create(@Valid @RequestBody SessionRequest request) {
		return SessionResponse.from(sessions.create(request));
	}

	@PutMapping("/{id}/activate")
	@PreAuthorize(HasPermission.ACADEMICS_MANAGE)
	@Operation(summary = "Make this the current session",
			description = "Exactly one session is active: every other one is stood down by the same call.")
	public SessionResponse activate(@PathVariable String id) {
		return SessionResponse.from(sessions.activate(id));
	}
}
