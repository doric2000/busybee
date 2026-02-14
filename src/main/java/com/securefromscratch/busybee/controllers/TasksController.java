package com.securefromscratch.busybee.controllers;

import com.securefromscratch.busybee.storage.Task;
import com.securefromscratch.busybee.storage.TasksStorage;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.Transformer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PostFilter;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.security.Principal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import com.securefromscratch.busybee.safety.TaskName;
import com.securefromscratch.busybee.safety.TaskDescription;
import com.securefromscratch.busybee.auth.UsersStorage;
import com.securefromscratch.busybee.safety.Username;

@RestController
@CrossOrigin(origins = "null")
@PreAuthorize("denyAll()")
public class TasksController {
    private static final Logger LOGGER = LoggerFactory.getLogger(TasksController.class);
    private static final int MAX_RESPONSIBLE_USERS = 5;

    public record CreateResponse(UUID taskid) { }

    public static class MarkDoneRequest {
        @NotNull
        public UUID taskid;
    }

    public static class CreateRequest {
        public TaskName name;
        public TaskDescription desc;
        public LocalDate dueDate;
        public LocalTime dueTime;
        public Username[] responsibilityOf;
    }

    @Autowired
    private TasksStorage m_tasks;

    @Autowired
    private UsersStorage m_users;

    @GetMapping("/tasks")
    @PreAuthorize("permitAll()")
    @PostFilter("hasRole('ADMIN') or T(com.securefromscratch.busybee.auth.TasksAuthorization).userAllowedToViewTask(filterObject, authentication.name)")
    public Collection<TaskOut> getTasks(Principal principal) {
        List<Task> allTasks = m_tasks.getAll();
        Transformer<Task, TaskOut> transformer = t -> TaskOut.fromTask((Task)t);
        Collection<TaskOut> returnedVal = CollectionUtils.collect(allTasks, transformer);
        // TasksAuthorization.filterToAuthorizedTasks(returnedVal, principal.getName()); // Filtering now handled by @PostFilter
        return returnedVal;
    }

    @PostMapping("/done")
    @PreAuthorize("@tasksAuthorization.isOwnerOrResponsible(#request.taskid, authentication.name) or hasRole('ADMIN')")
    public ResponseEntity<Map<String, Boolean>> markTaskDone (@Valid @RequestBody MarkDoneRequest request) throws IOException{
        boolean alreadyDone = m_tasks.markDone(request.taskid);
        return ResponseEntity.ok(Map.of("success",!alreadyDone));
    }

    //AUTHRIZATION RULES:
    //ADMIN - can create tasks.
    //CREATOR - can create tasks.
    //TRIAL – can create a task. but only if there is not an active task.

    @PostMapping("/create")
    @PreAuthorize("hasRole('ADMIN') or hasRole('CREATOR') or (hasRole('TRIAL') and @tasksAuthorization.trialUserCanCreate(authentication.name))")
    public ResponseEntity<CreateResponse> create(@RequestBody CreateRequest request,
                                                 @AuthenticationPrincipal UserDetails user) throws IOException
    {
        validateCreateRequest(request);

        String name = request.name.value();
        String desc = request.desc.value();
        String[] responsibilityOf = request.responsibilityOf != null
                ? java.util.Arrays.stream(request.responsibilityOf).map(Username::value).toArray(String[]::new)
                : null;
        String createdBy = user.getUsername();

        validateResponsibleUsersExist(request.responsibilityOf);

        validateUniqueTaskName(name);

        UUID newTaskId = addTask(request, name, desc, createdBy, responsibilityOf);

        // Avoid logging potentially sensitive identifiers (PII). taskId is sufficient for correlation.
        LOGGER.info("Task created: taskId={}", newTaskId);
        return ResponseEntity.ok(new CreateResponse(newTaskId));
    }

    private void validateResponsibleUsersExist(Username[] responsibilityOf) {
        if (responsibilityOf == null) {
            return;
        }
        for (int i = 0; i < responsibilityOf.length; i++) {
            Username u = responsibilityOf[i];
            if (u == null) {
                continue; // handled by validateCreateRequest
            }
            boolean exists = m_users.findByUsername(u.value()).isPresent();
            if (!exists) {
                // Don't log the username (PII). Index is enough for debugging.
                LOGGER.warn("Create task rejected: responsibilityOf[{}] user does not exist", i);
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "responsibilityOf[" + i + "]: user does not exist");
            }
        }
    }

    private UUID addTask(CreateRequest request, String name, String desc, String createdBy, String[] responsibilityOf) throws IOException {
        if (request.dueDate == null && request.dueTime == null) {
            return m_tasks.add(name, desc, createdBy, responsibilityOf);
        } else if (request.dueDate != null && request.dueTime == null) {
            return m_tasks.add(name, desc, request.dueDate, createdBy, responsibilityOf);
        } else if (request.dueDate != null && request.dueTime != null) {
            return m_tasks.add(name, desc, request.dueDate, request.dueTime, createdBy, responsibilityOf);
        } else {
            // validateCreateRequest(...) should have blocked this combination.
            throw new IllegalStateException("Invalid dueDate/dueTime combination");
        }
    }

    private static void validateCreateRequest(CreateRequest request) {
        if (request == null) {
            LOGGER.warn("Create task rejected: missing request body");
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "request: required");
        }

        if (request.name == null) {
            LOGGER.warn("Create task rejected: missing name");
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "name: required");
        }

        if (request.desc == null) {
            LOGGER.warn("Create task rejected: missing desc");
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "desc: required");
        }

        if (request.responsibilityOf == null) {
            LOGGER.warn("Create task rejected: missing responsibilityOf");
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "responsibilityOf: required");
        }

        if (request.dueTime != null && request.dueDate == null) {
            LOGGER.warn("Create task rejected: dueTime without dueDate");
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "dueTime: cannot be set without dueDate");
        }

        if (request.dueDate != null) {
            LocalDate today = LocalDate.now();
            if (request.dueDate.isBefore(today)) {
                LOGGER.warn("Create task rejected: dueDate is in the past");
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "dueDate: cannot be in the past");
            }

            if (request.dueTime != null && request.dueDate.isEqual(today)) {
                LocalTime now = LocalTime.now();
                if (request.dueTime.isBefore(now)) {
                    LOGGER.warn("Create task rejected: dueDate+dueTime is in the past");
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            "dueTime: cannot set dueDate+dueTime in the past");
                }
            }
        }

        // Prevent DoS via huge responsibility list
        if (request.responsibilityOf != null && request.responsibilityOf.length > MAX_RESPONSIBLE_USERS) {
            LOGGER.warn("Create task rejected: too many responsible users; count={}", request.responsibilityOf.length);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "responsibilityOf: too many values (max " + MAX_RESPONSIBLE_USERS + ")");
        }

        if (request.responsibilityOf != null) {
            for (int i = 0; i < request.responsibilityOf.length; i++) {
                if (request.responsibilityOf[i] == null) {
                    LOGGER.warn("Create task rejected: responsibilityOf[{}] is null", i);
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            "responsibilityOf[" + i + "]: required");
                }
            }
        }
    }

    private void validateUniqueTaskName(String name) {
        if (name == null) {
            return;
        }
        if (m_tasks.taskNameExists(name)) {
            LOGGER.warn("Create task rejected: duplicate task name");
            throw new ResponseStatusException(HttpStatus.CONFLICT, "name: task name already exists");
        }
    }

}
