package org.dataconservancy.pass.client;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.dataconservancy.pass.client.util.SubmissionStatusCalculator;
import org.dataconservancy.pass.model.Deposit;
import org.dataconservancy.pass.model.PassEntity;
import org.dataconservancy.pass.model.PassEntityType;
import org.dataconservancy.pass.model.RepositoryCopy;
import org.dataconservancy.pass.model.Submission;
import org.dataconservancy.pass.model.Submission.SubmissionStatus;
import org.dataconservancy.pass.model.SubmissionEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A service for calculating and updating the `Submission.submissionStatus` value based on data
 * related to the Submission. By default, there is a division of responsibility in calculating
 * this status, with pre-submission statuses being managed by the UI, and post-Submission statuses
 * being managed by back-end services. For this reason, pre-submission statuses will only be changed
 * if the starting value is null, or overrideUIStatus is true.
 *
 * @author Karen Hanson
 */
public class SubmissionStatusService {

    private static final Logger LOG = LoggerFactory.getLogger(SubmissionStatusService.class);

    private static final String SUBMISSION_MAP_KEY = "submission";
    private static final String PUBLICATION_MAP_KEY = "publication";

    private PassClient client;

    /**
     * Initiate service
     */
    public SubmissionStatusService() {
        this.client = PassClientFactory.getPassClient();
    }

    /**
     * Supports setting a specific client.
     *
     * @param client PASS client
     */
    public SubmissionStatusService(PassClient client) {
        if (client == null) {
            throw new IllegalArgumentException("PassClient cannot be null");
        }
        this.client = client;
    }

    /**
     * Calculates the appropriate {@link SubmissionStatus} for the {@code Submission.id} provided.
     * This is based on the status of associated {@link Deposit}s and {@link RepositoryCopy}s for
     * {@code submitted} records, and the existing status (if any) and {@link SubmissionEvent}s for unsubmitted records.
     *
     * @param submissionId Submission URI
     * @return calculated submission status.
     */
    public SubmissionStatus calculateSubmissionStatus(URI submissionId) {
        Submission submission = loadSubmission(submissionId);
        return calculateSubmissionStatus(submission);
    }

    /**
     * Calculates the appropriate {@link SubmissionStatus} for the {@link Submission} provided.
     * This is based on the status of associated {@link Deposit}s and {@link RepositoryCopy}s for
     * {@code submitted} records, and the existing status (if any) and {@link SubmissionEvent}s for unsubmitted records.
     *
     * @param submission The submission
     * @return Calculated submission status
     */
    public SubmissionStatus calculateSubmissionStatus(Submission submission) {
        if (submission == null) {
            throw new IllegalArgumentException("submission cannot be null");
        }
        if (submission.getId() == null) {
            throw new IllegalArgumentException(
                "No status could be calculated for the Submission as it does not have a `Submission.id`.");
        }

        URI submissionId = submission.getId();
        boolean submitted = submission.getSubmitted();

        SubmissionStatus fromStatus = submission.getSubmissionStatus();
        SubmissionStatus toStatus;

        Collection<URI> submissionLinks = retrieveLinks(submissionId, SUBMISSION_MAP_KEY);

        if (!submitted) {

            List<SubmissionEvent> submissionEvents = getConnectedRecords(submissionLinks,
                                                                         PassEntityType.SUBMISSION_EVENT,
                                                                         SubmissionEvent.class);

            // Calculate the pre-submission status, defaulting to the existing status if one cannot be determined
            // from the submission events.
            toStatus = SubmissionStatusCalculator.calculatePreSubmissionStatus(submissionEvents,
                                                                               submission.getSubmissionStatus());

        } else {

            List<Deposit> deposits = getConnectedRecords(submissionLinks, PassEntityType.DEPOSIT, Deposit.class);

            Collection<URI> publicationLinks = retrieveLinks(submission.getPublication(), PUBLICATION_MAP_KEY);
            List<RepositoryCopy> repositoryCopies = getConnectedRecords(publicationLinks,
                                                                        PassEntityType.REPOSITORY_COPY,
                                                                        RepositoryCopy.class);

            toStatus = SubmissionStatusCalculator.calculatePostSubmissionStatus(submission.getRepositories(), deposits,
                                                                                repositoryCopies);

        }

        try {
            SubmissionStatusCalculator.validateStatusChange(submitted, fromStatus, toStatus);
        } catch (RuntimeException ex) {
            String msg = String.format("Cannot change status from %s to %s on Submission %s. "
                                       + "The following explaination was provided: %s", fromStatus, toStatus,
                                       submissionId, ex.getMessage());
            throw new RuntimeException(msg);
        }

        return toStatus;

    }

    /**
     * Calculates the appropriate {@link SubmissionStatus} for the {@code Submission.id} provided.
     * <p>
     * This is based on the status of associated {@link Deposit}s and {@link RepositoryCopy}s for
     * {@code submitted} records, and {@link SubmissionEvent}s for unsubmitted records then updates
     * the status as appropriate.
     * </p>
     * <p>
     * The UI will typically have responsibility for updating the {@code submissionStatus} before
     * the {@link Submission} is submitted. Therefore, by default this service will not replace
     * the existing status of an unsubmitted record unless the starting value was null (i.e. it has not
     * been populated yet). To override this constraint, and replace the value anyway, use the method
     * {@code calculateAndUpdateSubmissionStatus(boolean overrideUIStatus)} and supply a parameter of {@code true}
     * </p>
     *
     * @param submissionId Submission URI
     * @return Calculated submission status
     */
    public SubmissionStatus calculateAndUpdateSubmissionStatus(URI submissionId) {
        return calculateAndUpdateSubmissionStatus(submissionId, false);
    }

    /**
     * Calculates the appropriate {@link SubmissionStatus} for the {@link Submission} provided.
     *
     * <p>
     * This is based on the status of associated {@link Deposit}s and {@link RepositoryCopy}s for
     * {@code submitted} records, and {@link SubmissionEvent}s for unsubmitted records then updates
     * the status as appropriate.
     * </p>
     * <p>
     * The UI will typically have responsibility for updating the {@code submissionStatus} before
     * the {@link Submission} is submitted. Therefore, by default this service will not replace
     * the existing status of an unsubmitted record unless the starting value was null (i.e. it has not
     * been populated yet). To override this constraint, set the {@code overrideUIStatus} parameter to
     * {@code true}
     * </p>
     *
     * @param submissionId     Submission URI
     * @param overrideUIStatus - {@code true} will override the current pre-submission status on the
     *                         {@code Submission} record, regardless of whether it was set by the UI.
     *                         {@code false} will not replace the current submission value, and favor the value set
     *                         by the UI
     * @return calculated submission status.
     */
    public SubmissionStatus calculateAndUpdateSubmissionStatus(URI submissionId, boolean overrideUIStatus) {

        Submission submission = loadSubmission(submissionId);

        SubmissionStatus fromStatus = submission.getSubmissionStatus();
        SubmissionStatus toStatus = calculateSubmissionStatus(submission);

        if (fromStatus == null || !fromStatus.equals(toStatus)) {

            //Applies special rule - this service should not overwrite what the UI has set the status to
            //unless the original status was null or this service has been specifically configured to do so
            //by setting overrideUIStatus to true.
            if (!overrideUIStatus && !submission.getSubmitted() && fromStatus != null) {
                LOG.info("Status of Submission {} did not change because pre-submission UI statuses are protected. "
                         + "The current status will stay as `{}`", submission.getId(), fromStatus);
                return fromStatus;
            }

            submission.setSubmissionStatus(toStatus);
            LOG.info("Updating status of Submission {} from `{}` to `{}`", submission.getId(), fromStatus, toStatus);
            client.updateResource(submission);

        } else {
            LOG.debug("Status of Submission {} did not change. The current status is `{}`", submission.getId(),
                      fromStatus);
        }

        return toStatus;
    }

    /**
     * Load submission based on URI
     *
     * @param submissionId Submission URI
     * @return The submission
     */
    private Submission loadSubmission(URI submissionId) {
        if (submissionId == null) {
            throw new IllegalArgumentException("submissionId cannot be null");
        }
        Submission submission = null;
        try {
            submission = client.readResource(submissionId, Submission.class);
        } catch (Exception ex) {
            String msg = String.format("Failed to retrieve Submission with ID %s from the database", submissionId);
            throw new RuntimeException(msg);
        }
        return submission;
    }

    /**
     * Retrieve incoming links for resource, filtered by a map key.
     *
     * @param uri
     * @param mapKey
     * @return Incoming links
     */
    private Collection<URI> retrieveLinks(URI uri, String mapKey) {
        Collection<URI> links = new HashSet<>();
        if (uri == null || mapKey == null) {
            return links;
        }
        Map<String, Collection<URI>> linksMap = client.getIncoming(uri);
        if (linksMap.containsKey(mapKey)) {
            links = linksMap.get(mapKey);
        }
        return links;
    }

    /**
     * Filter links list by entity type required and read in resources from database
     *
     * @param links
     * @param entityType
     * @param modelClass
     * @return list of connected resources.
     */
    private <T extends PassEntity> List<T> getConnectedRecords(Collection<URI> links, PassEntityType entityType,
                                                               Class<T> modelClass) {
        if (links == null || entityType == null || modelClass == null) {
            return new ArrayList<T>();
        }
        return links.stream()
                    .filter(link -> link.toString().contains(entityType.getPlural()))
                    .map(res -> client.readResource(res, modelClass))
                    .collect(Collectors.toList());
    }

}
