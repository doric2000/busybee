package com.securefromscratch.busybee.storage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

@Service
public class UrlImageDownloader {
    private static final Logger LOGGER = LoggerFactory.getLogger(UrlImageDownloader.class);

    private static final int CONNECT_TIMEOUT_MS = 5_000;
    private static final int READ_TIMEOUT_MS = 5_000;

    /**
     * Downloads a remote image via http/https with SSRF protections, then stores it using FileStorage.
     */
    public String downloadAndStore(String url, String username) throws IOException {
        URI uri = parseAndValidateUrl(url, username);
        LOGGER.info(
            "URL upload attempt: user={} scheme={} host={}",
            FileStorage.safeLogValue(username),
            FileStorage.safeLogValue(uri.getScheme()),
            FileStorage.safeLogValue(uri.getHost())
        );

        validateHostResolvesToPublicIps(uri, username);

        URL httpUrl = uri.toURL();
        HttpURLConnection connection = (HttpURLConnection) httpUrl.openConnection();
        connection.setInstanceFollowRedirects(false);
        connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
        connection.setReadTimeout(READ_TIMEOUT_MS);
        connection.setRequestMethod("GET");
        connection.setRequestProperty("User-Agent", "busybee/1.0");

        int status;
        try {
            status = connection.getResponseCode();
        } catch (IOException ex) {
            LOGGER.warn(
                    "URL upload fetch failed: user={} host={}",
                    FileStorage.safeLogValue(username),
                    FileStorage.safeLogValue(uri.getHost())
            );
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Failed to fetch URL");
        }

        if (status / 100 != 2) {
            LOGGER.warn(
                    "URL upload rejected (non-OK status): user={} host={} status={}",
                    FileStorage.safeLogValue(username),
                    FileStorage.safeLogValue(uri.getHost()),
                    status
            );
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "URL returned non-OK status");
        }

        long contentLength = connection.getContentLengthLong();
        if (contentLength > FileStorage.MAX_UPLOAD_BYTES) {
            LOGGER.warn(
                    "URL upload rejected (content-length too large): user={} host={} len={}",
                    FileStorage.safeLogValue(username),
                    FileStorage.safeLogValue(uri.getHost()),
                    contentLength
            );
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "File too large");
        }

        String contentType = normalizeContentType(connection.getContentType());
        String filename = guessFilename(uri, contentType);

        try (InputStream in = connection.getInputStream()) {
            FileStorage storage = new FileStorage(Path.of("uploads").toAbsolutePath().normalize());
            // storeUploadFromStream enforces MAX_UPLOAD_BYTES while streaming.
            String stored = storage.storeUploadFromStream(in, filename, username, contentType);
            LOGGER.info("URL upload stored: user={} urlHost={} stored={}", FileStorage.safeLogValue(username), FileStorage.safeLogValue(uri.getHost()), FileStorage.safeLogValue(stored));
            return stored;
        } catch (ResponseStatusException ex) {
            LOGGER.warn(
                    "URL upload rejected: user={} host={} status={}",
                    FileStorage.safeLogValue(username),
                    FileStorage.safeLogValue(uri.getHost()),
                    ex.getStatusCode().value()
            );
            throw ex;
        } catch (IOException ex) {
            LOGGER.warn("URL upload failed: user={} urlHost={}", FileStorage.safeLogValue(username), FileStorage.safeLogValue(uri.getHost()), ex);
            throw ex;
        } finally {
            connection.disconnect();
        }
    }

    private static URI parseAndValidateUrl(String url, String username) {
        if (url == null || url.isBlank()) {
            LOGGER.warn("URL upload rejected (missing URL): user={}", FileStorage.safeLogValue(username));
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Missing URL");
        }

        URI uri;
        try {
            uri = new URI(url.trim());
        } catch (URISyntaxException ex) {
            LOGGER.warn("URL upload rejected (invalid URL syntax): user={}", FileStorage.safeLogValue(username));
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid URL");
        }

        String scheme = (uri.getScheme() == null) ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        if (!scheme.equals("http") && !scheme.equals("https")) {
            LOGGER.warn(
                    "URL upload rejected (unsupported scheme): user={} scheme={}",
                    FileStorage.safeLogValue(username),
                    FileStorage.safeLogValue(scheme)
            );
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported URL protocol");
        }

        if (uri.getUserInfo() != null) {
            LOGGER.warn("URL upload rejected (credentials in URL): user={}", FileStorage.safeLogValue(username));
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "URL must not contain credentials");
        }

        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            LOGGER.warn("URL upload rejected (missing host): user={}", FileStorage.safeLogValue(username));
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "URL missing host");
        }

        int port = uri.getPort();
        if (port != -1 && (port < 1 || port > 65535)) {
            LOGGER.warn(
                    "URL upload rejected (invalid port): user={} host={} port={}",
                    FileStorage.safeLogValue(username),
                    FileStorage.safeLogValue(host),
                    port
            );
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid port");
        }

        return uri;
    }

    private static void validateHostResolvesToPublicIps(URI uri, String username) {
        String host = uri.getHost();
        InetAddress[] resolved;
        try {
            resolved = InetAddress.getAllByName(host);
        } catch (IOException ex) {
            LOGGER.warn(
                    "URL upload rejected (DNS failure): user={} host={}",
                    FileStorage.safeLogValue(username),
                    FileStorage.safeLogValue(host)
            );
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Host resolution failed");
        }

        if (resolved.length == 0) {
            LOGGER.warn(
                    "URL upload rejected (DNS empty): user={} host={}",
                    FileStorage.safeLogValue(username),
                    FileStorage.safeLogValue(host)
            );
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Host resolution failed");
        }

        for (InetAddress addr : resolved) {
            if (!isPublicAddress(addr)) {
                LOGGER.warn(
                        "URL upload rejected (SSRF blocked): user={} host={} ip={}",
                        FileStorage.safeLogValue(username),
                        FileStorage.safeLogValue(host),
                        FileStorage.safeLogValue(addr.getHostAddress())
                );
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Blocked host address");
            }
        }
    }

    private static boolean isPublicAddress(InetAddress addr) {
        // Defense-in-depth: block all special/private ranges (IPv4+IPv6)
        return !(addr.isAnyLocalAddress()
                || addr.isLoopbackAddress()
                || addr.isLinkLocalAddress()
                || addr.isSiteLocalAddress()
                || addr.isMulticastAddress());
    }

    private static String guessFilename(URI uri, String contentType) {
        // Always generate a safe filename (FileStorage enforces a strict whitelist).
        // Select extension inferred from content-type.
        String ext = switch (normalizeContentType(contentType)) {
            case "image/jpeg" -> ".jpg";
            case "image/png" -> ".png";
            case "image/gif" -> ".gif";
            case "image/webp" -> ".webp";
            case "application/pdf" -> ".pdf";
            default -> ".png";
        };

        return "download" + ext;
    }

    private static String normalizeContentType(String contentType) {
        if (contentType == null) {
            return "";
        }
        String trimmed = contentType.trim();
        int semi = trimmed.indexOf(';');
        if (semi >= 0) {
            trimmed = trimmed.substring(0, semi);
        }
        // basic ASCII lowercasing
        return new String(trimmed.getBytes(StandardCharsets.US_ASCII), StandardCharsets.US_ASCII)
                .toLowerCase(Locale.ROOT)
                .trim();
    }
}
