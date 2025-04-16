package org.opensearch.dataprepper.plugins.source.atlassian.base;

import com.fasterxml.jackson.databind.JsonNode;
import org.opensearch.dataprepper.model.acknowledgements.AcknowledgementSet;
import org.opensearch.dataprepper.model.buffer.Buffer;
import org.opensearch.dataprepper.model.event.Event;
import org.opensearch.dataprepper.model.event.EventType;
import org.opensearch.dataprepper.model.event.JacksonEvent;
import org.opensearch.dataprepper.model.record.Record;
import org.opensearch.dataprepper.plugins.source.source_crawler.base.CrawlerClient;
import org.opensearch.dataprepper.plugins.source.source_crawler.base.CrawlerSourceConfig;
import org.opensearch.dataprepper.plugins.source.source_crawler.coordination.state.SaasWorkerProgressState;
import org.opensearch.dataprepper.plugins.source.source_crawler.model.ItemInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

public abstract class DefaultCrawlerClient implements CrawlerClient {

    private static final Logger log = LoggerFactory.getLogger(DefaultCrawlerClient.class);
    private static final String ERROR_EVENT_TYPE = "error";
    private final int bufferWriteTimeoutInSeconds = 10;
    private final CrawlerSourceConfig configuration;

    public DefaultCrawlerClient(final CrawlerSourceConfig configuration) {
        this.configuration = configuration;
    }

    public abstract List<ItemInfo> getListOfItemInfosFromState(SaasWorkerProgressState state);

    // Constructor and other necessary fields...

    public List<Record<Event>> processItems(List<ItemInfo> itemInfos) {
        return itemInfos.stream()
                // 1. Create CompletableFutures for each item
                .map(this::processItemAsync)
                // 2. Collect all futures
                .collect(Collectors.toList())
                // 3. Wait for all futures to complete
                .stream()
                .map(CompletableFuture::join)
                // 4. Collect results
                .collect(Collectors.toList());
    }

    public abstract CompletableFuture<Record<Event>> processItemAsync(ItemInfo item);

    public Event createEvent(JsonNode data) {
        return JacksonEvent.builder()
                .withEventType(EventType.DOCUMENT.toString())
                .withData(data)
                .build();
    }

    public Event createErrorEvent(ItemInfo item, Exception e) {
        return JacksonEvent.builder()
                .withEventType(ERROR_EVENT_TYPE)
                .withData(Map.of("itemId", item.getId(), "errorMessage", e.getMessage()))
                .build();
    }

    @Override
    public void executePartition(SaasWorkerProgressState state,
                                 Buffer<Record<Event>> buffer,
                                 AcknowledgementSet acknowledgementSet) {
        log.trace("Executing the partition: {} with {} ticket(s)",
                state.getKeyAttributes(), state.getItemIds().size());

        List<ItemInfo> itemInfos = getListOfItemInfosFromState(state);
        List<Record<Event>> allRecords = processItems(itemInfos);
        List<Record<Event>> recordsToWrite = new ArrayList<>();
        StringBuilder aggregatedErrorMsg = new StringBuilder();

        for (Record<Event> record : allRecords) {
            if (record.getData().getMetadata().getEventType().equals(ERROR_EVENT_TYPE)) {
                aggregatedErrorMsg.append(record.getData().toJsonString()).append(" ");
            } else {
                recordsToWrite.add(record);
            }
        }

        try {
            if (configuration.isAcknowledgments()) {
                recordsToWrite.forEach(eventRecord -> acknowledgementSet.add(eventRecord.getData()));
                buffer.writeAll(recordsToWrite, (int) Duration.ofSeconds(bufferWriteTimeoutInSeconds).toMillis());
                acknowledgementSet.complete();
            } else {
                buffer.writeAll(recordsToWrite, (int) Duration.ofSeconds(bufferWriteTimeoutInSeconds).toMillis());
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        if (allRecords.size() != recordsToWrite.size()) {
            //There are some failed records
            throw new RuntimeException("Error processing item:" + aggregatedErrorMsg);
        }
    }
}
