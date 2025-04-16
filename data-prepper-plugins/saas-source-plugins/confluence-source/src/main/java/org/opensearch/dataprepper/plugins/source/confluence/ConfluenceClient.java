/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 *
 */

package org.opensearch.dataprepper.plugins.source.confluence;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.annotations.VisibleForTesting;
import org.opensearch.dataprepper.model.event.Event;
import org.opensearch.dataprepper.model.record.Record;
import org.opensearch.dataprepper.plugins.source.atlassian.base.DefaultCrawlerClient;
import org.opensearch.dataprepper.plugins.source.confluence.utils.HtmlToTextConversionUtil;
import org.opensearch.dataprepper.plugins.source.source_crawler.base.CrawlerSourceConfig;
import org.opensearch.dataprepper.plugins.source.source_crawler.base.PluginExecutorServiceProvider;
import org.opensearch.dataprepper.plugins.source.source_crawler.coordination.state.SaasWorkerProgressState;
import org.opensearch.dataprepper.plugins.source.source_crawler.model.ItemInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Named;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;

import static org.opensearch.dataprepper.plugins.source.confluence.utils.Constants.SPACE;

/**
 * This class represents a Confluence client.
 */
@Named
public class ConfluenceClient extends DefaultCrawlerClient {

    private static final Logger log = LoggerFactory.getLogger(ConfluenceClient.class);
    private ObjectMapper objectMapper = new ObjectMapper();
    private final ConfluenceService service;
    private final ConfluenceIterator confluenceIterator;
    private final ExecutorService executorService;
    private final CrawlerSourceConfig configuration;

    public ConfluenceClient(ConfluenceService service,
                            ConfluenceIterator confluenceIterator,
                            PluginExecutorServiceProvider executorServiceProvider,
                            ConfluenceSourceConfig sourceConfig) {
        super(sourceConfig);
        this.service = service;
        this.confluenceIterator = confluenceIterator;
        this.executorService = executorServiceProvider.get();
        this.configuration = sourceConfig;
    }

    @Override
    public Iterator<ItemInfo> listItems(Instant lastPollTime) {
        confluenceIterator.initialize(lastPollTime);
        return confluenceIterator;
    }

    public List<ItemInfo> getListOfItemInfosFromState(SaasWorkerProgressState state) {
        List<String> itemIds = state.getItemIds();
        Map<String, Object> keyAttributes = state.getKeyAttributes();
        String space = (String) keyAttributes.get(SPACE);
        Instant eventTime = state.getExportStartTime();
        List<ItemInfo> itemInfos = new ArrayList<>();
        for (String itemId : itemIds) {
            if (itemId == null) {
                continue;
            }
            ItemInfo itemInfo = ConfluenceItemInfo.builder()
                    .withItemId(itemId)
                    .withId(itemId)
                    .withSpace(space)
                    .withEventTime(eventTime)
                    .withMetadata(keyAttributes).build();
            itemInfos.add(itemInfo);
        }
        return itemInfos;
    }

    public CompletableFuture<Record<Event>> processItemAsync(ItemInfo item) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String contentJson = service.getContent(item.getId());
                ObjectNode contentJsonObj = objectMapper.readValue(contentJson, new TypeReference<>() {
                });
                JsonNode convertedText = HtmlToTextConversionUtil.convertHtmlToText(contentJsonObj, "body/view/value");
                Record<Event> record = new Record<>(createEvent(convertedText));
                record.getData().getMetadata().setAttribute(SPACE, ((ConfluenceItemInfo) item).getSpace().toLowerCase());
                return record;
            } catch (Exception e) {
                return new Record<>(createErrorEvent(item, e));
            }
        }, executorService);
    }

    @VisibleForTesting
    public void injectObjectMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

}
