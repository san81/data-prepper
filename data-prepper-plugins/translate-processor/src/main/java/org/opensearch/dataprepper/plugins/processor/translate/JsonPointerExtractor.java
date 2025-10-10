/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.dataprepper.plugins.processor.translate;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;

/**
 * JsonPointerExtractor provides optimized path-based extraction from JsonNode objects
 * using direct JsonNode traversal instead of full Map conversion.
 * 
 * This is a performance-optimized alternative to JsonExtractor for nested path scenarios.
 * It provides early-exit capability when paths don't exist, avoiding expensive toMap() calls.
 * 
 * Performance Benefits:
 * - Avoids full Event.toMap() conversion when paths are missing
 * - Works directly on JsonNode for faster path existence checks
 * - Maintains behavioral compatibility with JsonExtractor
 * 
 * Usage: Used by TranslateProcessor for nested path validation before expensive operations.
 */
public class JsonPointerExtractor {
    private final ObjectMapper mapper;

    public JsonPointerExtractor(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    /**
     * Extracts objects from the specified path in the JsonNode using JsonPointer.
     * This method avoids the expensive toMap() conversion by working directly on JsonNode.
     *
     * @param path The path to extract (e.g., "field1/field2/field3")
     * @param rootNode The root JsonNode to extract from
     * @return List of objects found at the specified path
     */
    public List<Object> getObjectFromPath(String path, JsonNode rootNode) {
        if (path == null || path.trim().isEmpty()) {
            return List.of(convertJsonNode(rootNode));
        }

        // Split path into components for recursive traversal (similar to JsonExtractor)
        String[] pathComponents = path.trim().split("/");
        return getLeafObjects(pathComponents, 0, rootNode);
    }

    /**
     * Recursively traverses JsonNode structure to find objects at the specified path.
     * Handles both Map-like objects and Arrays similar to JsonExtractor.getLeafObjects().
     */
    private List<Object> getLeafObjects(String[] pathComponents, int level, JsonNode currentNode) {
        if (currentNode == null || currentNode.isNull()) {
            return new ArrayList<>();
        }

        if (currentNode.isArray()) {
            // For arrays, recursively process each element
            List<Object> results = new ArrayList<>();
            for (JsonNode arrayElement : currentNode) {
                results.addAll(getLeafObjects(pathComponents, level, arrayElement));
            }
            return results;
        } else if (currentNode.isObject()) {
            if (level >= pathComponents.length) {
                return List.of(convertJsonNode(currentNode));
            } else {
                String fieldName = pathComponents[level];
                JsonNode childNode = currentNode.get(fieldName);
                if (childNode != null) {
                    return getLeafObjects(pathComponents, level + 1, childNode);
                }
            }
        }
        
        return new ArrayList<>();
    }

    /**
     * Converts a JsonNode to a Java object using ObjectMapper.
     * Only converts the specific node, not the entire document.
     *
     * @param node The JsonNode to convert
     * @return The converted Java object
     */
    private Object convertJsonNode(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        return mapper.convertValue(node, Object.class);
    }
}
