package net.khasegawa.stash.slacker.hooks;

import com.atlassian.bitbucket.user.ApplicationUser;
import com.atlassian.event.api.EventListener;
import com.atlassian.bitbucket.event.pull.*;
import com.atlassian.bitbucket.pull.PullRequestAction;
import com.atlassian.bitbucket.repository.Repository;
import com.atlassian.bitbucket.server.ApplicationPropertiesService;
import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import net.khasegawa.stash.slacker.activeobjects.ProjectConfiguration;
import net.khasegawa.stash.slacker.activeobjects.RepositoryConfiguration;
import net.khasegawa.stash.slacker.configurations.ConfigurationService;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpResponse;
import org.apache.http.client.fluent.Form;
import org.apache.http.client.fluent.Request;
import org.apache.log4j.Logger;

import java.io.IOException;
import java.nio.charset.Charset;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Created by Kazuki Hasegawa on 15/02/04.
 *
 * @author Kazuki Hasegawa
 */
public class PullRequestListener {
    private static final Logger logger = Logger.getLogger(PullRequestListener.class);

    private final ApplicationPropertiesService propertiesService;
    private final ConfigurationService configurationService;

    public PullRequestListener(ApplicationPropertiesService propertiesService,
                               ConfigurationService configurationService) {
        this.propertiesService = propertiesService;
        this.configurationService = configurationService;
    }

    @EventListener
    public void listenPullRequestOpenedEvent(PullRequestOpenedEvent event) {
        notifySlack(event);
    }

    @EventListener
    public void listenPullRequestReopenedEvent(PullRequestReopenedEvent event) {
        notifySlack(event);
    }

    @EventListener
    public void listenPullRequestMergedEvent(PullRequestMergedEvent event) {
        notifySlack(event);
    }

    @EventListener
    public void listenPullRequestDeclinedEvent(PullRequestDeclinedEvent event) {
        notifySlack(event);
    }

    @EventListener
    public void listenPullRequestUpdatedEvent(PullRequestUpdatedEvent event) {
        notifySlack(event);
    }

    @EventListener
    public void listenPullRequestCommentAddedEvent(PullRequestCommentAddedEvent event) {
        notifySlack(event);
    }

    @EventListener
    public void listenPullRequestCommentRepliedEvent(PullRequestCommentRepliedEvent event) {
        notifySlack(event);
    }

    @EventListener
    public void listenPullRequestRescopedEvent(PullRequestRescopedEvent event) {
        notifySlack(event);
    }

    public void notifySlack(PullRequestEvent event) {
        String username = event.getUser().getDisplayName();
        String repoName = event.getPullRequest().getToRef().getRepository().getName();
        Long id = event.getPullRequest().getId();
        Repository repo = event.getPullRequest().getToRef().getRepository();
        PullRequestAction action = event.getAction();
        Payload payload = new Payload();
        String url = String.format("%s/projects/%s/repos/%s/pull-requests/%d/overview",
                                   propertiesService.getBaseUrl(), repo.getProject().getKey(), repo.getSlug(),
                                   event.getPullRequest().getId());

        if (id == null) return;

        NotifyConfiguration configuration = getConfiguration(repo);
        if (configuration == null) return;

        if (StringUtils.isBlank(StringUtils.defaultString(configuration.hookURL))) {
            logger.warn("Slack hook url is blank.");
            return;
        }
        if (configuration.ignoreWIP &&
                Pattern.compile("^\\[?WIP\\]?").matcher(event.getPullRequest().getTitle()).find()) return;
        if (configuration.ignoreNotCrossRepository && !event.getPullRequest().isCrossRepository()) return;

        if (StringUtils.isNotBlank(configuration.channel)) {
            payload.channel = String.format("%s", configuration.channel);
        }
        if (action == PullRequestAction.OPENED) {
            if (!configuration.notifyPROpened) return;

            String title = event.getPullRequest().getTitle();
            Attachment attachment = new Attachment();

            attachment.pretext = String.format("%s opened PullRequest <%s|#%d> on %s", username, url, id, repoName);
            attachment.fallback = String.format("%s opened PullRequest #%d on %s - %s - %s", username, id, repoName, url, title);
            attachment.title = event.getPullRequest().getTitle();
            attachment.title_link = url;
            attachment.color = "#36a64f";
            attachment.text = event.getPullRequest().getDescription();
            payload.attachments.add(attachment);
        } else if (action == PullRequestAction.REOPENED) {
            if (!configuration.notifyPRReopened) return;

            payload.text = String.format("%s reopened PullRequest <%s|#%d> on %s", username, url, id, repoName);
        } else if (action == PullRequestAction.MERGED) {
            if (!configuration.notifyPRMerged) return;

            payload.text = String.format("%s merged PullRequest <%s|#%d> on %s", username, url, id, repoName);
        } else if (action == PullRequestAction.DECLINED) {
            if (!configuration.notifyPRDeclined) return;

            payload.text = String.format("%s declined PullRequest <%s|#%d> on %s", username, url, id, repoName);
        } else if (action == PullRequestAction.UPDATED) {
            if (!configuration.notifyPRUpdated) return;

            PullRequestUpdatedEvent pullRequestUpdatedEvent = (PullRequestUpdatedEvent) event;

            Attachment attachment = new Attachment();

            attachment.pretext = String.format("%s updated PullRequest <%s|#%d> on %s", username, url, id, repoName);
            attachment.fallback = String.format("%s updated PullRequest <%s|#%d> on %s", username, url, id, repoName);

            List<Field> fields = new ArrayList<Field>();

            Field previousTitle = new Field();
            previousTitle.title = "Previous Title";
            previousTitle.value = pullRequestUpdatedEvent.getPreviousTitle();
            previousTitle.isShort = true;
            fields.add(previousTitle);

            Field newTitle = new Field();
            newTitle.title = "New Title";
            newTitle.value = pullRequestUpdatedEvent.getPullRequest().getTitle();
            newTitle.isShort = true;
            fields.add(newTitle);

            Field previousDescription = new Field();
            previousDescription.title = "Previous Description";
            previousDescription.value = pullRequestUpdatedEvent.getPreviousDescription();
            previousDescription.isShort = true;
            fields.add(previousDescription);

            Field newDescription = new Field();
            newDescription.title = "New Description";
            newDescription.value = pullRequestUpdatedEvent.getPullRequest().getDescription();
            newDescription.isShort = true;
            fields.add(newDescription);

            if (pullRequestUpdatedEvent.getPreviousToBranch() != null) {
                Field previousToBranch = new Field();
                previousToBranch.title = "Previous To Branch";
                previousToBranch.value = pullRequestUpdatedEvent.getPreviousToBranch().toString();
                previousToBranch.isShort = true;
                fields.add(previousToBranch);

                Field newToBranch = new Field();
                newToBranch.title = "New To Branch";
                newToBranch.value = pullRequestUpdatedEvent.getPullRequest().getToRef().toString();
                newToBranch.isShort = true;
                fields.add(newToBranch);
            }

            attachment.fields = fields;

            attachment.title = event.getPullRequest().getTitle();
            attachment.title_link = url;
            attachment.color = "#36a64f";

            payload.attachments.add(attachment);
        } else if (action == PullRequestAction.RESCOPED) {
            if (!configuration.notifyPRRescoped) return;

            payload.text = String.format("%s rescoped PullRequest <%s|#%s> on %s", username, url, id, repoName);
        } else if(action == PullRequestAction.COMMENTED) {
            if (!configuration.notifyPRCommented) return;

            Map<String, String> userMap = configuration.userMap;
            if (userMap.isEmpty()) return;

            PullRequestCommentEvent commentEvent = (PullRequestCommentEvent) event;
            ApplicationUser author = null;
            ApplicationUser user = null;
            if (event instanceof PullRequestCommentAddedEvent) {
                author = commentEvent.getPullRequest().getAuthor().getUser();
                user = commentEvent.getUser();
            } else if (event instanceof PullRequestCommentRepliedEvent) {
                author = commentEvent.getParent().getAuthor();
                user = commentEvent.getUser();
            }

            if (author == null || !userMap.containsKey(author.getName()) ||
                    user == null || !userMap.containsKey(user.getName()) || user.getId() == author.getId()) {
                if (author == null) logger.warn("Can't get author.");
                if (user == null) logger.warn("Can't get current user.");
                if (!userMap.containsKey(author.getName())) logger.warn("Can't find " + author.getName() + " in userMapJSON.");
                if (!userMap.containsKey(user.getName())) logger.warn("Can't find " + user.getName() + " in userMapJSON.");
                return;
            }

            String commentUrl = String.format("%s?commentId=%d", url, commentEvent.getComment().getId());

            String text = String.format("%s commented to PullRequest <%s|#%d> on %s: <%s|Show>", username, url, id, repoName, commentUrl);
            Attachment attachment = new Attachment();
            attachment.pretext = text;
            attachment.fallback = text;
            attachment.color = "#447dff";
            attachment.text = commentEvent.getComment().getText();
            payload.attachments.add(attachment);
            payload.channel = String.format("@%s", userMap.get(author.getName()));
            payload.username = userMap.get(user.getName());
        } else return;

        try {
            Gson gson = new Gson();
            Form form = Form.form().add("payload", gson.toJson(payload));
            HttpResponse response = Request
                    .Post(configuration.hookURL)
                    .bodyForm(form.build(), Charset.forName("UTF-8"))
                    .execute()
                    .returnResponse();
            logger.info(response.getStatusLine());
        } catch (IOException e) {
            logger.error(e.getMessage());
        }
    }

    private NotifyConfiguration getConfiguration(Repository repository) {
        ProjectConfiguration projectConfiguration = null;
        RepositoryConfiguration repositoryConfiguration = null;

        NotifyConfiguration configuration = new NotifyConfiguration();

        try {
            if (configurationService.existsRepositoryConfiguration(repository.getId())) {
                projectConfiguration = configurationService.getProjectConfiguration(repository.getProject().getId());
                repositoryConfiguration = configurationService.getRepositoryConfiguration(repository.getId());

                configuration.hookURL = StringUtils.defaultIfBlank(repositoryConfiguration.getHookURL(), projectConfiguration.getHookURL());
                configuration.channel = StringUtils.defaultIfBlank(repositoryConfiguration.getChannel(), projectConfiguration.getChannel());
                configuration.notifyPROpened = BooleanUtils.toBooleanDefaultIfNull(repositoryConfiguration.getNotifyPROpened(), projectConfiguration.getNotifyPROpened());
                configuration.notifyPRReopened = BooleanUtils.toBooleanDefaultIfNull(repositoryConfiguration.getNotifyPRReopened(), projectConfiguration.getNotifyPRReopened());
                configuration.notifyPRRescoped = BooleanUtils.toBooleanDefaultIfNull(repositoryConfiguration.getNotifyPRRescoped(), projectConfiguration.getNotifyPRRescoped());
                configuration.notifyPRUpdated = BooleanUtils.toBooleanDefaultIfNull(repositoryConfiguration.getNotifyPRUpdated(), projectConfiguration.getNotifyPRUpdated());
                configuration.notifyPRMerged = BooleanUtils.toBooleanDefaultIfNull(repositoryConfiguration.getNotifyPRMerged(), projectConfiguration.getNotifyPRMerged());
                configuration.notifyPRDeclined = BooleanUtils.toBooleanDefaultIfNull(repositoryConfiguration.getNotifyPRDeclined(), projectConfiguration.getNotifyPRDeclined());
                configuration.notifyPRCommented = BooleanUtils.toBooleanDefaultIfNull(repositoryConfiguration.getNotifyPRCommented(), projectConfiguration.getNotifyPRCommented());
                configuration.ignoreWIP = BooleanUtils.toBooleanDefaultIfNull(repositoryConfiguration.getIgnoreWIP(), projectConfiguration.getIgnoreWIP());
                configuration.ignoreNotCrossRepository = BooleanUtils.toBooleanDefaultIfNull(repositoryConfiguration.getIgnoreNotCrossRepository(), projectConfiguration.getIgnoreNotCrossRepository());
                configuration.userMap = new HashMap<String, String>();
                configuration.setUserMapJSON(projectConfiguration.getUserMapJSON());
                configuration.setUserMapJSON(repositoryConfiguration.getUserMapJSON());
            } else if (configurationService.existsProjectConfiguration(repository.getProject().getId())) {
                projectConfiguration = configurationService.getProjectConfiguration(repository.getProject().getId());

                configuration.hookURL = projectConfiguration.getHookURL();
                configuration.channel = projectConfiguration.getChannel();
                configuration.notifyPROpened = projectConfiguration.getNotifyPROpened();
                configuration.notifyPRReopened = projectConfiguration.getNotifyPRReopened();
                configuration.notifyPRRescoped = projectConfiguration.getNotifyPRRescoped();
                configuration.notifyPRUpdated = projectConfiguration.getNotifyPRUpdated();
                configuration.notifyPRMerged = projectConfiguration.getNotifyPRMerged();
                configuration.notifyPRDeclined = projectConfiguration.getNotifyPRDeclined();
                configuration.notifyPRCommented = projectConfiguration.getNotifyPRCommented();
                configuration.ignoreWIP = projectConfiguration.getIgnoreWIP();
                configuration.ignoreNotCrossRepository = projectConfiguration.getIgnoreNotCrossRepository();
                configuration.userMap = new HashMap<String, String>();
                configuration.setUserMapJSON(projectConfiguration.getUserMapJSON());
            }
        } catch (SQLException e) {
            logger.error(e.getMessage());
            return null;
        }

        return configuration;
    }

    class NotifyConfiguration {
        public String hookURL;
        public String channel;
        public Boolean notifyPROpened;
        public Boolean notifyPRReopened;
        public Boolean notifyPRRescoped;
        public Boolean notifyPRUpdated;
        public Boolean notifyPRMerged;
        public Boolean notifyPRDeclined;
        public Boolean notifyPRCommented;
        public Boolean ignoreWIP;
        public Boolean ignoreNotCrossRepository;
        public Map<String, String> userMap;

        private void setUserMapJSON(String userMapJSON) {
            try {
                if (StringUtils.isNotBlank(userMapJSON)) {
                    Map<String, String> userMap = new Gson().fromJson(userMapJSON, HashMap.class);
                    for (Map.Entry<String, String> user : userMap.entrySet()) {
                        this.userMap.put(user.getKey(), user.getValue());
                    }
                }
            } catch (JsonSyntaxException e) {
                logger.warn("UserMapJSON is invalid! " + StringUtils.defaultString(userMapJSON));
            }
        }
    }
}
