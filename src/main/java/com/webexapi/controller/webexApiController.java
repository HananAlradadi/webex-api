package com.webexapi.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;
import java.io.*;

@RestController
@RequestMapping("/webex")
public class webexApiController {

    @Value("${webex.client-id}")
    private String clientId;

    @Value("${webex.client-secret}")
    private String clientSecret;

    @Value("${webex.redirect-uri}")
    private String redirectUri;

    @Value("${webex.scope}")
    private String scope;

    private final RestTemplate restTemplate = new RestTemplate();

    private String accessToken;
    private Instant tokenExpiry;

    @GetMapping("/login")
    public void login(HttpServletResponse response) throws IOException {
        String redirect = UriComponentsBuilder
                .fromHttpUrl("https://webexapis.com/v1/authorize")
                .queryParam("client_id", clientId)
                .queryParam("response_type", "code")
                .queryParam("redirect_uri", redirectUri)
                .queryParam("scope", scope)
                .queryParam("state", "xyz123")
                .build().toUriString();

        response.sendRedirect(redirect);
    }

    @GetMapping("/oauth")
    public ResponseEntity<String> callback(@RequestParam String code) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("grant_type", "authorization_code");
        params.add("client_id", clientId);
        params.add("client_secret", clientSecret);
        params.add("code", code);
        params.add("redirect_uri", redirectUri);

        HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(params, headers);

        ResponseEntity<Map> response = restTemplate.postForEntity(
                "https://webexapis.com/v1/access_token",
                request,
                Map.class
        );

        if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
            accessToken = (String) response.getBody().get("access_token");
            Integer expiresIn = (Integer) response.getBody().get("expires_in");
            tokenExpiry = Instant.now().plus(expiresIn, ChronoUnit.SECONDS);
        }

        return ResponseEntity.ok("Access token received and stored.");
    }

    @PostMapping("/create-meeting")
    public ResponseEntity<String> createMeeting() {
        if (accessToken == null || Instant.now().isAfter(tokenExpiry)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Access token is missing or expired. Please login again.");
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(accessToken);

        String meetingJson = """
        {
          "title": "Spring Boot Webex Meeting",
          "start": "2025-05-15T08:40:00+03:00",
          "end": "2025-05-15T18:00:00+03:00"
        }
        """;

        HttpEntity<String> request = new HttpEntity<>(meetingJson, headers);

        ResponseEntity<String> response = restTemplate.postForEntity(
                "https://webexapis.com/v1/meetings",
                request,
                String.class
        );

        return ResponseEntity.status(response.getStatusCode()).body(response.getBody());
    }

    @GetMapping("/token")
    public ResponseEntity<Map<String, String>> getAccessToken() {
            if (accessToken == null || Instant.now().isAfter(tokenExpiry)) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(Map.of("error", "Access token is missing or expired."));
            }
            return ResponseEntity.ok(Map.of("access_token", accessToken));
        }

    @PostMapping("/join-link")
    public ResponseEntity<?> generateJoinLinkViaApi(@RequestBody Map<String, Object> payload) {
        if (accessToken == null || Instant.now().isAfter(tokenExpiry)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(
                    Map.of("error", "Access token is missing or expired. Please login again.")
            );
        }

        if (!payload.containsKey("meetingId") && !payload.containsKey("meetingNumber") && !payload.containsKey("webLink")) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(
                    Map.of("error", "You must provide either meetingId, meetingNumber, or webLink in the request body.")
            );
        }

        payload.put("joinDirectly", false);

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(payload, headers);

        try {
            ResponseEntity<Map> response = restTemplate.postForEntity(
                    "https://webexapis.com/v1/meetings/join",
                    request,
                    Map.class
            );

            Map<?, ?> responseBody = response.getBody();

            if ((response.getStatusCode().is2xxSuccessful() || response.getStatusCode().is3xxRedirection())
                    && responseBody != null && responseBody.get("joinLink") != null) {

                Map<String, Object> safeResponse = new HashMap<>();
                if (responseBody.get("joinLink") != null) {
                    safeResponse.put("joinLink", responseBody.get("joinLink"));
                }
                if (responseBody.get("startLink") != null) {
                    safeResponse.put("startLink", responseBody.get("startLink"));
                }
                if (responseBody.get("expiration") != null) {
                    safeResponse.put("expiration", responseBody.get("expiration"));
                }

                return ResponseEntity.ok(safeResponse);
            } else {
                return ResponseEntity.status(response.getStatusCode()).body(
                        Map.of(
                                "error", "Failed to get join link from Webex API",
                                "status", response.getStatusCode().value(),
                                "details", responseBody != null ? responseBody : Map.of()
                        )
                );
            }
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                    Map.of(
                            "error", "Exception occurred while generating join link",
                            "message", e.getMessage()
                    )
            );
        }
    }

    @PostMapping("/audio-stream")
    public ResponseEntity<String> streamAudio(HttpServletRequest request) {
        try (InputStream inputStream = request.getInputStream()) {
            byte[] buffer = new byte[4096];
            ByteArrayOutputStream chunk = new ByteArrayOutputStream();
            int bytesRead;
            long startTime = System.currentTimeMillis();
            int fileIndex = 1;

            while ((bytesRead = inputStream.read(buffer)) != -1) {
                chunk.write(buffer, 0, bytesRead);

                long now = System.currentTimeMillis();
                if (now - startTime >= 10_000) {
                    saveToFile(chunk.toByteArray(), fileIndex++);
                    chunk.reset();
                    startTime = now;
                }
            }

            if (chunk.size() > 0) {
                saveToFile(chunk.toByteArray(), fileIndex);
            }

        } catch (IOException e) {
            e.printStackTrace();
            return ResponseEntity.status(500).body("Streaming failed");
        }

        return ResponseEntity.ok("Audio chunks saved successfully");
    }

    private void saveToFile(byte[] data, int index) throws IOException {
        String directory = "audio-chunks";
        File dir = new File(directory);
        if (!dir.exists()) {
            dir.mkdirs();
        }
        String filename = directory + "/audio_chunk_" + index + ".wav";
        try (FileOutputStream fos = new FileOutputStream(filename)) {
            fos.write(data);
        }
    }
}




