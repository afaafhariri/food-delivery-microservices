package com.quickbite.grpc.restaurant.v1;

import static io.grpc.MethodDescriptor.generateFullMethodName;

/**
 * <pre>
 * Authoritative menu &amp; pricing lookups, served by restaurant-service.
 * </pre>
 */
@io.grpc.stub.annotations.GrpcGenerated
public final class RestaurantCatalogGrpc {

  private RestaurantCatalogGrpc() {}

  public static final java.lang.String SERVICE_NAME = "quickbite.restaurant.v1.RestaurantCatalog";

  // Static method descriptors that strictly reflect the proto.
  private static volatile io.grpc.MethodDescriptor<com.quickbite.grpc.restaurant.v1.QuoteOrderRequest,
      com.quickbite.grpc.restaurant.v1.QuoteOrderResponse> getQuoteOrderMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "QuoteOrder",
      requestType = com.quickbite.grpc.restaurant.v1.QuoteOrderRequest.class,
      responseType = com.quickbite.grpc.restaurant.v1.QuoteOrderResponse.class,
      methodType = io.grpc.MethodDescriptor.MethodType.UNARY)
  public static io.grpc.MethodDescriptor<com.quickbite.grpc.restaurant.v1.QuoteOrderRequest,
      com.quickbite.grpc.restaurant.v1.QuoteOrderResponse> getQuoteOrderMethod() {
    io.grpc.MethodDescriptor<com.quickbite.grpc.restaurant.v1.QuoteOrderRequest, com.quickbite.grpc.restaurant.v1.QuoteOrderResponse> getQuoteOrderMethod;
    if ((getQuoteOrderMethod = RestaurantCatalogGrpc.getQuoteOrderMethod) == null) {
      synchronized (RestaurantCatalogGrpc.class) {
        if ((getQuoteOrderMethod = RestaurantCatalogGrpc.getQuoteOrderMethod) == null) {
          RestaurantCatalogGrpc.getQuoteOrderMethod = getQuoteOrderMethod =
              io.grpc.MethodDescriptor.<com.quickbite.grpc.restaurant.v1.QuoteOrderRequest, com.quickbite.grpc.restaurant.v1.QuoteOrderResponse>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
              .setFullMethodName(generateFullMethodName(SERVICE_NAME, "QuoteOrder"))
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  com.quickbite.grpc.restaurant.v1.QuoteOrderRequest.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  com.quickbite.grpc.restaurant.v1.QuoteOrderResponse.getDefaultInstance()))
              .setSchemaDescriptor(new RestaurantCatalogMethodDescriptorSupplier("QuoteOrder"))
              .build();
        }
      }
    }
    return getQuoteOrderMethod;
  }

  /**
   * Creates a new async stub that supports all call types for the service
   */
  public static RestaurantCatalogStub newStub(io.grpc.Channel channel) {
    io.grpc.stub.AbstractStub.StubFactory<RestaurantCatalogStub> factory =
      new io.grpc.stub.AbstractStub.StubFactory<RestaurantCatalogStub>() {
        @java.lang.Override
        public RestaurantCatalogStub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
          return new RestaurantCatalogStub(channel, callOptions);
        }
      };
    return RestaurantCatalogStub.newStub(factory, channel);
  }

  /**
   * Creates a new blocking-style stub that supports all types of calls on the service
   */
  public static RestaurantCatalogBlockingV2Stub newBlockingV2Stub(
      io.grpc.Channel channel) {
    io.grpc.stub.AbstractStub.StubFactory<RestaurantCatalogBlockingV2Stub> factory =
      new io.grpc.stub.AbstractStub.StubFactory<RestaurantCatalogBlockingV2Stub>() {
        @java.lang.Override
        public RestaurantCatalogBlockingV2Stub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
          return new RestaurantCatalogBlockingV2Stub(channel, callOptions);
        }
      };
    return RestaurantCatalogBlockingV2Stub.newStub(factory, channel);
  }

  /**
   * Creates a new blocking-style stub that supports unary and streaming output calls on the service
   */
  public static RestaurantCatalogBlockingStub newBlockingStub(
      io.grpc.Channel channel) {
    io.grpc.stub.AbstractStub.StubFactory<RestaurantCatalogBlockingStub> factory =
      new io.grpc.stub.AbstractStub.StubFactory<RestaurantCatalogBlockingStub>() {
        @java.lang.Override
        public RestaurantCatalogBlockingStub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
          return new RestaurantCatalogBlockingStub(channel, callOptions);
        }
      };
    return RestaurantCatalogBlockingStub.newStub(factory, channel);
  }

  /**
   * Creates a new ListenableFuture-style stub that supports unary calls on the service
   */
  public static RestaurantCatalogFutureStub newFutureStub(
      io.grpc.Channel channel) {
    io.grpc.stub.AbstractStub.StubFactory<RestaurantCatalogFutureStub> factory =
      new io.grpc.stub.AbstractStub.StubFactory<RestaurantCatalogFutureStub>() {
        @java.lang.Override
        public RestaurantCatalogFutureStub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
          return new RestaurantCatalogFutureStub(channel, callOptions);
        }
      };
    return RestaurantCatalogFutureStub.newStub(factory, channel);
  }

  /**
   * <pre>
   * Authoritative menu &amp; pricing lookups, served by restaurant-service.
   * </pre>
   */
  public interface AsyncService {

    /**
     * <pre>
     * Validates that the restaurant is active and every item is on its menu and
     * available, then prices the order server-side.
     * Errors:
     *   INVALID_ARGUMENT    malformed ids, empty item list, quantity &lt; 1
     *   NOT_FOUND           restaurant missing or inactive
     *   FAILED_PRECONDITION item not on this restaurant's menu, or unavailable
     * </pre>
     */
    default void quoteOrder(com.quickbite.grpc.restaurant.v1.QuoteOrderRequest request,
        io.grpc.stub.StreamObserver<com.quickbite.grpc.restaurant.v1.QuoteOrderResponse> responseObserver) {
      io.grpc.stub.ServerCalls.asyncUnimplementedUnaryCall(getQuoteOrderMethod(), responseObserver);
    }
  }

  /**
   * Base class for the server implementation of the service RestaurantCatalog.
   * <pre>
   * Authoritative menu &amp; pricing lookups, served by restaurant-service.
   * </pre>
   */
  public static abstract class RestaurantCatalogImplBase
      implements io.grpc.BindableService, AsyncService {

    @java.lang.Override public final io.grpc.ServerServiceDefinition bindService() {
      return RestaurantCatalogGrpc.bindService(this);
    }
  }

  /**
   * A stub to allow clients to do asynchronous rpc calls to service RestaurantCatalog.
   * <pre>
   * Authoritative menu &amp; pricing lookups, served by restaurant-service.
   * </pre>
   */
  public static final class RestaurantCatalogStub
      extends io.grpc.stub.AbstractAsyncStub<RestaurantCatalogStub> {
    private RestaurantCatalogStub(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected RestaurantCatalogStub build(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      return new RestaurantCatalogStub(channel, callOptions);
    }

    /**
     * <pre>
     * Validates that the restaurant is active and every item is on its menu and
     * available, then prices the order server-side.
     * Errors:
     *   INVALID_ARGUMENT    malformed ids, empty item list, quantity &lt; 1
     *   NOT_FOUND           restaurant missing or inactive
     *   FAILED_PRECONDITION item not on this restaurant's menu, or unavailable
     * </pre>
     */
    public void quoteOrder(com.quickbite.grpc.restaurant.v1.QuoteOrderRequest request,
        io.grpc.stub.StreamObserver<com.quickbite.grpc.restaurant.v1.QuoteOrderResponse> responseObserver) {
      io.grpc.stub.ClientCalls.asyncUnaryCall(
          getChannel().newCall(getQuoteOrderMethod(), getCallOptions()), request, responseObserver);
    }
  }

  /**
   * A stub to allow clients to do synchronous rpc calls to service RestaurantCatalog.
   * <pre>
   * Authoritative menu &amp; pricing lookups, served by restaurant-service.
   * </pre>
   */
  public static final class RestaurantCatalogBlockingV2Stub
      extends io.grpc.stub.AbstractBlockingStub<RestaurantCatalogBlockingV2Stub> {
    private RestaurantCatalogBlockingV2Stub(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected RestaurantCatalogBlockingV2Stub build(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      return new RestaurantCatalogBlockingV2Stub(channel, callOptions);
    }

    /**
     * <pre>
     * Validates that the restaurant is active and every item is on its menu and
     * available, then prices the order server-side.
     * Errors:
     *   INVALID_ARGUMENT    malformed ids, empty item list, quantity &lt; 1
     *   NOT_FOUND           restaurant missing or inactive
     *   FAILED_PRECONDITION item not on this restaurant's menu, or unavailable
     * </pre>
     */
    public com.quickbite.grpc.restaurant.v1.QuoteOrderResponse quoteOrder(com.quickbite.grpc.restaurant.v1.QuoteOrderRequest request) throws io.grpc.StatusException {
      return io.grpc.stub.ClientCalls.blockingV2UnaryCall(
          getChannel(), getQuoteOrderMethod(), getCallOptions(), request);
    }
  }

  /**
   * A stub to allow clients to do limited synchronous rpc calls to service RestaurantCatalog.
   * <pre>
   * Authoritative menu &amp; pricing lookups, served by restaurant-service.
   * </pre>
   */
  public static final class RestaurantCatalogBlockingStub
      extends io.grpc.stub.AbstractBlockingStub<RestaurantCatalogBlockingStub> {
    private RestaurantCatalogBlockingStub(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected RestaurantCatalogBlockingStub build(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      return new RestaurantCatalogBlockingStub(channel, callOptions);
    }

    /**
     * <pre>
     * Validates that the restaurant is active and every item is on its menu and
     * available, then prices the order server-side.
     * Errors:
     *   INVALID_ARGUMENT    malformed ids, empty item list, quantity &lt; 1
     *   NOT_FOUND           restaurant missing or inactive
     *   FAILED_PRECONDITION item not on this restaurant's menu, or unavailable
     * </pre>
     */
    public com.quickbite.grpc.restaurant.v1.QuoteOrderResponse quoteOrder(com.quickbite.grpc.restaurant.v1.QuoteOrderRequest request) {
      return io.grpc.stub.ClientCalls.blockingUnaryCall(
          getChannel(), getQuoteOrderMethod(), getCallOptions(), request);
    }
  }

  /**
   * A stub to allow clients to do ListenableFuture-style rpc calls to service RestaurantCatalog.
   * <pre>
   * Authoritative menu &amp; pricing lookups, served by restaurant-service.
   * </pre>
   */
  public static final class RestaurantCatalogFutureStub
      extends io.grpc.stub.AbstractFutureStub<RestaurantCatalogFutureStub> {
    private RestaurantCatalogFutureStub(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected RestaurantCatalogFutureStub build(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      return new RestaurantCatalogFutureStub(channel, callOptions);
    }

    /**
     * <pre>
     * Validates that the restaurant is active and every item is on its menu and
     * available, then prices the order server-side.
     * Errors:
     *   INVALID_ARGUMENT    malformed ids, empty item list, quantity &lt; 1
     *   NOT_FOUND           restaurant missing or inactive
     *   FAILED_PRECONDITION item not on this restaurant's menu, or unavailable
     * </pre>
     */
    public com.google.common.util.concurrent.ListenableFuture<com.quickbite.grpc.restaurant.v1.QuoteOrderResponse> quoteOrder(
        com.quickbite.grpc.restaurant.v1.QuoteOrderRequest request) {
      return io.grpc.stub.ClientCalls.futureUnaryCall(
          getChannel().newCall(getQuoteOrderMethod(), getCallOptions()), request);
    }
  }

  private static final int METHODID_QUOTE_ORDER = 0;

  private static final class MethodHandlers<Req, Resp> implements
      io.grpc.stub.ServerCalls.UnaryMethod<Req, Resp>,
      io.grpc.stub.ServerCalls.ServerStreamingMethod<Req, Resp>,
      io.grpc.stub.ServerCalls.ClientStreamingMethod<Req, Resp>,
      io.grpc.stub.ServerCalls.BidiStreamingMethod<Req, Resp> {
    private final AsyncService serviceImpl;
    private final int methodId;

    MethodHandlers(AsyncService serviceImpl, int methodId) {
      this.serviceImpl = serviceImpl;
      this.methodId = methodId;
    }

    @java.lang.Override
    @java.lang.SuppressWarnings("unchecked")
    public void invoke(Req request, io.grpc.stub.StreamObserver<Resp> responseObserver) {
      switch (methodId) {
        case METHODID_QUOTE_ORDER:
          serviceImpl.quoteOrder((com.quickbite.grpc.restaurant.v1.QuoteOrderRequest) request,
              (io.grpc.stub.StreamObserver<com.quickbite.grpc.restaurant.v1.QuoteOrderResponse>) responseObserver);
          break;
        default:
          throw new AssertionError();
      }
    }

    @java.lang.Override
    @java.lang.SuppressWarnings("unchecked")
    public io.grpc.stub.StreamObserver<Req> invoke(
        io.grpc.stub.StreamObserver<Resp> responseObserver) {
      switch (methodId) {
        default:
          throw new AssertionError();
      }
    }
  }

  public static final io.grpc.ServerServiceDefinition bindService(AsyncService service) {
    return io.grpc.ServerServiceDefinition.builder(getServiceDescriptor())
        .addMethod(
          getQuoteOrderMethod(),
          io.grpc.stub.ServerCalls.asyncUnaryCall(
            new MethodHandlers<
              com.quickbite.grpc.restaurant.v1.QuoteOrderRequest,
              com.quickbite.grpc.restaurant.v1.QuoteOrderResponse>(
                service, METHODID_QUOTE_ORDER)))
        .build();
  }

  private static abstract class RestaurantCatalogBaseDescriptorSupplier
      implements io.grpc.protobuf.ProtoFileDescriptorSupplier, io.grpc.protobuf.ProtoServiceDescriptorSupplier {
    RestaurantCatalogBaseDescriptorSupplier() {}

    @java.lang.Override
    public com.google.protobuf.Descriptors.FileDescriptor getFileDescriptor() {
      return com.quickbite.grpc.restaurant.v1.Catalog.getDescriptor();
    }

    @java.lang.Override
    public com.google.protobuf.Descriptors.ServiceDescriptor getServiceDescriptor() {
      return getFileDescriptor().findServiceByName("RestaurantCatalog");
    }
  }

  private static final class RestaurantCatalogFileDescriptorSupplier
      extends RestaurantCatalogBaseDescriptorSupplier {
    RestaurantCatalogFileDescriptorSupplier() {}
  }

  private static final class RestaurantCatalogMethodDescriptorSupplier
      extends RestaurantCatalogBaseDescriptorSupplier
      implements io.grpc.protobuf.ProtoMethodDescriptorSupplier {
    private final java.lang.String methodName;

    RestaurantCatalogMethodDescriptorSupplier(java.lang.String methodName) {
      this.methodName = methodName;
    }

    @java.lang.Override
    public com.google.protobuf.Descriptors.MethodDescriptor getMethodDescriptor() {
      return getServiceDescriptor().findMethodByName(methodName);
    }
  }

  private static volatile io.grpc.ServiceDescriptor serviceDescriptor;

  public static io.grpc.ServiceDescriptor getServiceDescriptor() {
    io.grpc.ServiceDescriptor result = serviceDescriptor;
    if (result == null) {
      synchronized (RestaurantCatalogGrpc.class) {
        result = serviceDescriptor;
        if (result == null) {
          serviceDescriptor = result = io.grpc.ServiceDescriptor.newBuilder(SERVICE_NAME)
              .setSchemaDescriptor(new RestaurantCatalogFileDescriptorSupplier())
              .addMethod(getQuoteOrderMethod())
              .build();
        }
      }
    }
    return result;
  }
}
