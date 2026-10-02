package ua.edu.chnu.awards.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.checksums.RequestChecksumCalculation;
import software.amazon.awssdk.core.checksums.ResponseChecksumValidation;
import software.amazon.awssdk.http.apache.ApacheHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

/**
 * The S3 client of the document storage: path-style requests to MinIO, checksums only where the API requires
 * them.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(DocumentProperties.class)
public class StorageConfig {

    /**
     * The client of the document storage.
     *
     * @param properties endpoint, keys and timeouts
     * @return the client, closed with the context
     */
    @Bean(destroyMethod = "close")
    S3Client documentStorageClient(DocumentProperties properties) {
        DocumentProperties.Storage storage = properties.storage();
        return S3Client.builder()
            .endpointOverride(storage.endpoint())
            .region(Region.of(storage.region()))
            .credentialsProvider(StaticCredentialsProvider.create(
                AwsBasicCredentials.create(storage.accessKey(), storage.secretKey())))
            .forcePathStyle(true)
            .requestChecksumCalculation(RequestChecksumCalculation.WHEN_REQUIRED)
            .responseChecksumValidation(ResponseChecksumValidation.WHEN_REQUIRED)
            .httpClientBuilder(ApacheHttpClient.builder()
                .connectionTimeout(storage.connectTimeout())
                .socketTimeout(storage.callTimeout()))
            .overrideConfiguration(config -> config.apiCallTimeout(storage.callTimeout()))
            .build();
    }
}
