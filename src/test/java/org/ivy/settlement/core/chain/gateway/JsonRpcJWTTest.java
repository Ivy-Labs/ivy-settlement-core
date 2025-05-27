package org.ivy.settlement.core.chain.gateway;

import io.vertx.core.MultiMap;
import io.vertx.core.Vertx;
import io.vertx.core.http.*;
import io.vertx.core.json.Json;
import org.ivy.settlement.core.chain.gateway.http.EngineJsonRpcService;
import org.ivy.settlement.core.chain.gateway.http.JsonRpcConfiguration;
import org.ivy.settlement.core.chain.gateway.http.authentication.AuthenticationService;
import org.ivy.settlement.core.chain.gateway.http.authentication.EngineAuthService;
import org.ivy.settlement.core.chain.gateway.http.health.HealthService;
import org.ivy.settlement.core.chain.gateway.http.jsonrpc.internal.JsonRpcRequest;
import org.ivy.settlement.core.chain.gateway.http.jsonrpc.internal.methods.JsonRpcMethod;
import org.ivy.settlement.core.chain.gateway.http.jsonrpc.internal.response.MutableJsonRpcSuccessResponse;
import org.ivy.settlement.core.chain.gateway.http.jsonrpc.websocket.SubscriptionManager;
import org.ivy.settlement.core.chain.gateway.http.jsonrpc.websocket.methods.WebSocketMethodsFactory;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.*;

/**
 * JsonRpcJWTTest.java description：
 *
 * @Author laiyiyu create on 2024-03-11 10:18:38
 */
public class JsonRpcJWTTest {

    public static final String HOSTNAME = "127.0.0.1";
    private static final int VERTX_AWAIT_TIMEOUT_MILLIS = 10000;
    protected static Vertx vertx;
    private final JsonRpcConfiguration jsonRpcConfiguration =
            JsonRpcConfiguration.createEngineDefault();
    private CountDownLatch testContext;
    private HttpClient httpClient;

    private Path bufferDir;

    private HealthService healthy;
    private ScheduledExecutorService scheduler;
    private Optional<AuthenticationService> jwtAuth;

    private Map<String, JsonRpcMethod> websocketMethods;

    @Before
    public void initServer() {
        var sep = File.separator;
        String fwtFilePath = System.getProperty("user.dir") + sep + "src" + sep + "test" + sep + "resource" + sep + "jwt.hex";
        System.out.println("fwtFilePath:" + fwtFilePath);

        jsonRpcConfiguration.setPort(8080);
        jsonRpcConfiguration.setHostsAllowlist(List.of("*"));
        try {
            jsonRpcConfiguration.setAuthenticationPublicKeyFile(
                    new File(fwtFilePath));
        } catch (Exception e) {
            fail("couldn't load jwt key from jwt.hex in classpath");
        }
        vertx = Vertx.vertx();
        testContext = new CountDownLatch(1);

        websocketMethods =
                new WebSocketMethodsFactory(
                        new SubscriptionManager(), new HashMap<>())
                        .methods();

        bufferDir = null;
        try {
            bufferDir = Files.createTempDirectory("JsonRpcJWTTest").toAbsolutePath();
        } catch (IOException e) {
            fail("can't create tempdir:" + e.getMessage());
        }

        jwtAuth =
                Optional.of(
                        new EngineAuthService(
                                vertx,
                                Optional.ofNullable(jsonRpcConfiguration.getAuthenticationPublicKeyFile()),
                                bufferDir));

        healthy =
                new HealthService(
                        new HealthService.HealthCheck() {
                            @Override
                            public boolean isHealthy(final HealthService.ParamSource paramSource) {
                                return true;
                            }
                        });

        scheduler = Executors.newScheduledThreadPool(1);
    }

    @Test
    public void unauthenticatedWebsocketAllowedWithoutJWTAuth() throws InterruptedException {
        EngineJsonRpcService engineJsonRpcService =
                new EngineJsonRpcService(
                        vertx,
                        jsonRpcConfiguration,
                        null,
                        websocketMethods,
                        Optional.empty(),
                        scheduler,
                        Optional.empty(),
                        healthy,
                        healthy);

        engineJsonRpcService.start().join();

        final InetSocketAddress inetSocketAddress = engineJsonRpcService.socketAddress();
        int listenPort = inetSocketAddress.getPort();

        final HttpClientOptions httpClientOptions =
                new HttpClientOptions().setDefaultHost(HOSTNAME).setDefaultPort(listenPort);

        httpClient = vertx.createHttpClient(httpClientOptions);

        WebSocketConnectOptions wsOpts = new WebSocketConnectOptions();
        wsOpts.setPort(listenPort);
        wsOpts.setHost(HOSTNAME);
        wsOpts.setURI("/");

        httpClient.webSocket(
                wsOpts,
                connected -> {
                    if (connected.failed()) {
                        connected.cause().printStackTrace();
                    }
                    assertTrue(connected.succeeded());
                    WebSocket ws = connected.result();

                    JsonRpcRequest req =
                            new JsonRpcRequest("2.0", "eth_subscribe", List.of("syncing").toArray());
                    ws.frameHandler(
                            resp -> {
                                assertTrue(resp.isText());
                                MutableJsonRpcSuccessResponse messageReply =
                                        Json.decodeValue(resp.textData(), MutableJsonRpcSuccessResponse.class);
                                assertEquals(messageReply.getResult(), "0x1");
                                testContext.countDown();
                            });
                    ws.writeTextMessage(Json.encode(req));
                });

        testContext.await(VERTX_AWAIT_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
        httpClient.close();
        engineJsonRpcService.stop();
    }

    @Test
    public void httpRequestWithDefaultHeaderAndValidJWTIsAccepted() throws InterruptedException {

        EngineJsonRpcService engineJsonRpcService =
                new EngineJsonRpcService(
                        vertx,
                        jsonRpcConfiguration,
                        null,
                        websocketMethods,
                        Optional.empty(),
                        scheduler,
                        jwtAuth,
                        healthy,
                        healthy);

        engineJsonRpcService.start().join();

        final InetSocketAddress inetSocketAddress = engineJsonRpcService.socketAddress();
        int listenPort = inetSocketAddress.getPort();

        final HttpClientOptions httpClientOptions =
                new HttpClientOptions().setDefaultHost(HOSTNAME).setDefaultPort(listenPort);

        httpClient = vertx.createHttpClient(httpClientOptions);

        WebSocketConnectOptions wsOpts = new WebSocketConnectOptions();
        wsOpts.setPort(listenPort);
        wsOpts.setHost(HOSTNAME);
        wsOpts.setURI("/");
        wsOpts.addHeader(
                "Authorization", "Bearer " + ((EngineAuthService) jwtAuth.get()).createToken());
        wsOpts.addHeader(HttpHeaders.HOST, "anything");

        httpClient.webSocket(
                wsOpts,
                connected -> {
                    if (connected.failed()) {
                        connected.cause().printStackTrace();
                    }
                    assertTrue(connected.succeeded());
                    WebSocket ws = connected.result();
                    JsonRpcRequest req =
                            new JsonRpcRequest("1", "eth_subscribe", List.of("syncing").toArray());
                    ws.frameHandler(

                            resp -> {
                                System.out.println("process resp:" + resp);
                                assertTrue(resp.isText());
                                System.out.println(resp.textData());
                                assertFalse(resp.textData().contains("error"));
                                MutableJsonRpcSuccessResponse messageReply =
                                        Json.decodeValue(resp.textData(), MutableJsonRpcSuccessResponse.class);
                                assertEquals(messageReply.getResult(), "0x1");
                                testContext.countDown();
                            });
                    ws.writeTextMessage(Json.encode(req));
                });

        testContext.await(VERTX_AWAIT_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
        httpClient.close();
        engineJsonRpcService.stop();
    }

    @Test
    public void wsRequestFromBadHostAndValidJWTIsDenied() throws InterruptedException {
        var sep = File.separator;
        JsonRpcConfiguration strictHost = JsonRpcConfiguration.createEngineDefault();
        strictHost.setHostsAllowlist(List.of("localhost"));
        strictHost.setPort(0);
        try {
            String fwtFilePath = System.getProperty("user.dir") + sep + "src" + sep + "test" + sep + "resource" + sep + "jwt.hex";

            strictHost.setAuthenticationPublicKeyFile(
                    new File(fwtFilePath));
        } catch (Exception e) {
            fail("didn't parse jwt");
        }

        EngineJsonRpcService engineJsonRpcService =
                        new EngineJsonRpcService(
                                vertx,
                                strictHost,
                                null,
                                websocketMethods,
                                Optional.empty(),
                                scheduler,
                                jwtAuth,
                                healthy,
                                healthy);

        engineJsonRpcService.start().join();

        final InetSocketAddress inetSocketAddress = engineJsonRpcService.socketAddress();
        int listenPort = inetSocketAddress.getPort();

        final HttpClientOptions httpClientOptions =
                new HttpClientOptions().setDefaultHost(HOSTNAME).setDefaultPort(listenPort);

        httpClient = vertx.createHttpClient(httpClientOptions);

        WebSocketConnectOptions wsOpts = new WebSocketConnectOptions();
        wsOpts.setPort(listenPort);
        wsOpts.setHost(HOSTNAME);
        wsOpts.setURI("/");
        wsOpts.addHeader(
                "Authorization", "Bearer " + ((EngineAuthService) jwtAuth.get()).createToken());
        wsOpts.addHeader(HttpHeaders.HOST, "bogushost");

        httpClient.webSocket(
                wsOpts,
                connected -> {
                    if (connected.failed()) {
                        connected.cause().printStackTrace();
                    }
                    assertFalse(connected.succeeded());
                    testContext.countDown();
                });

        testContext.await(VERTX_AWAIT_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
        httpClient.close();
        engineJsonRpcService.stop();
    }

    @Test
    public void httpRequestFromBadHostAndValidJWTIsDenied() throws InterruptedException {

        var sep = File.separator;
        String fwtFilePath = System.getProperty("user.dir") + sep + "src" + sep + "test" + sep + "resource" + sep + "jwt.hex";


        JsonRpcConfiguration strictHost = JsonRpcConfiguration.createEngineDefault();
        strictHost.setHostsAllowlist(List.of("localhost"));
        strictHost.setPort(0);
        try {
            strictHost.setAuthenticationPublicKeyFile(
                    new File(fwtFilePath));
        } catch (Exception e) {
            fail("didn't parse jwt");
        }

        EngineJsonRpcService engineJsonRpcService =

                        new EngineJsonRpcService(
                                vertx,
                                strictHost,
                                null,
                                websocketMethods,
                                Optional.empty(),
                                scheduler,
                                jwtAuth,
                                healthy,
                                healthy);

        engineJsonRpcService.start().join();

        final InetSocketAddress inetSocketAddress = engineJsonRpcService.socketAddress();
        int listenPort = inetSocketAddress.getPort();

        final HttpClientOptions httpClientOptions =
                new HttpClientOptions().setDefaultHost(HOSTNAME).setDefaultPort(listenPort);

        httpClient = vertx.createHttpClient(httpClientOptions);

        MultiMap headers =
                HttpHeaders.set(
                                "Authorization", "Bearer " + ((EngineAuthService) jwtAuth.get()).createToken())
                        .set(HttpHeaders.HOST, "bogushost");

        httpClient.request(
                HttpMethod.GET,
                "/",
                connected -> {
                    if (connected.failed()) {
                        connected.cause().printStackTrace();
                    }
                    HttpClientRequest request = connected.result();
                    request.headers().addAll(headers);
                    request.send(
                            response -> {
                                assertNotEquals(response.result().statusCode(), 500);
                                assertEquals(response.result().statusCode(), 403);
                                testContext.countDown();
                            });
                });

        testContext.await(VERTX_AWAIT_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
        httpClient.close();
        engineJsonRpcService.stop();
    }

    @Test
    public void httpRequestWithDefaultHeaderAndInvalidJWTIsDenied() throws InterruptedException {

        EngineJsonRpcService engineJsonRpcService =
                new EngineJsonRpcService(
                        vertx,
                        jsonRpcConfiguration,
                        null,
                        websocketMethods,
                        Optional.empty(),
                        scheduler,
                        jwtAuth,
                        healthy,
                        healthy);

        engineJsonRpcService.start().join();

        final InetSocketAddress inetSocketAddress = engineJsonRpcService.socketAddress();
        int listenPort = inetSocketAddress.getPort();

        final HttpClientOptions httpClientOptions =
                new HttpClientOptions().setDefaultHost(HOSTNAME).setDefaultPort(listenPort);

        httpClient = vertx.createHttpClient(httpClientOptions);

        WebSocketConnectOptions wsOpts = new WebSocketConnectOptions();
        wsOpts.setPort(listenPort);
        wsOpts.setHost(HOSTNAME);
        wsOpts.setURI("/");
        wsOpts.addHeader("Authorization", "Bearer totallyunparseablenonsense");

        httpClient.webSocket(
                wsOpts,
                connected -> {
                    if (connected.failed()) {
                        connected.cause().printStackTrace();
                    }
                    assertFalse(connected.succeeded());
                    testContext.countDown();
                });

        testContext.await(VERTX_AWAIT_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
        httpClient.close();
        engineJsonRpcService.stop();
    }
}
