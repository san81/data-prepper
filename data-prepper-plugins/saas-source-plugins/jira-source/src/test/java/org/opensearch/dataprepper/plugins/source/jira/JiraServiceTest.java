package org.opensearch.dataprepper.plugins.source.jira;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.google.gson.internal.LinkedTreeMap;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.opensearch.dataprepper.plugins.source.jira.exception.BadRequestException;
import org.opensearch.dataprepper.plugins.source.jira.models.IssueBean;
import org.opensearch.dataprepper.plugins.source.jira.models.SearchResults;
import org.opensearch.dataprepper.plugins.source.jira.rest.auth.JiraAuthConfig;
import org.opensearch.dataprepper.plugins.source.jira.rest.auth.JiraAuthFactory;
import org.opensearch.dataprepper.plugins.source.source_crawler.base.PluginExecutorServiceProvider;
import org.opensearch.dataprepper.plugins.source.source_crawler.model.ItemInfo;
import org.slf4j.Logger;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import javax.inject.Named;
import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;
import static org.opensearch.dataprepper.plugins.source.jira.utils.Constants.ACCESSIBLE_RESOURCES;
import static org.opensearch.dataprepper.plugins.source.jira.utils.Constants.BASIC;
import static org.opensearch.dataprepper.plugins.source.jira.utils.Constants.CREATED;
import static org.opensearch.dataprepper.plugins.source.jira.utils.Constants.KEY;
import static org.opensearch.dataprepper.plugins.source.jira.utils.Constants.UPDATED;


/**
 * The type Jira service.
 */
@ExtendWith(MockitoExtension.class)
@Named
public class JiraServiceTest {

    @Mock
    private RestTemplate restTemplate;

    @Mock
    private JiraAuthConfig authConfig;

    @Mock
    private SearchResults mockSearchResults;

    @Mock
    private StringBuilder jql;

    private static final Logger log = org.slf4j.LoggerFactory.getLogger(JiraServiceTest.class);
    private JiraSourceConfig jiraSourceConfig;
    private Queue<ItemInfo> itemInfoQueue;
    private List<Future<Boolean>> futureList;
    private ExecutorService crawlerTaskExecutor;
    private PluginExecutorServiceProvider executorServiceProvider = new PluginExecutorServiceProvider();
    private JiraService jiraService;
    private List<String> issueType;

    @BeforeEach
    void setUp() {
        itemInfoQueue = new ConcurrentLinkedQueue<>();
        crawlerTaskExecutor = executorServiceProvider.get();
        issueType = new ArrayList<>();
        futureList = new ArrayList<>();
    }

    @AfterEach
    void tearDown() {
        executorServiceProvider.terminateExecutor();
    }

    @Test
    void testJiraServiceInitialization() throws JsonProcessingException {
        jiraSourceConfig = createJiraConfiguration(BASIC, issueType);
        jiraService = new JiraService(restTemplate, jiraSourceConfig, authConfig);
        assertNotNull(jiraService);
    }

    @Test
    public void testGetJiraEntities() throws JsonProcessingException, InterruptedException, TimeoutException {
        issueType.add("Task");
        jiraSourceConfig = createJiraConfiguration(BASIC, issueType);
        jiraService = spy(new JiraService(restTemplate, jiraSourceConfig, authConfig));
        List<IssueBean> mockIssues = new ArrayList<>();
        IssueBean issue1 = createIssueBean(false);
        mockIssues.add(issue1);
        IssueBean issue2 = createIssueBean(true);
        mockIssues.add(issue2);

        when(mockSearchResults.getIssues()).thenReturn(mockIssues);
        when(mockSearchResults.getTotal()).thenReturn(mockIssues.size());

        doReturn(mockSearchResults).when(jiraService).getAllIssues(any(StringBuilder.class), anyInt(), any(JiraSourceConfig.class));

        Instant timestamp = Instant.ofEpochSecond(0);
        jiraService.getJiraEntities(jiraSourceConfig, timestamp, itemInfoQueue, futureList, crawlerTaskExecutor);

        waitForFutures();

        assertEquals(mockIssues.size(), itemInfoQueue.size());
    }

    @Test
    public void buildIssueItemInfoMultipleFutureThreads() throws JsonProcessingException, InterruptedException, TimeoutException {
        issueType.add("Task");
        jiraSourceConfig = createJiraConfiguration(BASIC, issueType);
        jiraService = spy(new JiraService(restTemplate, jiraSourceConfig, authConfig));
        List<IssueBean> mockIssues = new ArrayList<>();
        for (int i = 0; i < 50; i++) {
            IssueBean issue1 = createIssueBean(false);
            mockIssues.add(issue1);
        }

        when(mockSearchResults.getIssues()).thenReturn(mockIssues);
        when(mockSearchResults.getTotal()).thenReturn(100);

        doReturn(mockSearchResults).when(jiraService).getAllIssues(any(StringBuilder.class), anyInt(), any(JiraSourceConfig.class));

        Instant timestamp = Instant.ofEpochSecond(0);
        jiraService.getJiraEntities(jiraSourceConfig, timestamp, itemInfoQueue, futureList, crawlerTaskExecutor);

        waitForFutures();

        assertEquals(100, itemInfoQueue.size());
    }

    @Test
    public void testGetJiraEntitiesException() throws JsonProcessingException {
        issueType.add("Task");
        jiraSourceConfig = createJiraConfiguration(BASIC, issueType);
        jiraService = spy(new JiraService(restTemplate, jiraSourceConfig, authConfig));

        doThrow(RuntimeException.class).when(jiraService).getAllIssues(any(StringBuilder.class), anyInt(), any(JiraSourceConfig.class));

        Instant timestamp = Instant.ofEpochSecond(0);
        assertThrows(RuntimeException.class, () -> {
            jiraService.getJiraEntities(jiraSourceConfig, timestamp, itemInfoQueue, futureList, crawlerTaskExecutor);
        });
    }


    @Test
    public void testGetAllIssuesBasic() throws JsonProcessingException {
        issueType.add("Task");
        jiraSourceConfig = createJiraConfiguration(BASIC, issueType);
        jiraService = new JiraService(restTemplate, jiraSourceConfig, authConfig);
        SearchResults results = jiraService.getAllIssues(jql, 0, jiraSourceConfig);
        assertNotNull(results);
    }

    @Test
    public void testGetAllIssuesInvalidAuthType() throws JsonProcessingException {
        issueType.add("Task");
        jiraSourceConfig = createJiraConfiguration("Invalid Auth Type", issueType);
        jiraService = new JiraService(restTemplate, jiraSourceConfig, authConfig);
        assertThrows(BadRequestException.class, () -> {
            jiraService.getAllIssues(jql, 0, jiraSourceConfig);
        });
    }

    private JiraSourceConfig createJiraConfiguration(String auth_type, List<String> issueType) throws JsonProcessingException {
        ObjectMapper objectMapper = new ObjectMapper();
        Map<String, String> connectorCredentialsMap = new HashMap<>();
        connectorCredentialsMap.put("auth_type", auth_type);

        Map<String, Object> jiraSourceConfigMap = new HashMap<>();
        jiraSourceConfigMap.put("account_url", ACCESSIBLE_RESOURCES);
        jiraSourceConfigMap.put("connector_credentials", connectorCredentialsMap);
        jiraSourceConfigMap.put("issue_type", issueType);

        String jiraSourceConfigJsonString = objectMapper.writeValueAsString(jiraSourceConfigMap);
        return objectMapper.readValue(jiraSourceConfigJsonString, JiraSourceConfig.class);
    }

    private IssueBean createIssueBean(boolean nullFields) {
        IssueBean issue1 = new IssueBean();
        issue1.setId("123");
        issue1.setKey("issue_1_key");
        issue1.setSelf("https://example.com/rest/api/2/issue/123");
        issue1.setExpand("operations,versionedRepresentations,editmeta");

        Map<String, Object> fieldMap = new LinkedTreeMap<>();
        if (!nullFields) {
            fieldMap.put(CREATED, Instant.now());
            fieldMap.put(UPDATED, Instant.now());
        } else {
            fieldMap.put(CREATED, 0);
            fieldMap.put(UPDATED, 0);
        }

        Map<String, Object> issueTypeMap = new LinkedTreeMap<>();
        issueTypeMap.put("name", "Task");
        issueTypeMap.put("self", "https://example.com/rest/api/2/issuetype/1");
        issueTypeMap.put("id", "1");
        fieldMap.put("issuetype", issueTypeMap);

        Map<String, Object> projectMap = new LinkedTreeMap<>();
        if (!nullFields) {
            projectMap.put("name", "project name test");
            projectMap.put(KEY, "TEST");
        }
        fieldMap.put("project", projectMap);

        Map<String, Object> priorityMap = new LinkedTreeMap<>();
        priorityMap.put("name", "Medium");
        fieldMap.put("priority", priorityMap);

        Map<String, Object> statusMap = new LinkedTreeMap<>();
        statusMap.put("name", "In Progress");
        fieldMap.put("status", statusMap);

        issue1.setFields(fieldMap);

        return issue1;
    }

    private void waitForFutures() throws InterruptedException, TimeoutException {
        for (Future<?> future : futureList) {
            try {
                future.get(1000, TimeUnit.MILLISECONDS);
            } catch (InterruptedException ie) {
                log.error("Thread interrupted.", ie);
                throw new InterruptedException(ie.getMessage());
            } catch (ExecutionException xe) {
                log.error("The task aborted when attempting to retrieve its result.", xe);
            } catch (TimeoutException te) {
                log.error("Future is not done when timeout.", te);
                throw new TimeoutException(te.getMessage());
            }
        }
    }

    private static InputStream getResourceAsStream(String resourceName) {
        InputStream inputStream = Thread.currentThread().getContextClassLoader().getResourceAsStream(resourceName);
        if (inputStream == null) {
            inputStream = JiraServiceTest.class.getResourceAsStream("/" + resourceName);
        }
        return inputStream;
    }

    @ParameterizedTest
    @ValueSource(strings = {"basic-auth-jira-pipeline.yaml"})
    public void testFetchingJiraIssue(String configFileName) {
        when(restTemplate.getForEntity(any(String.class), any(Class.class))).thenReturn(new ResponseEntity<>("", HttpStatus.OK));
        JiraSourceConfig jiraSourceConfig = createJiraConfigurationFromYaml(configFileName);
        JiraAuthConfig authConfig = new JiraAuthFactory(jiraSourceConfig).getObject();
        JiraService jiraService = new JiraService(restTemplate, jiraSourceConfig, authConfig);
        String ticketDetails = jiraService.getIssue("key");
        assertNotNull(ticketDetails);
    }

    private JiraSourceConfig createJiraConfigurationFromYaml(String fileName) {
        ObjectMapper objectMapper = new ObjectMapper(new YAMLFactory());
        try (InputStream inputStream = getResourceAsStream(fileName)) {
            return objectMapper.readValue(inputStream, JiraSourceConfig.class);
        } catch (IOException ex) {
            log.error("Failed to parse pipeline Yaml", ex);
        }
        return null;
    }


}