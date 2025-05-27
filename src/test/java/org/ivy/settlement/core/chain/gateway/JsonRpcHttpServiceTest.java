package org.ivy.settlement.core.chain.gateway;

import com.google.common.collect.Lists;
import io.vertx.core.Vertx;
import org.ivy.settlement.core.chain.gateway.http.JsonRpcConfiguration;
import org.ivy.settlement.core.chain.gateway.http.JsonRpcHttpService;
import org.ivy.settlement.core.chain.gateway.http.health.HealthService;
import org.ivy.settlement.core.chain.gateway.http.jsonrpc.internal.JsonRpcRequestContext;
import org.ivy.settlement.core.chain.gateway.http.jsonrpc.internal.methods.JsonRpcMethod;
import org.ivy.settlement.core.chain.gateway.http.jsonrpc.internal.response.JsonRpcResponse;
import org.ivy.settlement.core.chain.gateway.http.jsonrpc.internal.response.JsonRpcSuccessResponse;
import org.ivy.settlement.core.chain.gateway.method.parameters.MainChainEventParameter;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.math.BigInteger;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static java.util.Collections.singletonList;

/**
 * description:
 * @author carrot
 */
public class JsonRpcHttpServiceTest {


    private static final String CLIENT_VERSION = "TestClientVersion/0.1.0";
    private static final BigInteger NETWORK_ID = BigInteger.valueOf(123);
    private static final List<String> netServices =
            new ArrayList<>(Arrays.asList("jsonrpc", "ws", "p2p", "metrics"));
    private static String baseUrl;
    private final Vertx vertx = Vertx.vertx();
    private Path folder;

    private HttpClient client;

    private JsonRpcHttpService service;
    private JsonRpcConfiguration configuration;

    private Map<String, JsonRpcMethod> rpcMethods = new HashMap<>();

    @Before
    public void before() {
        client = HttpClient.newBuilder()
                .build();
        configuration = JsonRpcConfiguration.createDefault();
        rpcMethods.put("net_version", new JsonRpcMethod() {
            @Override
            public String getName() {
                return "net_version";
            }

            @Override
            public JsonRpcResponse response(JsonRpcRequestContext request) {
                final var param = request.getRequiredParameter(0, MainChainEventParameter.class);
                final var from = request.getRequiredParameter(1, String.class);
                System.out.println("receive param:" + param);
                System.out.println("receive from:" + from);
                return new JsonRpcSuccessResponse(request.getRequest().getId(), "625");
            }
        });
    }

    @After
    public void after() {
        service.stop().join();
    }

    @Test
    public void requestWithNetMethodShouldSucceedWhenNetApiIsEnabled() throws Exception {
        service = createJsonRpcHttpServiceWithRpcApis(configuration);
        var request = HttpRequest.newBuilder()
                .uri(URI.create("http://127.0.0.1:8545/rpc"))
                .timeout(Duration.ofMinutes(2))
                .header("Content-Type", "application/json;charset=utf-8")
                .POST(HttpRequest.BodyPublishers.ofString(
                        "{\"id\": 1, \"method\": \"net_version\", \"params\": [{\"processType\": \"1\", \"slotIndex\": \"1\", \"publicKey\": \"bde09e67207daa157f510742ea77b7896782729c4621a0884fd018a1ee298fbe\", \"consensusVotingPower\": \"4\"}, \"0x1\"]}"
                ))


                .build();
        var body = client.send(request, HttpResponse.BodyHandlers.ofString()).body();
        System.out.println("阻塞请求结果:" + body);
    }


    private JsonRpcHttpService createJsonRpcHttpServiceWithRpcApis(final String... rpcApis)
            throws Exception {
        return createJsonRpcHttpServiceWithRpcApis(createJsonRpcConfigurationWithRpcApis(rpcApis));
    }

    private JsonRpcHttpService createJsonRpcHttpServiceWithRpcApis(final JsonRpcConfiguration config)
            throws Exception {

        final var jsonRpcHttpService =
                new JsonRpcHttpService(
                        vertx,
                        folder,
                        config,
                        null,
                        rpcMethods,
                        HealthService.ALWAYS_HEALTHY,
                        HealthService.ALWAYS_HEALTHY);
        jsonRpcHttpService.start().join();

        baseUrl = jsonRpcHttpService.url() + "/rpc";
        return jsonRpcHttpService;
    }


    private JsonRpcConfiguration createJsonRpcConfiguration() {
        final var config = JsonRpcConfiguration.createDefault();
        config.setEnabled(true);
        return config;
    }

    private JsonRpcConfiguration createJsonRpcConfigurationWithRpcApis(final String... rpcApis) {
        final var config = JsonRpcConfiguration.createDefault();
        config.setCorsAllowedDomains(singletonList("*"));
        config.setPort(0);
        if (rpcApis != null) {
            config.setRpcApis(Lists.newArrayList(rpcApis));
        }
        return config;
    }

    public static void main(String[] args) throws Exception {
        var client = HttpClient.newBuilder().build();
        var request = HttpRequest.newBuilder()
                .uri(URI.create("http://127.0.0.1:9381/rpc"))
                .timeout(Duration.ofMinutes(2))
                .header("Content-Type", "application/json;charset=utf-8")
                .POST(HttpRequest.BodyPublishers.ofString(
                        "{\"id\": 1, \"method\": \"main_chain_cross_event\", \"params\": [{\"processType\": \"1\", \"publicKey\": \"dsfsdf332\", \"consensusVotingPower\": \"4\"}, \"0x1\"]}"
                ))


                .build();
        var body = client.send(request, HttpResponse.BodyHandlers.ofString()).body();
    }

}
