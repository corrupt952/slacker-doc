package net.khasegawa.stash.slacker.hooks;

import com.atlassian.event.api.EventListener;
import com.atlassian.stash.event.pull.*;
import com.atlassian.stash.pull.PullRequestAction;
import com.atlassian.stash.repository.Repository;
import com.atlassian.stash.server.ApplicationPropertiesService;
import com.atlassian.stash.user.StashUser;
import com.google.gson.Gson;
import net.khasegawa.stash.slacker.activeobjects.RepositoryConfiguration;
import net.khasegawa.stash.slacker.configurations.ConfigurationService;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpResponse;
import org.apache.http.client.fluent.Form;
import org.apache.http.client.fluent.Request;
import org.apache.log4j.Logger;

import java.io.IOException;
import java.nio.charset.Charset;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

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

        RepositoryConfiguration configuration = null;
        try {
            configuration = configurationService.getRepositoryConfiguration(repo.getId());
        } catch (SQLException e) {
            logger.error(e.getMessage());
            return;
        }
        if (configuration == null) {
            // nothing
            return;
        }
        if (StringUtils.isBlank(configuration.getHookURL())) {
            logger.warn("Slack hook url is blank.");
            return;
        }

        if (StringUtils.isNotBlank(configuration.getChannel())) {
            payload.channel = String.format("%s", configuration.getChannel());
        }

        if (action == PullRequestAction.OPENED) {
            if (!configuration.getNotifyPRCreated()) return;

            String title = event.getPullRequest().getTitle();
            Attachment attachment = new Attachment();

            attachment.pretext = String.format("%s さんが %s に<%s|PR %d>をオープンしました。", username, repoName, url, id);
            attachment.fallback = String.format("%s さんが %s にPR %dをオープンしました。 - %s - %s", username, repoName, id, url, title);
            attachment.title = event.getPullRequest().getTitle();
            attachment.title_link = url;
            attachment.color = "#36a64f";
            attachment.text = event.getPullRequest().getDescription();
            payload.attachments.add(attachment);
        } else if (action == PullRequestAction.REOPENED) {
            if (!configuration.getNotifyPRCreated()) return;

            payload.text = String.format("%s さんが %s の<%s|PR %d>を再オープンしました。", username, repoName, url, id);
        } else if (action == PullRequestAction.MERGED) {
            if (!configuration.getNotifyPRMerged()) return;

            payload.text = String.format("%s さんが %s の<%s|PR %d>をマージしました。", username, repoName, url, id);
        } else if (action == PullRequestAction.DECLINED) {
            if (!configuration.getNotifyPRDeclined()) return;

            payload.text = String.format("%s さんが %s の<%s|PR %d>を却下しました。", username, repoName, url, id);
        } else if (action == PullRequestAction.UPDATED) {
            if (!configuration.getNotifyPRUpdated()) return;

            payload.text = String.format("%s さんが %s の<%s|PR %d>を更新しました。", username, repoName, url, id);
        } else if(action == PullRequestAction.COMMENTED) {
            if (!configuration.getNotifyPRCommented()) return;
            if (StringUtils.isBlank(configuration.getUserJSON())) return;

            PullRequestCommentEvent commentEvent = (PullRequestCommentEvent) event;
            StashUser author = null;
            StashUser user = null;
            if (event instanceof PullRequestCommentAddedEvent) {
                author = commentEvent.getPullRequest().getAuthor().getUser();
                user = commentEvent.getUser();
            } else if (event instanceof PullRequestCommentRepliedEvent) {
                author = commentEvent.getParent().getAuthor();
                user = commentEvent.getUser();
            }

            Map<String, String> userMap = new Gson().fromJson(configuration.getUserJSON(), HashMap.class);
            if (author == null || !userMap.containsKey(author.getName()) ||
                    user == null || !userMap.containsKey(user.getName())) return;
            String commentUrl = String.format("%s?commentId=%d", url, commentEvent.getComment().getId());

            payload.channel = String.format("@%s", userMap.get(author.getName()));
            payload.username = userMap.get(user.getName());
            payload.text = String.format("%s さんから %s の<%s|PR %d>に<%s|コメント>がありました。",
                                         username, repo.getName(), url, id, commentUrl);
        } else return;

        try {
            Gson gson = new Gson();
            Form form = Form.form().add("payload", gson.toJson(payload));
            HttpResponse response = Request
                    .Post(configuration.getHookURL())
                    .bodyForm(form.build(), Charset.forName("UTF-8"))
                    .execute()
                    .returnResponse();
            logger.info(response.getStatusLine());
        } catch (IOException e) {
            logger.error(e.getMessage());
        }
    }
}