package com.webexapi.controller;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.Collections;
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

            ResponseEntity<String> response = restTemplate.postForEntity(
                    "https://webexapis.com/v1/access_token",
                    request,
                    String.class
            );

            return ResponseEntity.ok(response.getBody());
        }


    }


