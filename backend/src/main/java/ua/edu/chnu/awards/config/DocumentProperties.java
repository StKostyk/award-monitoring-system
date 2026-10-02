package ua.edu.chnu.awards.config;

import java.net.URI;
import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.util.unit.DataSize;

/**
 * Documents attached to awards and the object storage that holds their content.
 *
 * @param bucket           the private bucket of all documents
 * @param maxFileSize      largest accepted file
 * @param maxFilesPerAward most documents one award may have
 * @param sweepAge         how old an object without a document row must be before the sweep removes it
 * @param storage          connection to the S3-compatible storage
 */
@ConfigurationProperties(prefix = "app.documents")
public record DocumentProperties(@DefaultValue("award-documents") String bucket,
                                 @DefaultValue("10MB") DataSize maxFileSize,
                                 @DefaultValue("10") int maxFilesPerAward,
                                 @DefaultValue("24h") Duration sweepAge,
                                 @DefaultValue Storage storage) {

    /**
     * Connection to the S3-compatible storage (MinIO).
     *
     * @param endpoint       base URL of the S3 API
     * @param region         region sent in the request signature
     * @param accessKey      access key
     * @param secretKey      secret key
     * @param connectTimeout how long to wait for a connection
     * @param callTimeout    how long one call may take, retries included
     */
    public record Storage(@DefaultValue("http://localhost:9000") URI endpoint,
                          @DefaultValue("us-east-1") String region,
                          @DefaultValue("minioadmin") String accessKey,
                          @DefaultValue("minioadmin") String secretKey,
                          @DefaultValue("2s") Duration connectTimeout,
                          @DefaultValue("30s") Duration callTimeout) {

        @Override
        public String toString() {
            return "Storage[endpoint=" + endpoint + ", region=" + region + "]";
        }
    }
}
