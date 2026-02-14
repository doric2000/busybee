package com.securefromscratch.busybee.controllers;

import com.securefromscratch.busybee.boxedpath.BoxedPath;
import com.securefromscratch.busybee.boxedpath.PathSandbox;
import com.securefromscratch.busybee.safety.ImageName;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@RestController
@PreAuthorize("denyAll()")
public class MediaController {
    private static final PathSandbox UPLOADS = PathSandbox.boxroot("uploads");

    @GetMapping("/image")
    @PreAuthorize("@tasksAuthorization.imageIsInOwnedOrAssignedTask(#file.value(), authentication.name)")
    public ResponseEntity<byte[]> getImage(@RequestParam("file") ImageName file) throws IOException {
        BoxedPath path = UPLOADS.getRoot().resolve(file.value());
        verifyExists(path, "image");

        MediaType contentType = probeContentType(path);
        byte[] bytes = Files.readAllBytes(path);
        return ResponseEntity.ok()
                .contentType(contentType)
                .body(bytes);
    }

    @GetMapping("/attachment")
    @PreAuthorize("@tasksAuthorization.attachmentIsInOwnedOrAssignedTask(#file.value(), authentication.name)")
    public ResponseEntity<byte[]> getAttachment(@RequestParam("file") ImageName file) throws IOException {
        BoxedPath path = UPLOADS.getRoot().resolve(file.value());
        verifyExists(path, "attachment");

        MediaType contentType = probeContentType(path);
        byte[] bytes = Files.readAllBytes(path);

        String filename = Path.of(file.value()).getFileName().toString();
        return ResponseEntity.ok()
                .contentType(contentType)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .body(bytes);
    }

    private static void verifyExists(Path path, String kind) {
        if (!Files.exists(path) || !Files.isRegularFile(path)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, kind + ": not found");
        }
    }

    private static MediaType probeContentType(Path path) {
        try {
            String probed = Files.probeContentType(path);
            if (probed != null && !probed.isBlank()) {
                return MediaType.parseMediaType(probed);
            }
        } catch (Exception ignored) {
            // Fall back to extension-based mapping.
        }

        String name = path.getFileName().toString().toLowerCase();
        if (name.endsWith(".png")) return MediaType.IMAGE_PNG;
        if (name.endsWith(".gif")) return MediaType.IMAGE_GIF;
        if (name.endsWith(".jpg") || name.endsWith(".jpeg")) return MediaType.IMAGE_JPEG;
        if (name.endsWith(".webp")) return MediaType.parseMediaType("image/webp");
        if (name.endsWith(".pdf")) return MediaType.APPLICATION_PDF;

        return MediaType.APPLICATION_OCTET_STREAM;
    }
}
