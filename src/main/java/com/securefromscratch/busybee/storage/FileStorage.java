package com.securefromscratch.busybee.storage;

import com.securefromscratch.busybee.boxedpath.BoxedPath;
import com.securefromscratch.busybee.boxedpath.PathSandbox;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public class FileStorage {
    // Restrict file upload: allow specific extensions & mimetypes, verify magic bytes,
    // sandbox uploads, unique filenames, quota per user, disk space checks, and clear errors.
    private static final Logger LOGGER = LoggerFactory.getLogger(FileStorage.class);
    private static final String CLIENT_REJECT_REASON = "upload: rejected";

    public enum FileType {
        IMAGE,
        PDF,
        OTHER
    }

    private static final long MAX_UPLOAD_BYTES = 5 * 1024 * 1024;
    private static final long MIN_FREE_BYTES = 10 * 1024 * 1024;
    private static final int MAX_FILES_PER_USER = 50;
    private static final int MAX_FILENAME_LENGTH = 80;
    private static final Pattern SAFE_FILENAME = Pattern.compile("^[a-zA-Z0-9._-]+$");
    private static final Pattern SAFE_USER_SEGMENT = Pattern.compile("^[a-zA-Z0-9_-]+$");
    private static final int MAGIC_READ_LIMIT = 16;
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(
            ".jpg",
            ".jpeg",
            ".png",
            ".gif",
            ".webp",
            ".pdf"
    );

    private static final int UUID_LENGTH = UUID.randomUUID().toString().length();

    private final PathSandbox m_sandbox;
    private final BoxedPath m_storageRoot;

    public FileStorage(Path storageDirectory) throws IOException {
        Path normalizedRoot = storageDirectory.toAbsolutePath().normalize();
        m_sandbox = PathSandbox.boxroot(normalizedRoot);
        m_storageRoot = m_sandbox.getRoot();
        Files.createDirectories(m_storageRoot);
    }

    /*public Path store(MultipartFile file) throws IOException {
        // write code to store a file and returns its path
    }*/

    public byte[] getBytes(String filename) throws IOException {
        Path filepath = m_storageRoot.resolve(filename);
        byte[] serialized = Files.readAllBytes(filepath);
        return serialized;
    }

    public static FileType identifyType(MultipartFile file) {
        String contentType = file.getContentType();
        if (contentType == null) {
            return FileType.OTHER;
        }
        contentType = contentType.toLowerCase();
        if (contentType.startsWith("image/")) {
            return FileType.IMAGE;
        }
        if (contentType.contains("pdf")) {
            return FileType.PDF;
        }
        return FileType.OTHER;
    }

    private static String extractExtension(String filename) {
        String[] parts = filename.split(".");
        return parts.length == 1 ? "" : ("." + parts[parts.length - 1]);
    }

    public String storeUpload(MultipartFile fileData, String username) throws IOException {
        if (fileData == null || fileData.isEmpty()) {
            throw reject(HttpStatus.BAD_REQUEST, "Missing file", username, null, 0);
        }
        long size = fileData.getSize();
        if (size <= 0) {
            throw reject(HttpStatus.BAD_REQUEST, "Empty file", username, null, size);
        }
        if (size > MAX_UPLOAD_BYTES) {
            throw reject(HttpStatus.PAYLOAD_TOO_LARGE, "File too large", username, null, size);
        }

        String originalFilename = fileData.getOriginalFilename();
        String baseName = (originalFilename == null) ? "" : Path.of(originalFilename).getFileName().toString();
        if (baseName.isBlank() || baseName.length() > MAX_FILENAME_LENGTH || !SAFE_FILENAME.matcher(baseName).matches()) {
            throw reject(HttpStatus.BAD_REQUEST, "Invalid filename", username, baseName, size);
        }

        String ext = getLowerExtension(baseName);
        if (ext.isBlank()) {
            throw reject(HttpStatus.BAD_REQUEST, "Missing file extension", username, baseName, size);
        }
        if (!ALLOWED_EXTENSIONS.contains(ext)) {
            throw reject(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Unsupported file extension", username, baseName, size);
        }

        String contentType = normalizeContentType(fileData.getContentType());
        if (contentType.isBlank()) {
            throw reject(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Missing content type", username, baseName, size);
        }

        String safeUserSegment = sanitizeUserSegment(username);
        BoxedPath userDir = m_storageRoot.resolve(safeUserSegment);
        Files.createDirectories(userDir);

        long fileCount = getFileCount(userDir);
        if (fileCount >= MAX_FILES_PER_USER) {
            throw reject(HttpStatus.TOO_MANY_REQUESTS, "Too many files for user", username, baseName, size);
        }

        long freeBytes = Files.getFileStore(userDir).getUsableSpace();
        if (freeBytes < size + MIN_FREE_BYTES) {
            throw reject(HttpStatus.BAD_REQUEST, "Insufficient disk space", username, baseName, size);
        }

        String storedName = UUID.randomUUID().toString() + ext;
        BoxedPath storedPath = userDir.resolve(storedName);

        long totalWritten = 0;
        try (InputStream in = new BufferedInputStream(fileData.getInputStream());
             OutputStream out = Files.newOutputStream(storedPath, StandardOpenOption.CREATE_NEW)) {
            byte[] header = in.readNBytes(MAGIC_READ_LIMIT);
            if (header.length == 0) {
                throw reject(HttpStatus.BAD_REQUEST, "Empty file", username, baseName, size);
            }
            MagicType magicType = detectMagicType(header, header.length);
            validateType(contentType, ext, magicType, username, baseName, size);

            out.write(header);
            totalWritten += header.length;

            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) != -1) {
                totalWritten += read;
                if (totalWritten > MAX_UPLOAD_BYTES) {
                    throw reject(HttpStatus.PAYLOAD_TOO_LARGE, "File too large", username, baseName, size);
                }
                out.write(buffer, 0, read);
            }
        } catch (ResponseStatusException ex) {
            Files.deleteIfExists(storedPath);
            throw ex;
        } catch (IOException ex) {
            Files.deleteIfExists(storedPath);
            LOGGER.warn("Upload failed: user={} filename={}", safeLogValue(username), safeLogValue(baseName), ex);
            throw ex;
        }

        LOGGER.info("Upload stored: user={} filename={} stored={}", safeLogValue(username), safeLogValue(baseName), storedName);
        return safeUserSegment + "/" + storedName;
    }

    public void cleanupStoredUpload(String storedRelativePath) {
        if (storedRelativePath == null || storedRelativePath.isBlank()) {
            return;
        }
        try {
            BoxedPath storedPath = m_storageRoot.resolve(storedRelativePath);
            Files.deleteIfExists(storedPath);
        } catch (Exception ex) {
            // Cleanup should never mask the original application error.
            LOGGER.warn("Upload cleanup failed: stored={}", safeLogValue(storedRelativePath), ex);
        }
    }

    private static String getLowerExtension(String filename) {
        int idx = filename.lastIndexOf('.');
        if (idx <= 0 || idx == filename.length() - 1) {
            return "";
        }
        return filename.substring(idx).toLowerCase(Locale.ROOT);
    }

    private static String normalizeContentType(String contentType) {
        return contentType == null ? "" : contentType.toLowerCase(Locale.ROOT).trim();
    }

    private static String sanitizeUserSegment(String username) {
        if (username == null) {
            return "user";
        }
        String trimmed = username.trim();
        if (SAFE_USER_SEGMENT.matcher(trimmed).matches()) {
            return trimmed;
        }
        String sanitized = trimmed.replaceAll("[^a-zA-Z0-9_-]", "_");
        return sanitized.isBlank() ? "user" : sanitized;
    }

    private enum MagicType { JPG, PNG, GIF, WEBP, PDF, UNKNOWN }

    private static MagicType detectMagicType(byte[] header, int len) {
        if (len >= 3 && (header[0] & 0xFF) == 0xFF && (header[1] & 0xFF) == 0xD8 && (header[2] & 0xFF) == 0xFF) {
            return MagicType.JPG;
        }
        if (len >= 8
                && (header[0] & 0xFF) == 0x89
                && header[1] == 0x50
                && header[2] == 0x4E
                && header[3] == 0x47
                && header[4] == 0x0D
                && header[5] == 0x0A
                && header[6] == 0x1A
                && header[7] == 0x0A) {
            return MagicType.PNG;
        }
        if (len >= 6) {
            String sig = new String(header, 0, 6);
            if ("GIF87a".equals(sig) || "GIF89a".equals(sig)) {
                return MagicType.GIF;
            }
        }
        if (len >= 12) {
            String riff = new String(header, 0, 4);
            String webp = new String(header, 8, 4);
            if ("RIFF".equals(riff) && "WEBP".equals(webp)) {
                return MagicType.WEBP;
            }
        }
        if (len >= 5) {
            String sig = new String(header, 0, 5);
            if ("%PDF-".equals(sig)) {
                return MagicType.PDF;
            }
        }
        return MagicType.UNKNOWN;
    }

    private ResponseStatusException reject(HttpStatus status, String reason, String username, String filename, long size) {
        LOGGER.warn(
                "Upload rejected: status={} reason={} user={} filename={} size={}",
                status.value(),
                reason,
                safeLogValue(username),
                safeLogValue(filename),
                size
        );
        return new ResponseStatusException(status, CLIENT_REJECT_REASON);
    }

    private static String safeLogValue(String value) {
        return value == null ? "-" : value;
    }

    private static long getFileCount(Path userDir) throws IOException {
        try (Stream<Path> files = Files.list(userDir)) {
            return files.filter(Files::isRegularFile).count();
        }
    }

    private void validateType(String contentType, String ext, MagicType magicType, String username, String filename, long size) {
        switch (magicType) {
            case JPG -> {
                if (!contentType.startsWith("image/")) {
                    throw reject(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Invalid image content type", username, filename, size);
                }
                if (!ext.equals(".jpg") && !ext.equals(".jpeg")) {
                    throw reject(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Invalid image extension", username, filename, size);
                }
            }
            case PNG -> {
                if (!contentType.startsWith("image/")) {
                    throw reject(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Invalid image content type", username, filename, size);
                }
                if (!ext.equals(".png")) {
                    throw reject(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Invalid image extension", username, filename, size);
                }
            }
            case GIF -> {
                if (!contentType.startsWith("image/")) {
                    throw reject(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Invalid image content type", username, filename, size);
                }
                if (!ext.equals(".gif")) {
                    throw reject(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Invalid image extension", username, filename, size);
                }
            }
            case WEBP -> {
                if (!contentType.startsWith("image/")) {
                    throw reject(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Invalid image content type", username, filename, size);
                }
                if (!ext.equals(".webp")) {
                    throw reject(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Invalid image extension", username, filename, size);
                }
            }
            case PDF -> {
                if (!contentType.contains("pdf")) {
                    throw reject(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Invalid PDF content type", username, filename, size);
                }
                if (!ext.equals(".pdf")) {
                    throw reject(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Invalid PDF extension", username, filename, size);
                }
            }
            default -> throw reject(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Unsupported file type", username, filename, size);
        }
    }
}
