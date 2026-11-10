package com.quickbite.grpc.customer.v1;

import static io.grpc.MethodDescriptor.generateFullMethodName;

/**
 * <pre>
 * Customer identity &amp; saved-address lookups, served by customer-service.
 * </pre>
 */
@io.grpc.stub.annotations.GrpcGenerated
public final class CustomerDirectoryGrpc {

  private CustomerDirectoryGrpc() {}

  public static final java.lang.String SERVICE_NAME = "quickbite.customer.v1.CustomerDirectory";

  // Static method descriptors that strictly reflect the proto.
  private static volatile io.grpc.MethodDescriptor<com.quickbite.grpc.customer.v1.ResolveCustomerRequest,
      com.quickbite.grpc.customer.v1.ResolveCustomerResponse> getResolveCustomerMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "ResolveCustomer",
      requestType = com.quickbite.grpc.customer.v1.ResolveCustomerRequest.class,
      responseType = com.quickbite.grpc.customer.v1.ResolveCustomerResponse.class,
      methodType = io.grpc.MethodDescriptor.MethodType.UNARY)
  public static io.grpc.MethodDescriptor<com.quickbite.grpc.customer.v1.ResolveCustomerRequest,
      com.quickbite.grpc.customer.v1.ResolveCustomerResponse> getResolveCustomerMethod() {
    io.grpc.MethodDescriptor<com.quickbite.grpc.customer.v1.ResolveCustomerRequest, com.quickbite.grpc.customer.v1.ResolveCustomerResponse> getResolveCustomerMethod;
    if ((getResolveCustomerMethod = CustomerDirectoryGrpc.getResolveCustomerMethod) == null) {
      synchronized (CustomerDirectoryGrpc.class) {
        if ((getResolveCustomerMethod = CustomerDirectoryGrpc.getResolveCustomerMethod) == null) {
          CustomerDirectoryGrpc.getResolveCustomerMethod = getResolveCustomerMethod =
              io.grpc.MethodDescriptor.<com.quickbite.grpc.customer.v1.ResolveCustomerRequest, com.quickbite.grpc.customer.v1.ResolveCustomerResponse>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
              .setFullMethodName(generateFullMethodName(SERVICE_NAME, "ResolveCustomer"))
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  com.quickbite.grpc.customer.v1.ResolveCustomerRequest.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  com.quickbite.grpc.customer.v1.ResolveCustomerResponse.getDefaultInstance()))
              .setSchemaDescriptor(new CustomerDirectoryMethodDescriptorSupplier("ResolveCustomer"))
              .build();
        }
      }
    }
    return getResolveCustomerMethod;
  }

  /**
   * Creates a new async stub that supports all call types for the service
   */
  public static CustomerDirectoryStub newStub(io.grpc.Channel channel) {
    io.grpc.stub.AbstractStub.StubFactory<CustomerDirectoryStub> factory =
      new io.grpc.stub.AbstractStub.StubFactory<CustomerDirectoryStub>() {
        @java.lang.Override
        public CustomerDirectoryStub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
          return new CustomerDirectoryStub(channel, callOptions);
        }
      };
    return CustomerDirectoryStub.newStub(factory, channel);
  }

  /**
   * Creates a new blocking-style stub that supports all types of calls on the service
   */
  public static CustomerDirectoryBlockingV2Stub newBlockingV2Stub(
      io.grpc.Channel channel) {
    io.grpc.stub.AbstractStub.StubFactory<CustomerDirectoryBlockingV2Stub> factory =
      new io.grpc.stub.AbstractStub.StubFactory<CustomerDirectoryBlockingV2Stub>() {
        @java.lang.Override
        public CustomerDirectoryBlockingV2Stub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
          return new CustomerDirectoryBlockingV2Stub(channel, callOptions);
        }
      };
    return CustomerDirectoryBlockingV2Stub.newStub(factory, channel);
  }

  /**
   * Creates a new blocking-style stub that supports unary and streaming output calls on the service
   */
  public static CustomerDirectoryBlockingStub newBlockingStub(
      io.grpc.Channel channel) {
    io.grpc.stub.AbstractStub.StubFactory<CustomerDirectoryBlockingStub> factory =
      new io.grpc.stub.AbstractStub.StubFactory<CustomerDirectoryBlockingStub>() {
        @java.lang.Override
        public CustomerDirectoryBlockingStub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
          return new CustomerDirectoryBlockingStub(channel, callOptions);
        }
      };
    return CustomerDirectoryBlockingStub.newStub(factory, channel);
  }

  /**
   * Creates a new ListenableFuture-style stub that supports unary calls on the service
   */
  public static CustomerDirectoryFutureStub newFutureStub(
      io.grpc.Channel channel) {
    io.grpc.stub.AbstractStub.StubFactory<CustomerDirectoryFutureStub> factory =
      new io.grpc.stub.AbstractStub.StubFactory<CustomerDirectoryFutureStub>() {
        @java.lang.Override
        public CustomerDirectoryFutureStub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
          return new CustomerDirectoryFutureStub(channel, callOptions);
        }
      };
    return CustomerDirectoryFutureStub.newStub(factory, channel);
  }

  /**
   * <pre>
   * Customer identity &amp; saved-address lookups, served by customer-service.
   * </pre>
   */
  public interface AsyncService {

    /**
     * <pre>
     * Confirms the customer exists and is active. When address_id is set, also
     * resolves that saved address (it must belong to the customer).
     * Errors:
     *   INVALID_ARGUMENT    malformed ids
     *   NOT_FOUND           customer missing, or address not owned by customer
     *   FAILED_PRECONDITION customer is deactivated
     * </pre>
     */
    default void resolveCustomer(com.quickbite.grpc.customer.v1.ResolveCustomerRequest request,
        io.grpc.stub.StreamObserver<com.quickbite.grpc.customer.v1.ResolveCustomerResponse> responseObserver) {
      io.grpc.stub.ServerCalls.asyncUnimplementedUnaryCall(getResolveCustomerMethod(), responseObserver);
    }
  }

  /**
   * Base class for the server implementation of the service CustomerDirectory.
   * <pre>
   * Customer identity &amp; saved-address lookups, served by customer-service.
   * </pre>
   */
  public static abstract class CustomerDirectoryImplBase
      implements io.grpc.BindableService, AsyncService {

    @java.lang.Override public final io.grpc.ServerServiceDefinition bindService() {
      return CustomerDirectoryGrpc.bindService(this);
    }
  }

  /**
   * A stub to allow clients to do asynchronous rpc calls to service CustomerDirectory.
   * <pre>
   * Customer identity &amp; saved-address lookups, served by customer-service.
   * </pre>
   */
  public static final class CustomerDirectoryStub
      extends io.grpc.stub.AbstractAsyncStub<CustomerDirectoryStub> {
    private CustomerDirectoryStub(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected CustomerDirectoryStub build(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      return new CustomerDirectoryStub(channel, callOptions);
    }

    /**
     * <pre>
     * Confirms the customer exists and is active. When address_id is set, also
     * resolves that saved address (it must belong to the customer).
     * Errors:
     *   INVALID_ARGUMENT    malformed ids
     *   NOT_FOUND           customer missing, or address not owned by customer
     *   FAILED_PRECONDITION customer is deactivated
     * </pre>
     */
    public void resolveCustomer(com.quickbite.grpc.customer.v1.ResolveCustomerRequest request,
        io.grpc.stub.StreamObserver<com.quickbite.grpc.customer.v1.ResolveCustomerResponse> responseObserver) {
      io.grpc.stub.ClientCalls.asyncUnaryCall(
          getChannel().newCall(getResolveCustomerMethod(), getCallOptions()), request, responseObserver);
    }
  }

  /**
   * A stub to allow clients to do synchronous rpc calls to service CustomerDirectory.
   * <pre>
   * Customer identity &amp; saved-address lookups, served by customer-service.
   * </pre>
   */
  public static final class CustomerDirectoryBlockingV2Stub
      extends io.grpc.stub.AbstractBlockingStub<CustomerDirectoryBlockingV2Stub> {
    private CustomerDirectoryBlockingV2Stub(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected CustomerDirectoryBlockingV2Stub build(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      return new CustomerDirectoryBlockingV2Stub(channel, callOptions);
    }

    /**
     * <pre>
     * Confirms the customer exists and is active. When address_id is set, also
     * resolves that saved address (it must belong to the customer).
     * Errors:
     *   INVALID_ARGUMENT    malformed ids
     *   NOT_FOUND           customer missing, or address not owned by customer
     *   FAILED_PRECONDITION customer is deactivated
     * </pre>
     */
    public com.quickbite.grpc.customer.v1.ResolveCustomerResponse resolveCustomer(com.quickbite.grpc.customer.v1.ResolveCustomerRequest request) throws io.grpc.StatusException {
      return io.grpc.stub.ClientCalls.blockingV2UnaryCall(
          getChannel(), getResolveCustomerMethod(), getCallOptions(), request);
    }
  }

  /**
   * A stub to allow clients to do limited synchronous rpc calls to service CustomerDirectory.
   * <pre>
   * Customer identity &amp; saved-address lookups, served by customer-service.
   * </pre>
   */
  public static final class CustomerDirectoryBlockingStub
      extends io.grpc.stub.AbstractBlockingStub<CustomerDirectoryBlockingStub> {
    private CustomerDirectoryBlockingStub(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected CustomerDirectoryBlockingStub build(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      return new CustomerDirectoryBlockingStub(channel, callOptions);
    }

    /**
     * <pre>
     * Confirms the customer exists and is active. When address_id is set, also
     * resolves that saved address (it must belong to the customer).
     * Errors:
     *   INVALID_ARGUMENT    malformed ids
     *   NOT_FOUND           customer missing, or address not owned by customer
     *   FAILED_PRECONDITION customer is deactivated
     * </pre>
     */
    public com.quickbite.grpc.customer.v1.ResolveCustomerResponse resolveCustomer(com.quickbite.grpc.customer.v1.ResolveCustomerRequest request) {
      return io.grpc.stub.ClientCalls.blockingUnaryCall(
          getChannel(), getResolveCustomerMethod(), getCallOptions(), request);
    }
  }

  /**
   * A stub to allow clients to do ListenableFuture-style rpc calls to service CustomerDirectory.
   * <pre>
   * Customer identity &amp; saved-address lookups, served by customer-service.
   * </pre>
   */
  public static final class CustomerDirectoryFutureStub
      extends io.grpc.stub.AbstractFutureStub<CustomerDirectoryFutureStub> {
    private CustomerDirectoryFutureStub(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected CustomerDirectoryFutureStub build(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      return new CustomerDirectoryFutureStub(channel, callOptions);
    }

    /**
     * <pre>
     * Confirms the customer exists and is active. When address_id is set, also
     * resolves that saved address (it must belong to the customer).
     * Errors:
     *   INVALID_ARGUMENT    malformed ids
     *   NOT_FOUND           customer missing, or address not owned by customer
     *   FAILED_PRECONDITION customer is deactivated
     * </pre>
     */
    public com.google.common.util.concurrent.ListenableFuture<com.quickbite.grpc.customer.v1.ResolveCustomerResponse> resolveCustomer(
        com.quickbite.grpc.customer.v1.ResolveCustomerRequest request) {
      return io.grpc.stub.ClientCalls.futureUnaryCall(
          getChannel().newCall(getResolveCustomerMethod(), getCallOptions()), request);
    }
  }

  private static final int METHODID_RESOLVE_CUSTOMER = 0;

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
        case METHODID_RESOLVE_CUSTOMER:
          serviceImpl.resolveCustomer((com.quickbite.grpc.customer.v1.ResolveCustomerRequest) request,
              (io.grpc.stub.StreamObserver<com.quickbite.grpc.customer.v1.ResolveCustomerResponse>) responseObserver);
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
          getResolveCustomerMethod(),
          io.grpc.stub.ServerCalls.asyncUnaryCall(
            new MethodHandlers<
              com.quickbite.grpc.customer.v1.ResolveCustomerRequest,
              com.quickbite.grpc.customer.v1.ResolveCustomerResponse>(
                service, METHODID_RESOLVE_CUSTOMER)))
        .build();
  }

  private static abstract class CustomerDirectoryBaseDescriptorSupplier
      implements io.grpc.protobuf.ProtoFileDescriptorSupplier, io.grpc.protobuf.ProtoServiceDescriptorSupplier {
    CustomerDirectoryBaseDescriptorSupplier() {}

    @java.lang.Override
    public com.google.protobuf.Descriptors.FileDescriptor getFileDescriptor() {
      return com.quickbite.grpc.customer.v1.CustomerDirectoryOuterClass.getDescriptor();
    }

    @java.lang.Override
    public com.google.protobuf.Descriptors.ServiceDescriptor getServiceDescriptor() {
      return getFileDescriptor().findServiceByName("CustomerDirectory");
    }
  }

  private static final class CustomerDirectoryFileDescriptorSupplier
      extends CustomerDirectoryBaseDescriptorSupplier {
    CustomerDirectoryFileDescriptorSupplier() {}
  }

  private static final class CustomerDirectoryMethodDescriptorSupplier
      extends CustomerDirectoryBaseDescriptorSupplier
      implements io.grpc.protobuf.ProtoMethodDescriptorSupplier {
    private final java.lang.String methodName;

    CustomerDirectoryMethodDescriptorSupplier(java.lang.String methodName) {
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
      synchronized (CustomerDirectoryGrpc.class) {
        result = serviceDescriptor;
        if (result == null) {
          serviceDescriptor = result = io.grpc.ServiceDescriptor.newBuilder(SERVICE_NAME)
              .setSchemaDescriptor(new CustomerDirectoryFileDescriptorSupplier())
              .addMethod(getResolveCustomerMethod())
              .build();
        }
      }
    }
    return result;
  }
}
