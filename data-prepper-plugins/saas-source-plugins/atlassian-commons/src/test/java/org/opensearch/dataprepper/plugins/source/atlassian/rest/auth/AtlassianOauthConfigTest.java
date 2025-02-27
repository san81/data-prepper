/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 *
 */

package org.opensearch.dataprepper.plugins.source.atlassian.rest.auth;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.opensearch.dataprepper.model.plugin.PluginConfigVariable;
import org.opensearch.dataprepper.plugins.source.atlassian.AtlassianSourceConfig;
import org.opensearch.dataprepper.plugins.source.atlassian.configuration.Oauth2Config;
import org.opensearch.dataprepper.plugins.source.atlassian.utils.ConfigUtilForTests;
import org.opensearch.dataprepper.plugins.source.source_crawler.exception.UnauthorizedException;
import org.opensearch.dataprepper.test.helper.ReflectivelySetField;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.opensearch.dataprepper.plugins.source.atlassian.utils.Constants.MAX_RETRIES;

@ExtendWith(MockitoExtension.class)
public class AtlassianOauthConfigTest {

    @Mock
    RestTemplate restTemplateMock;


    AtlassianSourceConfig confluenceSourceConfig;

    @Mock
    PluginConfigVariable accessTokenVariable;

    @BeforeEach
    void setUp() {
        confluenceSourceConfig = ConfigUtilForTests.createJiraConfigurationFromYaml("oauth2-auth-jira-pipeline.yaml");
    }

    @Test
    void testRenewToken() {
        Instant testStartTime = Instant.now();
        Map<String, Object> firstMockResponseMap = Map.of("access_token", "first_mock_access_token",
                "refresh_token", "first_mock_refresh_token",
                "expires_in", 3600);
        AtlassianOauthConfig jiraOauthConfig = new AtlassianOauthConfig(confluenceSourceConfig);
        when(restTemplateMock.postForEntity(any(String.class), any(HttpEntity.class), any(Class.class)))
                .thenReturn(new ResponseEntity<>(firstMockResponseMap, HttpStatus.OK));
        jiraOauthConfig.restTemplate = restTemplateMock;
        ExecutorService executor = Executors.newFixedThreadPool(2);
        Future<?> firstCall = executor.submit(jiraOauthConfig::renewCredentials);
        Future<?> secondCall = executor.submit(jiraOauthConfig::renewCredentials);

        await()
                .atMost(10, SECONDS)
                .pollInterval(10, MILLISECONDS)
                .until(() -> firstCall.isDone() && secondCall.isDone());
        executor.shutdown();
        assertNotNull(jiraOauthConfig.getAccessToken());
        assertNotNull(jiraOauthConfig.getExpireTime());
        assertEquals(jiraOauthConfig.getRefreshToken(), "first_mock_refresh_token");
        assertEquals(jiraOauthConfig.getExpiresInSeconds(), 3600);
        assertEquals(jiraOauthConfig.getAccessToken(), "first_mock_access_token");
        assertTrue(jiraOauthConfig.getExpireTime().isAfter(testStartTime));
        Instant expectedNewExpireTime = Instant.ofEpochMilli(testStartTime.toEpochMilli() + 3601 * 1000);
        assertTrue(jiraOauthConfig.getExpireTime().isBefore(expectedNewExpireTime),
                String.format("Expected that %s time should be before %s", jiraOauthConfig.getExpireTime(), expectedNewExpireTime));
        verify(restTemplateMock, times(1)).postForEntity(any(String.class), any(HttpEntity.class), any(Class.class));

    }

    @Test
    void testFailedToRenewAccessToken() throws NoSuchFieldException, IllegalAccessException {
        AtlassianOauthConfig jiraOauthConfig = new AtlassianOauthConfig(confluenceSourceConfig);
        Oauth2Config oauth2Config = confluenceSourceConfig.getAuthenticationConfig().getOauth2Config();
        ReflectivelySetField.setField(Oauth2Config.class, oauth2Config, "accessToken", accessTokenVariable);
        when(restTemplateMock.postForEntity(any(String.class), any(HttpEntity.class), any(Class.class)))
                .thenThrow(HttpClientErrorException.class);
        jiraOauthConfig.restTemplate = restTemplateMock;
        assertThrows(RuntimeException.class, jiraOauthConfig::renewCredentials);
        verify(oauth2Config.getAccessToken(), times(0))
                .refresh();
    }

    @Test
    void testFailedToRenewAccessToken_with_unauthorized_and_trigger_secrets_refresh()
            throws NoSuchFieldException, IllegalAccessException {
        AtlassianOauthConfig jiraOauthConfig = new AtlassianOauthConfig(confluenceSourceConfig);
        Oauth2Config oauth2Config = confluenceSourceConfig.getAuthenticationConfig().getOauth2Config();
        ReflectivelySetField.setField(Oauth2Config.class, oauth2Config, "accessToken", accessTokenVariable);
        HttpClientErrorException unAuthorizedException = new HttpClientErrorException(HttpStatus.UNAUTHORIZED);
        when(restTemplateMock.postForEntity(any(String.class), any(HttpEntity.class), any(Class.class)))
                .thenThrow(unAuthorizedException);
        jiraOauthConfig.restTemplate = restTemplateMock;
        assertThrows(RuntimeException.class, jiraOauthConfig::renewCredentials);
        verify(oauth2Config.getAccessToken(), times(1))
                .refresh();
    }


    @Test
    void testGetTestAccountCloudId() {
        Map<String, Object> mockGetCallResponse = new HashMap<>();
        mockGetCallResponse.put("id", "test_cloud_id");
        when(restTemplateMock.exchange(any(String.class), any(HttpMethod.class), any(HttpEntity.class), any(Class.class)))
                .thenReturn(new ResponseEntity<>(List.of(mockGetCallResponse), HttpStatus.OK));
        AtlassianOauthConfig jiraOauthConfig = new AtlassianOauthConfig(confluenceSourceConfig);
        jiraOauthConfig.restTemplate = restTemplateMock;

        ExecutorService executor = Executors.newFixedThreadPool(2);
        Future<?> firstCall = executor.submit(jiraOauthConfig::getUrl);
        Future<?> secondCall = executor.submit(jiraOauthConfig::getUrl);

        await().atMost(10, SECONDS)
                .pollInterval(10, MILLISECONDS)
                .until(() -> firstCall.isDone() && secondCall.isDone());

        executor.shutdown();
        assertEquals("test_cloud_id", jiraOauthConfig.getAtlassianAccountCloudId());
        assertEquals("https://api.atlassian.com/ex/test/test_cloud_id/", jiraOauthConfig.getUrl());
        //calling second time shouldn't trigger rest call
        jiraOauthConfig.getUrl();
        verify(restTemplateMock, times(1))
                .exchange(any(String.class), any(HttpMethod.class), any(HttpEntity.class), any(Class.class));
    }

    @Test
    void testGetAtlassianAccountCloudIdUnauthorizedCase() {

        when(restTemplateMock.exchange(any(String.class), any(HttpMethod.class), any(HttpEntity.class), any(Class.class)))
                .thenThrow(new HttpClientErrorException(HttpStatus.UNAUTHORIZED));
        Map<String, Object> mockRenewTokenResponse = Map.of("access_token", "first_mock_access_token",
                "refresh_token", "first_mock_refresh_token",
                "expires_in", 3600);
        when(restTemplateMock.postForEntity(any(String.class), any(HttpEntity.class), any(Class.class)))
                .thenReturn(new ResponseEntity<>(mockRenewTokenResponse, HttpStatus.OK));
        AtlassianOauthConfig jiraOauthConfig = new AtlassianOauthConfig(confluenceSourceConfig);
        jiraOauthConfig.restTemplate = restTemplateMock;


        assertThrows(UnauthorizedException.class, jiraOauthConfig::initCredentials);
        verify(restTemplateMock, times(6))
                .exchange(any(String.class), any(HttpMethod.class), any(HttpEntity.class), any(Class.class));
        verify(restTemplateMock, times(1))
                .postForEntity(any(String.class), any(HttpEntity.class), any(Class.class));

    }

    @Test
    void testFailedToGetCloudId() {
        when(restTemplateMock.exchange(any(String.class), any(HttpMethod.class), any(HttpEntity.class), any(Class.class)))
                .thenThrow(new HttpClientErrorException(HttpStatus.UNAUTHORIZED))
                .thenThrow(HttpClientErrorException.class);
        AtlassianOauthConfig jiraOauthConfig = new AtlassianOauthConfig(confluenceSourceConfig);
        jiraOauthConfig.restTemplate = restTemplateMock;
        assertThrows(RuntimeException.class, jiraOauthConfig::getUrl);
        for (int i = 0; i <= MAX_RETRIES; i++) {
            assertThrows(RuntimeException.class, jiraOauthConfig::getUrl);
        }
    }

}
