package vn.zalopay.benchmark.core.client;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mockConstructionWithAnswer;

import com.google.common.collect.ImmutableList;
import com.google.protobuf.DynamicMessage;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.util.JsonFormat;

import io.grpc.StatusRuntimeException;
import io.grpc.netty.GrpcSslContexts;
import io.netty.handler.ssl.ApplicationProtocolConfig;
import io.netty.handler.ssl.ApplicationProtocolNames;
import io.netty.handler.ssl.SslContextBuilder;

import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.testng.Assert;
import org.testng.annotations.Test;

import vn.zalopay.benchmark.core.BaseTest;
import vn.zalopay.benchmark.core.ClientCaller;
import vn.zalopay.benchmark.core.config.GrpcRequestConfig;
import vn.zalopay.benchmark.core.protobuf.ProtocInvoker;
import vn.zalopay.benchmark.core.specification.GrpcResponse;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import javax.net.ssl.SSLException;

public class ClientCallerTest extends BaseTest {
    static int countMockFailedALPN = 0;

    @Test
    public void testCanSendGrpcUnaryRequest() {
        clientCaller = new ClientCaller(DEFAULT_GRPC_REQUEST_CONFIG);
        ImmutableList<DynamicMessage> req = clientCaller.buildRequestAndMetadata(REQUEST_JSON, "key1:1,key2:2");
        GrpcResponse resp = clientCaller.call("5000", req);
        clientCaller.shutdownNettyChannel();
        Assert.assertNotNull(resp);
        Assert.assertTrue(resp.getGrpcMessageString().contains("\"theme\": \"Hello server"));
    }

    @Test
    public void testCanGetShutDownBoolean() {
        clientCaller = new ClientCaller(DEFAULT_GRPC_REQUEST_CONFIG);
        ImmutableList<DynamicMessage> req = clientCaller.buildRequestAndMetadata(REQUEST_JSON, "key1:1,key2:2");
        Assert.assertEquals(clientCaller.isShutdown(), false);
        Assert.assertEquals(clientCaller.isTerminated(), false);
    }

    @Test
    public void testCanGetShutDownBooleanAfterShutdown() {
        clientCaller = new ClientCaller(DEFAULT_GRPC_REQUEST_CONFIG);
        ImmutableList<DynamicMessage> req = clientCaller.buildRequestAndMetadata(REQUEST_JSON, "key1:1,key2:2");
        clientCaller.shutdownNettyChannel();
        Assert.assertEquals(clientCaller.isShutdown(), true);
        Assert.assertEquals(clientCaller.isTerminated(), true);
    }

    @Test
    public void testCanCallClientStreamingRequest() {
        clientCaller = new ClientCaller(DEFAULT_GRPC_REQUEST_CONFIG);
        ImmutableList<DynamicMessage> req = clientCaller.buildRequestAndMetadata(REQUEST_JSON, "key1:1,key2:2");
        GrpcResponse resp = clientCaller.callClientStreaming("5000", req);
        clientCaller.shutdownNettyChannel();
        Assert.assertNotNull(resp);
        Assert.assertTrue(resp.getGrpcMessageString().contains("\"theme\": \"Hello server"));
    }

    @Test
    public void testCanCallServerStreamingRequest() {
        clientCaller = new ClientCaller(DEFAULT_GRPC_REQUEST_CONFIG);
        ImmutableList<DynamicMessage> req = clientCaller.buildRequestAndMetadata(REQUEST_JSON, "key1:1,key2:2");
        GrpcResponse resp = clientCaller.callServerStreaming("5000", req);
        clientCaller.shutdownNettyChannel();
        Assert.assertNotNull(resp);
        Assert.assertTrue(resp.getGrpcMessageString().contains("\"theme\": \"Hello server"));
    }

    @Test
    public void testCanCallBidiStreamingRequest() {
        clientCaller = new ClientCaller(DEFAULT_GRPC_REQUEST_CONFIG);
        ImmutableList<DynamicMessage> req = clientCaller.buildRequestAndMetadata(REQUEST_JSON, "key1:1,key2:2");
        GrpcResponse resp = clientCaller.callBidiStreaming("5000", req);
        clientCaller.shutdownNettyChannel();
        Assert.assertNotNull(resp);
        Assert.assertTrue(resp.getGrpcMessageString().contains("\"theme\": \"Hello server"));
    }

    @Test
    public void testCanSendRequestWithNegativeTimeoutRequest() {
        clientCaller = new ClientCaller(DEFAULT_GRPC_REQUEST_CONFIG);
        ImmutableList<DynamicMessage> req = clientCaller.buildRequestAndMetadata(REQUEST_JSON, METADATA);
        GrpcResponse resp = clientCaller.call("-10", req);
        clientCaller.shutdownNettyChannel();
        Assert.assertNotNull(resp);
        Assert.assertTrue(resp.getGrpcMessageString().contains("\"theme\": \"Hello server"));
    }

    @Test(
            expectedExceptions = IllegalArgumentException.class,
            expectedExceptionsMessageRegExp =
                    "Metadata entry must be valid JSON String or in key1:value1,key2:value2 format"
                            + " if not JsonString but found: key1=1,key2:2")
    public void testCanThrowExceptionWithInvalidMetaData() {
        clientCaller = new ClientCaller(DEFAULT_GRPC_REQUEST_CONFIG);
        ImmutableList<DynamicMessage> req = clientCaller.buildRequestAndMetadata(REQUEST_JSON, "key1=1,key2:2");
        GrpcResponse resp = clientCaller.call("2000", req);
        clientCaller.shutdownNettyChannel();
        Assert.assertNotNull(resp);
        Assert.assertTrue(resp.getGrpcMessageString().contains("\"theme\": \"Hello server"));
    }

    @Test
    public void testCanSendGrpcUnaryRequestWithMetaData() {
        clientCaller = new ClientCaller(DEFAULT_GRPC_REQUEST_CONFIG);
        ImmutableList<DynamicMessage> req = clientCaller.buildRequestAndMetadata(REQUEST_JSON, METADATA);
        GrpcResponse resp = clientCaller.call("2000", req);
        clientCaller.shutdownNettyChannel();
        Assert.assertNotNull(resp);
        Assert.assertTrue(resp.getGrpcMessageString().contains("\"theme\": \"Hello server"));
    }

    @Test
    public void testCanSendGrpcUnaryRequestWithEncodedMetaData()
            throws UnsupportedEncodingException {
        clientCaller = new ClientCaller(DEFAULT_GRPC_REQUEST_CONFIG);
        ImmutableList<DynamicMessage> req = clientCaller.buildRequestAndMetadata(
                REQUEST_JSON,
                "tracestate:" + URLEncoder.encode("a=3,b:4", StandardCharsets.UTF_8.name()));
        GrpcResponse resp = clientCaller.call("2000", req);
        clientCaller.shutdownNettyChannel();
        Assert.assertNotNull(resp);
        Assert.assertTrue(resp.getGrpcMessageString().contains("\"theme\": \"Hello server"));
    }

    @Test
    public void testCanSendGrpcUnaryRequestWithSSLAndDisableSSLVerification() {
        GrpcRequestConfig grpcRequestConfig =
                new GrpcRequestConfig(
                        HOST_PORT_TLS,
                        PROTO_WITH_EXTERNAL_IMPORT_FOLDER.toString(),
                        LIB_FOLDER.toString(),
                        FULL_METHOD,
                        true,
                        true,
                        DEFAULT_CHANNEL_SHUTDOWN_TIME);
        clientCaller = new ClientCaller(grpcRequestConfig);
        ImmutableList<DynamicMessage> req = clientCaller.buildRequestAndMetadata(REQUEST_JSON, METADATA);
        GrpcResponse resp = clientCaller.call("10000", req);
        clientCaller.shutdownNettyChannel();
        Assert.assertNotNull(resp);
        Assert.assertTrue(resp.getGrpcMessageString().contains("\"theme\": \"Hello server"));
    }

    @Test
    public void testCanSendGrpcUnaryRequestWithSSLAndEnableSSLVerification() {
        GrpcRequestConfig grpcRequestConfig =
                new GrpcRequestConfig(
                        HOST_PORT_TLS,
                        PROTO_WITH_EXTERNAL_IMPORT_FOLDER.toString(),
                        LIB_FOLDER.toString(),
                        FULL_METHOD,
                        true,
                        false,
                        DEFAULT_CHANNEL_SHUTDOWN_TIME);
        clientCaller = new ClientCaller(grpcRequestConfig);
        ImmutableList<DynamicMessage> req = clientCaller.buildRequestAndMetadata(REQUEST_JSON, METADATA);
        GrpcResponse resp = clientCaller.call("10000", req);
        clientCaller.shutdownNettyChannel();
        Assert.assertNotNull(resp);
        Assert.assertTrue(resp.getGrpcMessageString().contains("\"theme\": \"Hello server"));
    }

    @Test
    public void
            testCanSendGrpcUnaryRequestWithSSLAndEnableSSLVerificationAndErrorUnsupportedOperationException() {
        try (MockedConstruction<ApplicationProtocolConfig>
                applicationProtocolConfigMockedConstruction =
                        mockConstructionWithAnswer(
                                ApplicationProtocolConfig.class,
                                (invocation) -> {
                                    // Simulate JDK not supported ALPN
                                    if (countMockFailedALPN == 0) {
                                        countMockFailedALPN++;
                                        throw new UnsupportedOperationException(
                                                "Dummy UnsupportedOperationException");
                                    }
                                    switch (invocation.getMethod().getName()) {
                                        case "protocol":
                                            return ApplicationProtocolConfig.Protocol.ALPN;
                                        case "selectorFailureBehavior":
                                            return ApplicationProtocolConfig.SelectorFailureBehavior
                                                    .NO_ADVERTISE;
                                        case "selectedListenerFailureBehavior":
                                            return ApplicationProtocolConfig
                                                    .SelectedListenerFailureBehavior.ACCEPT;
                                        case "supportedProtocols":
                                            return Arrays.asList(ApplicationProtocolNames.HTTP_2);
                                        default:
                                            throw new UnsupportedOperationException(
                                                    "Dummy UnsupportedOperationException");
                                    }
                                })) {
            GrpcRequestConfig grpcRequestConfig =
                    new GrpcRequestConfig(
                            HOST_PORT_TLS,
                            PROTO_WITH_EXTERNAL_IMPORT_FOLDER.toString(),
                            LIB_FOLDER.toString(),
                            FULL_METHOD,
                            true,
                            false,
                            DEFAULT_CHANNEL_SHUTDOWN_TIME);
            clientCaller = new ClientCaller(grpcRequestConfig);
            ImmutableList<DynamicMessage> req = clientCaller.buildRequestAndMetadata(REQUEST_JSON, METADATA);
            GrpcResponse resp = clientCaller.call("10000", req);
            clientCaller.shutdownNettyChannel();
            Assert.assertNotNull(resp);
            Assert.assertTrue(resp.getGrpcMessageString().contains("\"theme\": \"Hello server"));
        }
    }

    @Test(
            expectedExceptions = RuntimeException.class,
            expectedExceptionsMessageRegExp = "Caught exception while parsing deadline to long")
    public void testThrowExceptionWithInvalidTimeoutFormat() {
        clientCaller = new ClientCaller(DEFAULT_GRPC_REQUEST_CONFIG);
        ImmutableList<DynamicMessage> req = clientCaller.buildRequestAndMetadata(REQUEST_JSON, METADATA);
        clientCaller.call("1000s", req);
    }

    @Test(
            expectedExceptions = RuntimeException.class,
            expectedExceptionsMessageRegExp = "Caught exception while parsing deadline to long")
    public void testThrowExceptionWithBlankTimeoutFormat() {
        clientCaller = new ClientCaller(DEFAULT_GRPC_REQUEST_CONFIG);
        ImmutableList<DynamicMessage> req = clientCaller.buildRequestAndMetadata(REQUEST_JSON, METADATA);
        clientCaller.call(" ", req);
    }

    @Test(
            expectedExceptions = RuntimeException.class,
            expectedExceptionsMessageRegExp = "Caught exception while parsing deadline to long")
    public void testThrowExceptionWithEmptyTimeoutFormat() {
        clientCaller = new ClientCaller(DEFAULT_GRPC_REQUEST_CONFIG);
        ImmutableList<DynamicMessage> req = clientCaller.buildRequestAndMetadata(REQUEST_JSON, METADATA);
        clientCaller.call("", req);
    }

    @Test(
            expectedExceptions = RuntimeException.class,
            expectedExceptionsMessageRegExp = "Caught exception while parsing deadline to long")
    public void testThrowExceptionWithNullTimeoutFormat() {
        clientCaller = new ClientCaller(DEFAULT_GRPC_REQUEST_CONFIG);
        ImmutableList<DynamicMessage> req = clientCaller.buildRequestAndMetadata(REQUEST_JSON, METADATA);
        clientCaller.call(null, req);
    }

    @Test(
            expectedExceptions = RuntimeException.class,
            expectedExceptionsMessageRegExp = "Caught exception while parsing request for rpc")
    public void testThrowExceptionWithInvalidRequestJson() {
        clientCaller = new ClientCaller(DEFAULT_GRPC_REQUEST_CONFIG);
        ImmutableList<DynamicMessage> req = clientCaller.buildRequestAndMetadata(
                "{shelf:{\"id\":1599156420811,\"theme\":\"Hello server!!\".}}", METADATA);
        clientCaller.call("1000", req);
    }

    @Test(
            expectedExceptions = RuntimeException.class,
            expectedExceptionsMessageRegExp = "Caught exception while parsing request for rpc")
    public void testThrowExceptionWithParsingRequestToJson() {
        MockedStatic<com.google.protobuf.util.JsonFormat> jsonFormat =
                Mockito.mockStatic(com.google.protobuf.util.JsonFormat.class);
        jsonFormat
                .when(JsonFormat::printer)
                .then((i) -> new InvalidProtocolBufferException("Dummy Exception"));
        clientCaller = new ClientCaller(DEFAULT_GRPC_REQUEST_CONFIG);
        ImmutableList<DynamicMessage> req = clientCaller.buildRequestAndMetadata(
                "{shelf:{\"id\":1599156420811,\"theme\":\"Hello server!!\".}}", METADATA);
        clientCaller.call("1000", req);
    }

    @Test(
            expectedExceptions = RuntimeException.class,
            expectedExceptionsMessageRegExp = "Unable to resolve service by invoking protoc.*")
    public void testThrowExceptionWithExceptionInProtocInvoke() {
        MockedStatic<ProtocInvoker> protocInvoker = Mockito.mockStatic(ProtocInvoker.class);
        protocInvoker
                .when(
                        () ->
                                ProtocInvoker.forConfig(Mockito.anyString(), Mockito.anyString())
                                        .invoke())
                .thenThrow(new RuntimeException("Dummy Exception"));
        clientCaller = new ClientCaller(DEFAULT_GRPC_REQUEST_CONFIG);
    }

    @Test(
            expectedExceptions = RuntimeException.class,
            expectedExceptionsMessageRegExp = "Error in create SSL connection!")
    public void testCanThrowExceptionWithSSLException() {
        MockedStatic<io.grpc.netty.GrpcSslContexts> grpcSslContextBuilder =
                Mockito.mockStatic(io.grpc.netty.GrpcSslContexts.class);
        grpcSslContextBuilder
                .when(() -> GrpcSslContexts.forClient())
                .then(
                        invocation -> {
                            SslContextBuilder sslContext = Mockito.mock(SslContextBuilder.class);
                            Mockito.when(
                                            sslContext.applicationProtocolConfig(
                                                    any(ApplicationProtocolConfig.class)))
                                    .then(i -> sslContext);
                            Mockito.when(sslContext.build())
                                    .thenThrow(new SSLException("Dummy Exception"));
                            return sslContext;
                        });
        GrpcRequestConfig grpcRequestConfig =
                new GrpcRequestConfig(
                        "localhost:1231",
                        PROTO_WITH_EXTERNAL_IMPORT_FOLDER.toString(),
                        LIB_FOLDER.toString(),
                        FULL_METHOD,
                        true,
                        false,
                        DEFAULT_CHANNEL_SHUTDOWN_TIME);
        clientCaller = new ClientCaller(grpcRequestConfig);
    }

    @Test(
            expectedExceptions = RuntimeException.class,
            expectedExceptionsMessageRegExp = "Error in create SSL connection!")
    public void testCanThrowExceptionWithSSLExceptionAndDisableSSLVerification() {
        MockedStatic<io.grpc.netty.GrpcSslContexts> grpcSslContextBuilder =
                Mockito.mockStatic(io.grpc.netty.GrpcSslContexts.class);
        grpcSslContextBuilder
                .when(() -> GrpcSslContexts.forClient())
                .then(
                        invocation -> {
                            SslContextBuilder sslContext = Mockito.mock(SslContextBuilder.class);
                            Mockito.when(
                                            sslContext.applicationProtocolConfig(
                                                    any(ApplicationProtocolConfig.class)))
                                    .then(i -> sslContext);
                            Mockito.when(sslContext.build())
                                    .thenThrow(new SSLException("Dummy Exception"));
                            return sslContext;
                        });
        GrpcRequestConfig grpcRequestConfig =
                new GrpcRequestConfig(
                        "localhost:1231",
                        PROTO_WITH_EXTERNAL_IMPORT_FOLDER.toString(),
                        LIB_FOLDER.toString(),
                        FULL_METHOD,
                        true,
                        true,
                        DEFAULT_CHANNEL_SHUTDOWN_TIME);
        clientCaller = new ClientCaller(grpcRequestConfig);
    }

    @Test(
            expectedExceptions = StatusRuntimeException.class,
            expectedExceptionsMessageRegExp = "DEADLINE_EXCEEDED: .*")
    public void testThrowExceptionWithTimeoutRequest() throws Throwable {
        clientCaller = new ClientCaller(DEFAULT_GRPC_REQUEST_CONFIG);
        ImmutableList<DynamicMessage> req = clientCaller.buildRequestAndMetadata(REQUEST_JSON, METADATA);
        GrpcResponse resp = clientCaller.call("1", req);
        clientCaller.shutdownNettyChannel();
        Assert.assertNotNull(resp);
        throw resp.getThrowable();
    }

    @Test(
            expectedExceptions = StatusRuntimeException.class,
            expectedExceptionsMessageRegExp = "DEADLINE_EXCEEDED: .*")
    public void testThrowExceptionWithTimeoutRequestServerStream() throws Throwable {
        GrpcRequestConfig grpcRequestConfig =
                new GrpcRequestConfig(
                        HOST_PORT,
                        PROTO_WITH_EXTERNAL_IMPORT_FOLDER.toString(),
                        LIB_FOLDER.toString(),
                        "bookstore.Bookstore/GetShelfStreamServer",
                        false,
                        false,
                        DEFAULT_CHANNEL_SHUTDOWN_TIME);
        clientCaller = new ClientCaller(grpcRequestConfig);
        ImmutableList<DynamicMessage> req = clientCaller.buildRequestAndMetadata(REQUEST_JSON, METADATA);
        GrpcResponse resp = clientCaller.callServerStreaming("1", req);
        clientCaller.shutdownNettyChannel();
        Assert.assertNotNull(resp);
        throw resp.getThrowable();
    }

    @Test(
            expectedExceptions = RuntimeException.class,
            expectedExceptionsMessageRegExp = "Caught exception while " + "waiting for rpc.*")
    public void testThrowExceptionWithTimeoutRequestClientStream() {
        GrpcRequestConfig grpcRequestConfig =
                new GrpcRequestConfig(
                        HOST_PORT,
                        PROTO_WITH_EXTERNAL_IMPORT_FOLDER.toString(),
                        LIB_FOLDER.toString(),
                        "bookstore.Bookstore/GetShelfStreamClient",
                        false,
                        false,
                        DEFAULT_CHANNEL_SHUTDOWN_TIME);
        clientCaller = new ClientCaller(grpcRequestConfig);
        ImmutableList<DynamicMessage> req = clientCaller.buildRequestAndMetadata(REQUEST_JSON, METADATA);
        GrpcResponse resp = clientCaller.callClientStreaming("1", req);
        clientCaller.shutdownNettyChannel();
        Assert.assertNotNull(resp);
        Assert.assertTrue(resp.getGrpcMessageString().contains("\"theme\": \"Hello server"));
    }

    @Test(
            expectedExceptions = RuntimeException.class,
            expectedExceptionsMessageRegExp = "Caught exception while " + "waiting for rpc.*")
    public void testThrowExceptionWithTimeoutRequestBidiStream() {
        GrpcRequestConfig grpcRequestConfig =
                new GrpcRequestConfig(
                        HOST_PORT,
                        PROTO_WITH_EXTERNAL_IMPORT_FOLDER.toString(),
                        LIB_FOLDER.toString(),
                        "bookstore.Bookstore/GetShelfStreamBidi",
                        false,
                        false,
                        DEFAULT_CHANNEL_SHUTDOWN_TIME);
        clientCaller = new ClientCaller(grpcRequestConfig);
        ImmutableList<DynamicMessage> req = clientCaller.buildRequestAndMetadata(REQUEST_JSON, METADATA);
        GrpcResponse resp = clientCaller.callBidiStreaming("1", req);
        clientCaller.shutdownNettyChannel();
        Assert.assertNotNull(resp);
        Assert.assertTrue(resp.getGrpcMessageString().contains("\"theme\": \"Hello server"));
    }

    @Test(
            expectedExceptions = RuntimeException.class,
            expectedExceptionsMessageRegExp =
                    "Unable to find method invalidName in service Bookstore")
    public void testThrowExceptionWithInvalidMethodName() {
        GrpcRequestConfig grpcRequestConfig =
                new GrpcRequestConfig(
                        HOST_PORT,
                        PROTO_WITH_EXTERNAL_IMPORT_FOLDER.toString(),
                        LIB_FOLDER.toString(),
                        "bookstore.Bookstore/invalidName",
                        false,
                        false,
                        DEFAULT_CHANNEL_SHUTDOWN_TIME);
        clientCaller = new ClientCaller(grpcRequestConfig);
        clientCaller.buildRequestAndMetadata(REQUEST_JSON, METADATA);
    }

    @Test(
            expectedExceptions = NullPointerException.class,
            expectedExceptionsMessageRegExp = "fullMethodName")
    public void testThrowExceptionWithNullMethodName() {
        GrpcRequestConfig grpcRequestConfig =
                new GrpcRequestConfig(
                        HOST_PORT,
                        PROTO_WITH_EXTERNAL_IMPORT_FOLDER.toString(),
                        LIB_FOLDER.toString(),
                        null,
                        false,
                        false,
                        DEFAULT_CHANNEL_SHUTDOWN_TIME);
        clientCaller = new ClientCaller(grpcRequestConfig);
        clientCaller.buildRequestAndMetadata(REQUEST_JSON, METADATA);
    }

    @Test(
            expectedExceptions = IllegalArgumentException.class,
            expectedExceptionsMessageRegExp = "Could not extract full service from  ")
    public void testThrowExceptionWithBlankMethodName() {
        GrpcRequestConfig grpcRequestConfig =
                new GrpcRequestConfig(
                        HOST_PORT,
                        PROTO_WITH_EXTERNAL_IMPORT_FOLDER.toString(),
                        LIB_FOLDER.toString(),
                        " ",
                        false,
                        false,
                        DEFAULT_CHANNEL_SHUTDOWN_TIME);
        clientCaller = new ClientCaller(grpcRequestConfig);
        ImmutableList<DynamicMessage> req = clientCaller.buildRequestAndMetadata(REQUEST_JSON, METADATA);
        GrpcResponse resp = clientCaller.call("10", req);
        clientCaller.shutdownNettyChannel();
        Assert.assertNotNull(resp);
        Assert.assertTrue(resp.getGrpcMessageString().contains("\"theme\": \"Hello server"));
    }

    @Test(
            expectedExceptions = IllegalArgumentException.class,
            expectedExceptionsMessageRegExp = "Could not extract full service from ")
    public void testThrowExceptionWithEmptyMethodName() {
        GrpcRequestConfig grpcRequestConfig =
                new GrpcRequestConfig(
                        HOST_PORT,
                        PROTO_WITH_EXTERNAL_IMPORT_FOLDER.toString(),
                        LIB_FOLDER.toString(),
                        "",
                        false,
                        false,
                        DEFAULT_CHANNEL_SHUTDOWN_TIME);
        clientCaller = new ClientCaller(grpcRequestConfig);
        ImmutableList<DynamicMessage> req = clientCaller.buildRequestAndMetadata(REQUEST_JSON, METADATA);
        GrpcResponse resp = clientCaller.call("10", req);
        clientCaller.shutdownNettyChannel();
        Assert.assertNotNull(resp);
        Assert.assertTrue(resp.getGrpcMessageString().contains("\"theme\": \"Hello server"));
    }

    @Test(
            expectedExceptions = IllegalArgumentException.class,
            expectedExceptionsMessageRegExp = "Could not extract service from bookstoreBookstore.")
    public void testThrowExceptionWithInvalidPackagedName() {
        GrpcRequestConfig grpcRequestConfig =
                new GrpcRequestConfig(
                        HOST_PORT,
                        PROTO_WITH_EXTERNAL_IMPORT_FOLDER.toString(),
                        LIB_FOLDER.toString(),
                        "bookstoreBookstore./CreateShelf",
                        false,
                        false,
                        DEFAULT_CHANNEL_SHUTDOWN_TIME);
        clientCaller = new ClientCaller(grpcRequestConfig);
        ImmutableList<DynamicMessage> req = clientCaller.buildRequestAndMetadata(REQUEST_JSON, METADATA);
        GrpcResponse resp = clientCaller.call("10", req);
        clientCaller.shutdownNettyChannel();
        Assert.assertNotNull(resp);
        Assert.assertTrue(resp.getGrpcMessageString().contains("\"theme\": \"Hello server"));
    }

    @Test(
            expectedExceptions = IllegalArgumentException.class,
            expectedExceptionsMessageRegExp =
                    "Could not extract method name from bookstore.Bookstore/")
    public void testThrowExceptionWithInvalidMethodNameWithDoubleSlash() {
        GrpcRequestConfig grpcRequestConfig =
                new GrpcRequestConfig(
                        HOST_PORT,
                        PROTO_WITH_EXTERNAL_IMPORT_FOLDER.toString(),
                        LIB_FOLDER.toString(),
                        "bookstore.Bookstore/",
                        false,
                        false,
                        DEFAULT_CHANNEL_SHUTDOWN_TIME);
        clientCaller = new ClientCaller(grpcRequestConfig);
        ImmutableList<DynamicMessage> req = clientCaller.buildRequestAndMetadata(REQUEST_JSON, METADATA);
        GrpcResponse resp = clientCaller.call("10", req);
        clientCaller.shutdownNettyChannel();
        Assert.assertNotNull(resp);
        Assert.assertTrue(resp.getGrpcMessageString().contains("\"theme\": \"Hello server"));
    }

    @Test(
            expectedExceptions = IllegalArgumentException.class,
            expectedExceptionsMessageRegExp = "Unable to find service with name: Bookstores")
    public void testThrowExceptionWithInvalidServiceName() {
        GrpcRequestConfig grpcRequestConfig =
                new GrpcRequestConfig(
                        HOST_PORT,
                        PROTO_WITH_EXTERNAL_IMPORT_FOLDER.toString(),
                        LIB_FOLDER.toString(),
                        "bookstore.Bookstores/CreateShelf",
                        false,
                        false,
                        DEFAULT_CHANNEL_SHUTDOWN_TIME);
        clientCaller = new ClientCaller(grpcRequestConfig);
        clientCaller.buildRequestAndMetadata(REQUEST_JSON, METADATA);
    }
}
