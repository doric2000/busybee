package com.securefromscratch.busybee.controllers;

import com.securefromscratch.busybee.storage.Task;
import com.securefromscratch.busybee.storage.TasksStorage;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.Transformer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PostFilter;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.AccessDeniedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.Principal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import com.securefromscratch.busybee.safety.TaskName;
import com.securefromscratch.busybee.safety.TaskDescription;
import com.securefromscratch.busybee.auth.TasksAuthorization;
import com.securefromscratch.busybee.safety.ImageName;
import com.securefromscratch.busybee.safety.ResponsibilityName;
import com.securefromscratch.busybee.boxedpath.BoxedPath;
import com.securefromscratch.busybee.boxedpath.PathSandbox;

@RestController
@CrossOrigin(origins = "null")
@PreAuthorize("denyAll()")
public class TasksController {
    private static final Logger LOGGER = LoggerFactory.getLogger(TasksController.class);
    private static final PathSandbox UPLOADS = PathSandbox.boxroot("uploads");
    public static class MarkDoneRequest {
        public UUID taskid;
    }

    public static class CreateRequest {
        public TaskName name;
        public TaskDescription desc;
        public LocalDate dueDate;
        public LocalTime dueTime;
        public ResponsibilityName[] responsibilityOf;
    }

    @Autowired
    private TasksStorage m_tasks;

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
    @PreAuthorize("@tasksAuthorization.isOwner(#request.taskid, authentication.name)")
    public ResponseEntity<Map<String, Boolean>> markTaskDone (@RequestBody MarkDoneRequest request) throws IOException{
        boolean alreadyDone = m_tasks.markDone(request.taskid);
        return ResponseEntity.ok(Map.of("success",!alreadyDone));
    }

    //AUTHRIZATION RULES:
    //ADMIN - can create tasks.
    //CREATOR - can create tasks.
    //no one else can create tasks.


    @PostMapping("/create")
    // SPEL
    @PreAuthorize("hasRole('ADMIN') or hasRole('CREATOR') or (hasRole('TRIAL') and @tasksAuthorization.trialUserCanCreate(authentication.name))")
    public ResponseEntity<?> create(@RequestBody CreateRequest request,
                                    @AuthenticationPrincipal UserDetails user) throws IOException 
    {
        // Debug: print all authorities for the current user
        LOGGER.info("Authorities for user '{}': {}", user.getUsername(), user.getAuthorities());

        try {
            String name = request.name != null ? request.name.value() : null;
            String desc = request.desc != null ? request.desc.value() : null;
            String[] responsibilityOf = request.responsibilityOf != null ? java.util.Arrays.stream(request.responsibilityOf).map(r -> r.value()).toArray(String[]::new) : null;
            String createdBy = user.getUsername();

            UUID newTaskId;
            if (request.dueDate == null && request.dueTime == null) {
                newTaskId = m_tasks.add(name, desc, createdBy, responsibilityOf);
            } else if (request.dueDate != null && request.dueTime == null) {
                newTaskId = m_tasks.add(name, desc, request.dueDate, createdBy, responsibilityOf);
            } else if (request.dueDate != null && request.dueTime != null) {
                newTaskId = m_tasks.add(name, desc, request.dueDate, request.dueTime, createdBy, responsibilityOf);
            } else {
                // dueTime without dueDate is not allowed
                return ResponseEntity.badRequest().body(Map.of("error", "Cannot set dueTime without dueDate"));
            }
            return ResponseEntity.ok(Map.of("taskid", newTaskId));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (IOException e) {
            LOGGER.error("Error creating task", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Could not create task"));
        }
    }

    @GetMapping("/image")
    @PreAuthorize("@tasksAuthorization.imgIsInOwnedOrAssignedTask(#img.value(), authentication.name)")
    public ResponseEntity<byte[]> getImage(@RequestParam("img") ImageName img) throws IOException{
        BoxedPath imagePath = UPLOADS.getRoot().resolve(img.get());
        byte[] imageBytes = Files.readAllBytes(imagePath);
        return ResponseEntity.ok().body(imageBytes);
    }

    @GetMapping("/attachment")
    @PreAuthorize("@tasksAuthorization.attachmentIsInOwnedOrAssignedTask(#file.value(), authentication.name)")
    public ResponseEntity<byte[]> getAttachment(@RequestParam("file") ImageName file) throws IOException {
        BoxedPath attachmentPath = UPLOADS.getRoot().resolve(file.get());
        byte[] attachmentBytes = Files.readAllBytes(attachmentPath);
        String filename = Path.of(file.get()).getFileName().toString().replace("\"", "");
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header("Content-Disposition", "attachment; filename=\"" + filename + "\"")
                .body(attachmentBytes);
    }
}
