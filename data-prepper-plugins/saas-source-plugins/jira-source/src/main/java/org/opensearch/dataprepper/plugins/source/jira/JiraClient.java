/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 *
 */

package org.opensearch.dataprepper.plugins.source.jira;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.annotations.VisibleForTesting;
import org.opensearch.dataprepper.model.event.Event;
import org.opensearch.dataprepper.model.record.Record;
import org.opensearch.dataprepper.plugins.source.atlassian.base.DefaultCrawlerClient;
import org.opensearch.dataprepper.plugins.source.source_crawler.base.CrawlerSourceConfig;
import org.opensearch.dataprepper.plugins.source.source_crawler.base.PluginExecutorServiceProvider;
import org.opensearch.dataprepper.plugins.source.source_crawler.coordination.state.SaasWorkerProgressState;
import org.opensearch.dataprepper.plugins.source.source_crawler.model.ItemInfo;

import javax.inject.Named;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;

import static org.opensearch.dataprepper.plugins.source.jira.utils.Constants.PROJECT;

/**
 * This class represents a Jira client.
 */
@Named
public class JiraClient extends DefaultCrawlerClient {

    private ObjectMapper objectMapper = new ObjectMapper();
    private final JiraService service;
    private final JiraIterator jiraIterator;
    private final ExecutorService executorService;
    private final CrawlerSourceConfig configuration;

    public JiraClient(JiraService service,
                      JiraIterator jiraIterator,
                      PluginExecutorServiceProvider executorServiceProvider,
                      JiraSourceConfig sourceConfig) {
        super(sourceConfig);
        this.service = service;
        this.jiraIterator = jiraIterator;
        this.executorService = executorServiceProvider.get();
        this.configuration = sourceConfig;
    }

    @Override
    public Iterator<ItemInfo> listItems(Instant lastPollTime) {
        jiraIterator.initialize(lastPollTime);
        return jiraIterator;
    }

    public List<ItemInfo> getListOfItemInfosFromState(SaasWorkerProgressState state) {
        List<String> itemIds = state.getItemIds();
        Map<String, Object> keyAttributes = state.getKeyAttributes();
        String project = (String) keyAttributes.get(PROJECT);
        Instant eventTime = state.getExportStartTime();
        List<ItemInfo> itemInfos = new ArrayList<>();
        for (String itemId : itemIds) {
            if (itemId == null) {
                continue;
            }
            ItemInfo itemInfo = JiraItemInfo.builder()
                    .withItemId(itemId)
                    .withId(itemId)
                    .withProject(project)
                    .withEventTime(eventTime)
                    .withMetadata(keyAttributes).build();
            itemInfos.add(itemInfo);
        }
        return itemInfos;
    }

    public CompletableFuture<Record<Event>> processItemAsync(ItemInfo item) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String contentJson = service.getIssue(item.getId());
                ObjectNode contentJsonObj = objectMapper.readValue(contentJson, new TypeReference<>() {
                });
                Record<Event> record = new Record<>(createEvent(contentJsonObj));
                record.getData().getMetadata().setAttribute(PROJECT, ((JiraItemInfo) item).getProject().toLowerCase());
                return record;
            } catch (Exception e) {
                return new Record<>(createErrorEvent(item, e));
            }
        }, executorService);
    }

    @VisibleForTesting
    void injectObjectMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

}
