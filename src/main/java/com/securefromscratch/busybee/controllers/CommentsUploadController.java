package com.securefromscratch.busybee.controllers;

import com.securefromscratch.busybee.safety.CommentText;
import com.securefromscratch.busybee.storage.FileStorage;
import com.securefromscratch.busybee.storage.UrlImageDownloader;
import com.securefromscratch.busybee.storage.Task;
import com.securefromscratch.busybee.storage.TaskNotFoundException;
import com.securefromscratch.busybee.storage.TasksStorage;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.io.*;
import java.nio.file.Path;
import java.util.Optional;
import java.util.UUID;

@RestController
public class CommentsUploadController {
    private static final Logger LOGGER = LoggerFactory.getLogger(CommentsUploadController.class);

    @Autowired
    private TasksStorage m_tasks;

    @Autowired
    private UrlImageDownloader m_urlImageDownloader;

    // TODO: If you don't have a CommentText type - use whatever type you have
        public record AddCommentFields(
            @NotNull UUID taskid,
            Optional<UUID> commentid,
            @NotNull CommentText text,
            Optional<String> imageUrl
        ) { }
    public record CreatedCommentId(UUID commentid) {}
    @PreAuthorize("@tasksAuthorization.userAllowedToComment(#commentFields.taskid(), authentication.name)")
    @PostMapping(value = "/comment", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<CreatedCommentId> addComment(
            @RequestPart("commentFields") @Valid AddCommentFields commentFields,
            @RequestPart(value = "file", required = false) Optional<MultipartFile> optFile,
            @AuthenticationPrincipal UserDetails user
    ) throws IOException {
        Optional<Task> t = m_tasks.find(commentFields.taskid());
        if (t.isEmpty()) {
            throw new TaskNotFoundException(commentFields.taskid());
        }

        String username = user.getUsername();

        boolean hasFile = optFile.isPresent() && !optFile.get().isEmpty();
        boolean hasUrl = commentFields.imageUrl() != null
                && commentFields.imageUrl().isPresent()
                && !commentFields.imageUrl().get().isBlank();

        if (hasFile) {
            // Per course requirement: image URL replaces upload for comments.
            LOGGER.warn("Comment add rejected (file upload not allowed): taskId={} user={}", commentFields.taskid(), FileStorage.safeLogValue(username));
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "File upload is not supported; use imageUrl");
        }

        if (!hasUrl) {
            UUID newComment = m_tasks.addComment(t.get(), commentFields.text().get(), username, commentFields.commentid());
            LOGGER.info("Comment added: taskId={} commentId={} by user={}", commentFields.taskid(), newComment, username);
            return ResponseEntity.ok(new CreatedCommentId(newComment));
        }

        LOGGER.info("Comment add with imageUrl: taskId={} user={}", commentFields.taskid(), FileStorage.safeLogValue(username));
        String storedFilename = m_urlImageDownloader.downloadAndStore(commentFields.imageUrl().get(), username);
        String lowerStored = storedFilename.toLowerCase();
        Optional<String> imageFilename = lowerStored.endsWith(".pdf") ? Optional.empty() : Optional.of(storedFilename);
        Optional<String> attachFilename = lowerStored.endsWith(".pdf") ? Optional.of(storedFilename) : Optional.empty();

        UUID newComment = m_tasks.addComment(
                t.get(),
                commentFields.text().get(),
                imageFilename,
                attachFilename,
                username,
                commentFields.commentid()
        );
		LOGGER.info("Comment added: taskId={} commentId={} by user={}", commentFields.taskid(), newComment, username);
		return ResponseEntity.ok(new CreatedCommentId(newComment));
    }

	private String filePartProcessing(MultipartFile fileData, String username) throws IOException {
        FileStorage storage = new FileStorage(Path.of("uploads").toAbsolutePath().normalize());
        return storage.storeUpload(fileData, username);
	}
}
