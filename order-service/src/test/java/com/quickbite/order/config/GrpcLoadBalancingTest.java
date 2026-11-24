package com.quickbite.order.config;

import com.quickbite.grpc.restaurant.v1.QuoteOrderRequest;
import com.quickbite.grpc.restaurant.v1.QuoteOrderResponse;
import com.quickbite.grpc.restaurant.v1.RestaurantCatalogGrpc;
import io.grpc.Attributes;
import io.grpc.EquivalentAddressGroup;
import io.grpc.ManagedChannel;
import io.grpc.NameResolver;
import io.grpc.NameResolverProvider;
import io.grpc.NameResolverRegistry;
import io.grpc.Server;
import io.grpc.ServerTransportFilter;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import io.grpc.inprocess.InProcessSocketAddress;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.SocketAddress;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The channel service config ({@link GrpcClientConfig#serviceConfig}) against two copies of
 * restaurant-service behind one name, as a headless Service presents them: DNS returns both
 * addresses. Here a test name resolver plays DNS and the copies are in-process servers.
 */
class GrpcLoadBalancingTest {

    private static final String SCHEME = "quickbite-test";
    private static final List<String> COPIES =
            List.of("restaurant-a-" + System.nanoTime(), "restaurant-b-" + System.nanoTime());
    private static final NameResolverProvider TWO_COPIES = new TwoCopiesResolver();

    private final List<Server> servers = new ArrayList<>();
    private final List<AtomicInteger> calls = List.of(new AtomicInteger(), new AtomicInteger());
    private final CountDownLatch bothConnected = new CountDownLatch(COPIES.size());
    private ManagedChannel channel;

    @BeforeAll
    static void registerResolver() {
        NameResolverRegistry.getDefaultRegistry().register(TWO_COPIES);
    }

    @AfterAll
    static void deregisterResolver() {
        NameResolverRegistry.getDefaultRegistry().deregister(TWO_COPIES);
    }

    @BeforeEach
    void startCopies() throws Exception {
        for (int i = 0; i < COPIES.size(); i++) {
            AtomicInteger counter = calls.get(i);
            servers.add(InProcessServerBuilder.forName(COPIES.get(i)).directExecutor()
                    .addTransportFilter(new ServerTransportFilter() {
                        @Override
                        public Attributes transportReady(Attributes attributes) {
                            bothConnected.countDown();
                            return attributes;
                        }
                    })
                    .addService(new RestaurantCatalogGrpc.RestaurantCatalogImplBase() {
                        @Override
                        public void quoteOrder(QuoteOrderRequest request, StreamObserver<QuoteOrderResponse> out) {
                            counter.incrementAndGet();
                            out.onNext(QuoteOrderResponse.newBuilder().setTotalMinor(1_000).build());
                            out.onCompleted();
                        }
                    })
                    .build().start());
        }
    }

    @AfterEach
    void stop() {
        if (channel != null) {
            channel.shutdownNow();
        }
        servers.forEach(Server::shutdownNow);
    }

    @Test
    void callsAreSpreadOverEveryCopy() throws Exception {
        var stub = connect(GrpcClientConfig.serviceConfig(RestaurantCatalogGrpc.SERVICE_NAME), true);

        quote(stub, 20);

        assertThat(calls.get(0).get() + calls.get(1).get()).isEqualTo(20);
        assertThat(calls).allSatisfy(c -> assertThat(c.get()).isBetween(8, 12));   // round robin: about half each
    }

    @Test
    void grpcsDefaultPolicyWouldSendEveryCallToOneCopy() throws Exception {
        // gRPC's own default: one connection, every call down it.
        var stub = connect(Map.of("loadBalancingConfig", List.of(Map.of("pick_first", Map.of()))), false);

        quote(stub, 20);

        assertThat(calls).extracting(AtomicInteger::get).containsExactlyInAnyOrder(20, 0);
    }

    @Test
    void aCopyThatGoesAwayIsSkipped() throws Exception {
        var stub = connect(GrpcClientConfig.serviceConfig(RestaurantCatalogGrpc.SERVICE_NAME), true);
        servers.getFirst().shutdownNow();       // e.g. a pod removed by a rollout

        quote(stub, 10);                        // every call succeeds; a retry covers one sent as it closed

        assertThat(calls.get(0).get()).isZero();
        assertThat(calls.get(1).get()).isEqualTo(10);
    }

    /** A channel built like order-service's, warmed up (and, if wanted, connected to both copies). */
    private RestaurantCatalogGrpc.RestaurantCatalogBlockingStub connect(Map<String, ?> serviceConfig,
                                                                       boolean expectBoth) throws Exception {
        channel = InProcessChannelBuilder.forTarget(SCHEME + ":///restaurant-service").directExecutor()
                .enableRetry().defaultServiceConfig(serviceConfig).build();
        var stub = RestaurantCatalogGrpc.newBlockingStub(channel);
        stub.quoteOrder(QuoteOrderRequest.getDefaultInstance());
        if (expectBoth) {
            assertThat(bothConnected.await(5, TimeUnit.SECONDS)).as("connected to both copies").isTrue();
        }
        calls.forEach(c -> c.set(0));
        return stub;
    }

    private static void quote(RestaurantCatalogGrpc.RestaurantCatalogBlockingStub stub, int times) {
        for (int i = 0; i < times; i++) {
            assertThat(stub.withDeadlineAfter(2, TimeUnit.SECONDS)
                    .quoteOrder(QuoteOrderRequest.getDefaultInstance()).getTotalMinor()).isEqualTo(1_000);
        }
    }

    /** Resolves any {@code quickbite-test:///...} target to both in-process copies, like DNS for a headless Service. */
    private static final class TwoCopiesResolver extends NameResolverProvider {

        @Override
        public NameResolver newNameResolver(URI targetUri, NameResolver.Args args) {
            if (!SCHEME.equals(targetUri.getScheme())) {
                return null;
            }
            return new NameResolver() {
                @Override
                public String getServiceAuthority() {
                    return "restaurant-service";
                }

                @Override
                public void start(Listener2 listener) {
                    listener.onResult(ResolutionResult.newBuilder()
                            .setAddresses(COPIES.stream()
                                    .map(name -> new EquivalentAddressGroup(new InProcessSocketAddress(name)))
                                    .toList())
                            .build());
                }

                @Override
                public void shutdown() {
                }
            };
        }

        @Override
        public String getDefaultScheme() {
            return SCHEME;
        }

        @Override
        protected boolean isAvailable() {
            return true;
        }

        @Override
        protected int priority() {
            return 5;
        }

        @Override
        public Collection<Class<? extends SocketAddress>> getProducedSocketAddressTypes() {
            return Set.of(InProcessSocketAddress.class);
        }
    }
}
