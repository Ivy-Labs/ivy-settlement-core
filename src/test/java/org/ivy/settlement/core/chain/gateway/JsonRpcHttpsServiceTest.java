package org.ivy.settlement.core.chain.gateway;

import io.vertx.core.Vertx;
import org.ivy.settlement.core.chain.gateway.http.JsonRpcConfiguration;
import org.ivy.settlement.core.chain.gateway.http.JsonRpcHttpService;
import org.ivy.settlement.core.chain.gateway.http.health.HealthService;
import org.ivy.settlement.core.chain.gateway.http.jsonrpc.internal.JsonRpcRequestContext;
import org.ivy.settlement.core.chain.gateway.http.jsonrpc.internal.methods.JsonRpcMethod;
import org.ivy.settlement.core.chain.gateway.http.jsonrpc.internal.response.JsonRpcResponse;
import org.ivy.settlement.core.chain.gateway.http.jsonrpc.internal.response.JsonRpcSuccessResponse;
import org.ivy.settlement.core.chain.gateway.http.tls.FileBasedPasswordProvider;
import org.ivy.settlement.core.chain.gateway.http.tls.TlsClientAuthConfiguration;
import org.ivy.settlement.core.chain.gateway.http.tls.TlsConfiguration;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigInteger;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.*;

import static java.net.http.HttpClient.Version.HTTP_1_1;
import static java.net.http.HttpClient.Version.HTTP_2;

/**
 * JsonRpcHttpServiceTest.java description：
 *
 * @Author laiyiyu create on 2024-03-06 10:31:00
 */
public class JsonRpcHttpsServiceTest {

    private static final String CLIENT_VERSION = "TestClientVersion/0.1.0";
    private static final BigInteger NETWORK_ID = BigInteger.valueOf(123);
    private static final List<String> netServices =
            new ArrayList<>(Arrays.asList("jsonrpc", "ws", "p2p", "metrics"));

    private static String baseUrl;
    private final Vertx vertx = Vertx.vertx();
    private Path folder = Path.of(System.getProperty("user.dir") + File.separator + "src" + File.separator + "test" + File.separator + "resource");
    ;

    private HttpClient client;

    private JsonRpcHttpService service;
    private JsonRpcConfiguration configuration;

    private Map<String, JsonRpcMethod> rpcMethods = new HashMap<>();

    private final SelfSignedP12Certificate serverCertificate = SelfSignedP12Certificate.create();

    private final SelfSignedP12Certificate clientCertificate = SelfSignedP12Certificate.create();


    private final FileBasedPasswordProvider fileBasedPasswordProvider = new FileBasedPasswordProvider(createPasswordFile(serverCertificate.getPassword()));


    @Before
    public void before() throws Exception {
//        var sep = File.separator;
//
//
//        String currentPath = System.getProperty("user.dir") + sep + "src" + sep + "test" + sep + "resource";
//        folder = Path.of(currentPath);
        //System.out.println("当前运行时文件路径: " + currentPath);


        client = buildClient();
        //configuration = JsonRpcConfiguration.createDefault();
        rpcMethods.put("net_version", new JsonRpcMethod() {
            @Override
            public String getName() {
                return "net_version";
            }

            @Override
            public JsonRpcResponse response(JsonRpcRequestContext request) {
                return new JsonRpcSuccessResponse(request.getRequest().getId(), "625");
            }
        });

        service = createJsonRpcHttpService(createJsonRpcConfig());
        service.start().join();
        baseUrl = service.url() + "/rpc";
        System.out.println("baseUrl:" + baseUrl);
    }

    @After
    public void after() {
        service.stop().join();
    }

    @Test
    public void reqWithTlsVersion1() throws Exception {
        reqWithTls(HTTP_1_1);
    }

    @Test
    public void reqWithTlsVersion2() throws Exception {
        reqWithTls(HTTP_2);
    }

    public void reqWithTls(HttpClient.Version httpVersion) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl))
                .timeout(Duration.ofMinutes(2))
                .version(httpVersion)
                .header("Content-Type", "application/json;charset=utf-8")
                .POST(HttpRequest.BodyPublishers.ofString(
                        """
                                {"jsonrpc":"2.0","id":"10","method":"net_version"}
                                """))


                .build();
        String body = client.send(request, HttpResponse.BodyHandlers.ofString()).body();
        System.out.println("阻塞请求结果:" + body);
    }


    private JsonRpcHttpService createJsonRpcHttpService(final JsonRpcConfiguration jsonRpcConfig)
            throws Exception {
        return new JsonRpcHttpService(
                vertx,
                null,
                jsonRpcConfig,
                null,
                rpcMethods,
                HealthService.ALWAYS_HEALTHY,
                HealthService.ALWAYS_HEALTHY);
    }

    private JsonRpcConfiguration createJsonRpcConfig() {
        final JsonRpcConfiguration config = JsonRpcConfiguration.createDefault();
        config.setPort(8080);
        config.setHostsAllowlist(Collections.singletonList("*"));
        config.setTlsConfiguration(getRpcHttpTlsConfiguration());
        return config;
    }

    private Optional<TlsConfiguration> getRpcHttpTlsConfiguration() {
        final var knowClientsFile = createTempFile();
        writeToKnowClientFile(clientCertificate.getCommonName(), clientCertificate.getCertificateHexFingerprint(), knowClientsFile);

        final TlsConfiguration tlsConfiguration =
                TlsConfiguration.Builder.aTlsConfiguration()
                        .withKeyStorePath(serverCertificate.getKeyStoreFile())
                        .withKeyStorePasswordSupplier(fileBasedPasswordProvider)
                        .withClientAuthConfiguration(TlsClientAuthConfiguration.Builder.aTlsClientAuthConfiguration().withKnownClientsFile(knowClientsFile).withCaClientsEnabled(true).build())
                        .build();

        return Optional.of(tlsConfiguration);
    }


    private Optional<TlsConfiguration> getRpcHttpTlsConfigurationWithoutKnowClients() {
        final TlsConfiguration tlsConfiguration =
                TlsConfiguration.Builder.aTlsConfiguration()
                        .withKeyStorePath(serverCertificate.getKeyStoreFile())
                        .withKeyStorePasswordSupplier(
                                new FileBasedPasswordProvider(createPasswordFile(serverCertificate.getPassword())))
                        .build();

        return Optional.of(tlsConfiguration);
    }

    private Path createPasswordFile(final char[] password) {
        try {
            return Files.writeString(createTempFile(), new String(password));
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
    }


    private Path createTempFile() {
        try {
//            var path = folder.resolve("newFile");
//            if (Files.exists(path)) {
//                return path;
//            } else {
//                return Files.createFile(path);
//            }
            return Files.createTempFile(folder, "newFile", "");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static void writeToKnowClientFile(final String commonName, final String fingerprint, final Path knowClientFile) {
        try {
            final var knowClientLine = String.format("%s %s", commonName, fingerprint);
            Files.write(knowClientFile, List.of("#Known Clients File", knowClientLine));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }


    private SSLContext getCustomSslContext(final TrustManagerFactory trustManagerFactory, final Optional<KeyManagerFactory> keyManagerFactory) throws NoSuchAlgorithmException, KeyManagementException {
        final var km = keyManagerFactory.map(KeyManagerFactory::getKeyManagers).orElse(null);
        final var tm = trustManagerFactory.getTrustManagers();
        var sslContext = SSLContext.getInstance("TLS");
        sslContext.init(km, tm, new SecureRandom());
        return sslContext;
    }


    private HttpClient buildClient() {
        try {
            var trustManagers = new TrustManager[]{getServerTrustManagerFactory().getTrustManagers()[0]};
            var sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, trustManagers, new SecureRandom());
            return HttpClient.newBuilder()
                    .sslContext(sslContext)
                    .build();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }


    public TrustManagerFactory getServerTrustManagerFactory() {
        try {
            var keyStore = KeyStore.getInstance(serverCertificate.getKeyStoreFile().toFile(), serverCertificate.getPassword());
            var trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            trustManagerFactory.init(keyStore);
            return trustManagerFactory;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private KeyManagerFactory getClientKeyManagerFactory() {
        try {
            var keyStorePwd = clientCertificate.getPassword();
            var keyStore = KeyStore.getInstance(clientCertificate.getKeyStoreFile().toFile(), keyStorePwd);
            var keyManagerFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            keyManagerFactory.init(keyStore, keyStorePwd);
            return keyManagerFactory;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

}
