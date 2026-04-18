package by.losik.lambda;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.ScheduledEvent;
import software.amazon.awssdk.services.ses.SesClient;
import software.amazon.awssdk.services.ses.model.Body;
import software.amazon.awssdk.services.ses.model.Content;
import software.amazon.awssdk.services.ses.model.Destination;
import software.amazon.awssdk.services.ses.model.Message;
import software.amazon.awssdk.services.ses.model.SendEmailRequest;

import java.util.Optional;

public class LambdaHandler implements RequestHandler<ScheduledEvent, String> {

    private static final String FROM_EMAIL = System.getenv("FROM_EMAIL");
    private static final String TO_EMAIL = System.getenv("TO_EMAIL");

    private final SesClient sesClient;

    public LambdaHandler() {
        this.sesClient = SesClient.create();
    }

    @Override
    public String handleRequest(ScheduledEvent event, Context context) {
        LambdaLogger logger = context.getLogger();

        var detailType = event.getDetailType();
        var detail = event.getDetail();

        logger.log("Received event: " + detailType);
        logger.log("Detail: " + detail);

        try {
            String emailBody = buildEmailBody(detailType, String.valueOf(detail));

            SendEmailRequest request = SendEmailRequest.builder()
                    .source(FROM_EMAIL)
                    .destination(Destination.builder()
                            .toAddresses(TO_EMAIL)
                            .build())
                    .message(Message.builder()
                            .subject(Content.builder().data(detailType).build())
                            .body(Body.builder()
                                    .text(Content.builder().data(emailBody).build())
                                    .build())
                            .build())
                    .build();

            sesClient.sendEmail(request);

            logger.log("Email sent successfully");
            return "Success";

        } catch (Exception e) {
            logger.log("Error: " + e.getMessage());
            throw new RuntimeException("Failed to process event", e);
        }
    }

    private String buildEmailBody(String detailType, String detail) {
        return String.format("""
                Event Type: %s
                Time: %s
                Details: %s
                """,
                detailType,
                java.time.Instant.now(),
                Optional.ofNullable(detail).orElse("none")
        );
    }
}