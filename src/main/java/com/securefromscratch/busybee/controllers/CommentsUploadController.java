package com.securefromscratch.busybee.controllers;

import com.securefromscratch.busybee.safety.CommentText;
import com.securefromscratch.busybee.storage.FileStorage;
import com.securefromscratch.busybee.storage.Task;
import com.securefromscratch.busybee.storage.TaskNotFoundException;
import com.securefromscratch.busybee.storage.TasksStorage;

import jakarta.validation.constraints.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.nio.file.Path;
import java.util.Optional;
import java.util.UUID;

@RestController
public class CommentsUploadController {
    @Autowired
    private TasksStorage m_tasks;

    // TODO: If you don't have a CommentText type - use whatever type you have
    public record AddCommentFields(@NotNull UUID taskid, Optional<UUID> commentid, @NotNull CommentText text) { }
    public record CreatedCommentId(UUID commentid) {}
    @PreAuthorize("@tasksAuthorization.userAllowedToComment(#commentFields.taskid(), authentication.name)")
    @PostMapping(value = "/comment", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<CreatedCommentId> addComment(
            @RequestPart("commentFields") AddCommentFields commentFields,
            @RequestPart(value = "file", required = false) Optional<MultipartFile> optFile,
            @AuthenticationPrincipal UserDetails user
    ) throws IOException {
        Optional<Task> t = m_tasks.find(commentFields.taskid());
        if (t.isEmpty()) {
            throw new TaskNotFoundException(commentFields.taskid());
        }

        String username = user.getUsername();

        if (optFile.isEmpty() || optFile.get().isEmpty()) {
            UUID newComment = m_tasks.addComment(t.get(), commentFields.text().get(), username, commentFields.commentid());
            return ResponseEntity.ok(new CreatedCommentId(newComment));
        }

		String storedFilename = filePartProcessing(optFile.get(), username);
        FileStorage.FileType filetype = FileStorage.identifyType(optFile.get());
        Optional<String> imageFilename = (filetype == FileStorage.FileType.IMAGE) ? Optional.of(storedFilename) : Optional.empty();
        Optional<String> attachFilename = (filetype != FileStorage.FileType.IMAGE) ? Optional.of(storedFilename) : Optional.empty();

        UUID newComment = m_tasks.addComment(
                t.get(),
                commentFields.text().get(),
                imageFilename,
                attachFilename,
                username,
                commentFields.commentid()
        );
		return ResponseEntity.ok(new CreatedCommentId(newComment));
    }

	private String filePartProcessing(MultipartFile fileData, String username) throws IOException {
        FileStorage storage = new FileStorage(Path.of("uploads").toAbsolutePath().normalize());
        return storage.storeUpload(fileData, username);
	}
}
