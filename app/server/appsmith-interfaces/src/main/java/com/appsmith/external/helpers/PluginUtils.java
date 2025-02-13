package com.appsmith.external.helpers;

import com.appsmith.external.models.DatasourceConfiguration;
import com.appsmith.external.models.Endpoint;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class PluginUtils {

    /**
     * - Regex to match everything inside double or single quotes, including the quotes.
     * - e.g. Earth "revolves'" '"around"' "the" 'sun' will match:
     * (1) "revolves'"
     * (2) '"around"'
     * (3) "the"
     * (4) 'sun'
     * - ref: https://stackoverflow.com/questions/171480/regex-grabbing-values-between-quotation-marks
     */
    public static String MATCH_QUOTED_WORDS_REGEX = "([\\\"'])(?:(?=(\\\\?))\\2.)*?\\1";

    public static List<String> getColumnsListForJdbcPlugin(ResultSetMetaData metaData) throws SQLException {
        List<String> columnsList = IntStream
                .range(1, metaData.getColumnCount()+1) // JDBC column indexes start from 1
                .mapToObj(i -> {
                    try {
                        return metaData.getColumnName(i);
                    } catch (SQLException exception) {
                        /*
                         * - Need suggestions on alternative ways of handling this exception.
                         */
                        throw new RuntimeException(exception);
                    }
                })
                .collect(Collectors.toList());

        return columnsList;
    }

    public static List<String> getIdenticalColumns(List<String> columnNames) {
        /*
         * - Get frequency of each column name
         */
        Map<String, Long> columnFrequencies = columnNames
                .stream()
                .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));

        /*
         * - Filter only the inputs which have frequency greater than 1
         */
        List<String> identicalColumns = columnFrequencies.entrySet().stream()
                .filter(entry -> entry.getValue() > 1)
                .map(entry -> entry.getKey())
                .collect(Collectors.toList());

        return identicalColumns;
    }

    public static String getActionConfigurationPropertyPath(int index) {
        return "actionConfiguration.pluginSpecifiedTemplates[" + index + "].value";
    }

    public static String getPSParamLabel(int i) {
        return "$" + i;
    }

    public static Boolean validConfigurationPresentInFormData(Map<String, Object> formData, String field) {
        return getValueSafelyFromFormData(formData, field) != null;
    }

    public static Object getValueSafelyFromFormData(Map<String, Object> formData, String field) {
        if (formData == null || formData.isEmpty()) {
            return null;
        }

        // formData exists and is not empty. Continue with fetching the value for the field

        /**
         * For a given fieldname : parent.child.grandchild, in the formData, there would be a key called "parent"
         * which stores the parent map. In the map stored for parent, there would be a key called "child"
         * which stores the child map. In the child map, there would be a key called grandchild which stores the value
         * corresponding to the fieldname `parent.child.grandchild`
         */
        // This field value contains nesting
        if (field.contains(".")) {

            String[] fieldNames = field.split("\\.");

            Map<String, Object> nestedMap = (Map<String, Object>) formData.get(fieldNames[0]);

            String[] trimmedFieldNames = Arrays.copyOfRange(fieldNames, 1, fieldNames.length);
            String nestedFieldName = String.join(".", trimmedFieldNames);

            // Now get the value from the new nested map using trimmed field name (without the parent key)
            return getValueSafelyFromFormData(nestedMap, nestedFieldName);
        } else {
            // This is a top level field. Return the value
            return formData.getOrDefault(field, null);
        }

    }

    public static void setValueSafelyInFormData(Map<String, Object> formData, String field, Object value) {

        // In case the formData has not been initialized before the fxn call, assign a new HashMap to the variable
        if (formData == null) {
            formData = new HashMap<>();
        }

        // This field value contains nesting
        if (field.contains(".")) {

            String[] fieldNames = field.split("\\.");

            // In case the parent key does not exist in the map, create one
            formData.putIfAbsent(fieldNames[0], new HashMap<String, Object>());

            Map<String, Object> nestedMap = (Map<String, Object>) formData.get(fieldNames[0]);

            String[] trimmedFieldNames = Arrays.copyOfRange(fieldNames, 1, fieldNames.length);
            String nestedFieldName = String.join(".", trimmedFieldNames);

            // Now set the value from the new nested map using trimmed field name (without the parent key)
            setValueSafelyInFormData(nestedMap, nestedFieldName, value);
        } else {
            // This is a top level field. Set the value
            formData.put(field, value);
        }
    }

    public static boolean endpointContainsLocalhost(Endpoint endpoint) {
        if (endpoint == null || StringUtils.isEmpty(endpoint.getHost())) {
            return false;
        }

        List<String> localhostUrlIdentifiers = new ArrayList<>();
        localhostUrlIdentifiers.add("localhost");
        localhostUrlIdentifiers.add("host.docker.internal");
        localhostUrlIdentifiers.add("127.0.0.1");

        String host = endpoint.getHost().toLowerCase();
        return localhostUrlIdentifiers.stream()
                .anyMatch(identifier -> host.contains(identifier));
    }

    /**
     * Check if the URL supplied by user is pointing to localhost. If so, then return a hint message.
     *
     * @param datasourceConfiguration
     * @return a set containing a hint message.
     */
    public static Set<String> getHintMessageForLocalhostUrl(DatasourceConfiguration datasourceConfiguration) {
        Set<String> message = new HashSet<>();
        if (datasourceConfiguration != null) {
            boolean usingLocalhostUrl = false;

            if(!StringUtils.isEmpty(datasourceConfiguration.getUrl())) {
                usingLocalhostUrl = datasourceConfiguration.getUrl().contains("localhost");
            }
            else if(!CollectionUtils.isEmpty(datasourceConfiguration.getEndpoints())) {
                usingLocalhostUrl = datasourceConfiguration
                        .getEndpoints()
                        .stream()
                        .anyMatch(endpoint -> endpointContainsLocalhost(endpoint));
            }

            if(usingLocalhostUrl) {
                message.add("You may not be able to access your localhost if Appsmith is running inside a docker " +
                        "container or on the cloud. To enable access to your localhost you may use ngrok to expose " +
                        "your local endpoint to the internet. Please check out Appsmith's documentation to understand more" +
                        ".");
            }
        }

        return message;
    }
}
