package org.opensearch.dataprepper.plugins.source.jira;


import lombok.extern.slf4j.Slf4j;
import org.opensearch.dataprepper.plugins.source.jira.exception.BadRequestException;
import org.opensearch.dataprepper.plugins.source.jira.utils.Constants;
import org.opensearch.dataprepper.plugins.source.jira.utils.ErrorCodeEnum;
import org.opensearch.dataprepper.plugins.source.jira.utils.ExceptionUtil;
import org.springframework.util.CollectionUtils;

import java.util.List;
import java.util.stream.Collectors;

import static org.opensearch.dataprepper.plugins.source.jira.utils.Constants.BASIC;
import static org.opensearch.dataprepper.plugins.source.jira.utils.Constants.MAX_CHARACTERS_LENGTH;
import static org.opensearch.dataprepper.plugins.source.jira.utils.Constants.OAUTH2;

/**
 * The type Jira configuration.
 */
@Slf4j
public class JiraConfigHelper {

    public static final String ISSUE_STATUS_FILTER = "status";
    public static final String ISSUE_TYPE_FILTER = "issuetype";


    /**
     * Get Issue Status Filter from repository configuration.
     *
     * @return List Issue Status Filter.
     */
    public static List<String> getIssueStatusFilter(JiraSourceConfig repositoryConfiguration) {
        List<String> issueStatusFilter = (List<String>)
                repositoryConfiguration.getAdditionalProperties().get(ISSUE_STATUS_FILTER);
        if (!CollectionUtils.isEmpty(issueStatusFilter)) {
            if (issueStatusFilter.size() > 1000) {
                log.error(ExceptionUtil.getErrorMessage(
                        String.valueOf(ErrorCodeEnum.ERROR_JIRA_ISSUE_STATUS_FILTER_PATTERN),
                        Constants.SOLUTION_FOR_JIRA_ISSUE_STATUS_FILTER));
                throw new BadRequestException(ExceptionUtil.getErrorMessage(
                        String.valueOf(ErrorCodeEnum.ERROR_JIRA_ISSUE_STATUS_FILTER_PATTERN),
                        Constants.SOLUTION_FOR_JIRA_ISSUE_STATUS_FILTER));
            } else {
                List<String> charLengthExceedingPatterns = issueStatusFilter.stream()
                        .filter(pattern -> pattern.length() > MAX_CHARACTERS_LENGTH)
                        .collect(Collectors.toList());
                if (!CollectionUtils.isEmpty(charLengthExceedingPatterns)) {
                    log.error(ExceptionUtil.getErrorMessage(
                            String.valueOf(ErrorCodeEnum.JIRA_ISSUE_STATUS_FILTER_VALUE),
                            Constants.SOLUTION_FOR_JIRA_ISSUE_STATUS_FILTER_OBJECT_VALUE));
                    throw new BadRequestException(ExceptionUtil.getErrorMessage(
                            String.valueOf(ErrorCodeEnum.JIRA_ISSUE_STATUS_FILTER_VALUE),
                            Constants.SOLUTION_FOR_JIRA_ISSUE_STATUS_FILTER_OBJECT_VALUE));
                }
            }
        }
        return issueStatusFilter;
    }

    /**
     * Get Issue Types Filter from repository configuration.
     *
     * @return List Issue Type Filter.
     */
    public static List<String> getIssueTypeFilter(JiraSourceConfig repositoryConfiguration) {
        List<String> issueTypeFilter = (List<String>)
                repositoryConfiguration.getAdditionalProperties().get(ISSUE_TYPE_FILTER);
        if (!CollectionUtils.isEmpty(issueTypeFilter)) {
            if (issueTypeFilter.size() > 1000) {
                log.error(ExceptionUtil.getErrorMessage(
                        String.valueOf(ErrorCodeEnum.ERROR_JIRA_ISSUE_TYPE_FILTER),
                        Constants.SOLUTION_FOR_JIRA_ISSUE_TYPE_FILTER));
                throw new BadRequestException(ExceptionUtil.getErrorMessage(
                        String.valueOf(ErrorCodeEnum.ERROR_JIRA_ISSUE_TYPE_FILTER),
                        Constants.SOLUTION_FOR_JIRA_ISSUE_TYPE_FILTER));
            } else {
                List<String> charLengthExceedingPatterns = issueTypeFilter.stream()
                        .filter(pattern -> pattern.length() > MAX_CHARACTERS_LENGTH)
                        .collect(Collectors.toList());
                if (!CollectionUtils.isEmpty(charLengthExceedingPatterns)) {
                    log.error(ExceptionUtil.getErrorMessage(
                            String.valueOf(ErrorCodeEnum.JIRA_ISSUE_TYPE_FILTER_VALUE),
                            Constants.SOLUTION_FOR_JIRA_ISSUE_TYPE_FILTER_OBJECT_VALUE));
                    throw new BadRequestException(ExceptionUtil.getErrorMessage(
                            String.valueOf(ErrorCodeEnum.JIRA_ISSUE_TYPE_FILTER_VALUE),
                            Constants.SOLUTION_FOR_JIRA_ISSUE_TYPE_FILTER_OBJECT_VALUE));
                }
            }
        }
        return issueTypeFilter;
    }

    /**
     * Get Project Filter Types from repository configuratio
     * public static final String ST = "status";n.
     *
     * @return List Project Filter.
     */
    public static List<String> getProjectKeyFilter(JiraSourceConfig repositoryConfiguration) {
        List<String> projectKeyFilter = repositoryConfiguration.getProject();
        if (!CollectionUtils.isEmpty(projectKeyFilter)) {
            if (projectKeyFilter.size() > 1000) {
                log.error(ExceptionUtil.getErrorMessage(
                        String.valueOf(ErrorCodeEnum.ERROR_JIRA_PROJECT_KEY_FILTER),
                        Constants.SOLUTION_FOR_JIRA_PROJECT_KEY_FILTER));
                throw new BadRequestException(ExceptionUtil.getErrorMessage(
                        String.valueOf(ErrorCodeEnum.ERROR_JIRA_PROJECT_KEY_FILTER),
                        Constants.SOLUTION_FOR_JIRA_PROJECT_KEY_FILTER));
            } else {
                List<String> charLengthExceedingPatterns = projectKeyFilter.stream()
                        .filter(pattern -> pattern.length() > MAX_CHARACTERS_LENGTH)
                        .collect(Collectors.toList());
                if (!CollectionUtils.isEmpty(charLengthExceedingPatterns)) {
                    log.error(ExceptionUtil.getErrorMessage(
                            String.valueOf(ErrorCodeEnum.JIRA_PROJECT_KEY_FILTER_VALUE),
                            Constants.SOLUTION_FOR_JIRA_PROJECT_KEY_FILTER_OBJECT_VALUE));
                    throw new BadRequestException(ExceptionUtil.getErrorMessage(
                            String.valueOf(ErrorCodeEnum.JIRA_PROJECT_KEY_FILTER_VALUE),
                            Constants.SOLUTION_FOR_JIRA_PROJECT_KEY_FILTER_OBJECT_VALUE));
                }
            }
        }
        return projectKeyFilter;
    }


    public static boolean validateConfig(JiraSourceConfig config) {
        if (config.getAccountUrl() == null) {
            throw new RuntimeException("Account URL is missing.");
        }
        //At least one of the AuthType should be present
        if (config.getAuthType() == null) {
            throw new RuntimeException("Authentication Type is missing.");
        }
        String authType = config.getAuthType();
        if (!OAUTH2.equals(authType) && !BASIC.equals(authType)) {
            throw new RuntimeException("Invalid AuthType is given");
        }

        if (BASIC.equals(authType)) {
            if (config.getJiraId() == null || config.getJiraCredential() == null) {
                throw new RuntimeException("Jira ID or Credential are required for Basic AuthType");
            }
        }

        if (OAUTH2.equals(authType)) {
            if (config.getAccessToken() == null || config.getRefreshToken() == null) {
                throw new RuntimeException("Access Token or Refresh Token are required for OAuth2 AuthType");
            }
        }
        return true;
    }
}