package org.ivy.settlement.core.chain.network.impl;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.google.protobuf.BytesValue;
import io.libp2p.core.Libp2pExceptions;
import io.libp2p.core.dsl.BasicBuilder;
import io.libp2p.core.host.Host;
import io.libp2p.core.multiformats.Multiaddr;
import io.libp2p.core.multiformats.Protocol;
import io.libp2p.core.multistream.ProtocolBinding;
import io.libp2p.core.network.Stream;
import io.libp2p.core.peer.AddrInfo;
import io.libp2p.core.peer.PeerId;
import io.libp2p.core.pubsub.pb.RpcProto;
import io.libp2p.core.utils.Pair;
import io.libp2p.core.utils.collections.ConcurrentSegmentMap;
import io.libp2p.protocol.kad.DHT;
import io.libp2p.protocol.kad.config.DHTConfig;
import io.libp2p.protocol.rpc.ProtobufRpcProtocolSender;
import io.libp2p.pubsub.ntree.NTree;
import io.libp2p.pubsub.ntree.NTreeRouter;
import io.libp2p.security.secio.SecIoSecureChannel;
import io.libp2p.transport.tcp.TcpTransport;
import org.ivy.settlement.core.chain.config.IrisCoreSystemConfig;
import org.ivy.settlement.core.chain.network.protocols.IvySettlementMsgCodec;
import org.ivy.settlement.core.chain.network.protocols.MessageDuplexDispatcher;
import org.ivy.settlement.core.chain.network.protocols.base.Message;
import org.ivy.settlement.core.chain.network.protocols.base.RemotingMessageType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiConsumer;
import java.util.function.Function;

import static io.libp2p.core.crypto.keys.Secp256k1.unmarshalSecp256k1PrivateKey;
import static io.libp2p.core.dsl.IntegrationBuilder.integrationHost;

/**
 * description:
 * @author carrot
 */
class P2pNetworkManger {

    static final Logger logger = LoggerFactory.getLogger("network");

    static AtomicLong Counter = new AtomicLong(0);

    static Function<Void, Long> seqIdGenerator = unused -> Counter.incrementAndGet();

    IrisCoreSystemConfig config;

    Host host;

    Function<PeerId, Boolean> peerFilter;

    DHT dht;

    NTreeRouter gossip;

    IvyProtocolRpc ivyProtocolRpc;

    protected ConcurrentSegmentMap<Pair<PeerId, Long>, Stream> peerToStreamsMap;

    List<AddrInfo> bootAddrInfos;

    Set<PeerId> currentValidatorNodes;
    Set<PeerId> preValidatorNodes;


    public P2pNetworkManger(IrisCoreSystemConfig config) {
        var privateKey = unmarshalSecp256k1PrivateKey(config.getMyKey().getRawPrivKeyBytes());
        var threadFactory = new ThreadFactoryBuilder().setThreadFactory(Thread.ofVirtual().factory()).setDaemon(true).setNameFormat("NTreeRouter-event-thread-%d").build();

        BiConsumer<RpcProto.Message, PeerId> msgHandler = (message, peerId) -> processMessage(IvySettlementMsgCodec.decode(peerId, message.getData()));

        gossip = new NTreeRouter(privateKey, msgHandler, message -> true, seqIdGenerator, true, new ScheduledThreadPoolExecutor(1, threadFactory));

        this.peerFilter = this::doFilter;

        this.host = integrationHost(BasicBuilder.Defaults.Standard, builder -> {
            builder.identity(identityBuilder -> identityBuilder.factory = () -> privateKey);
            builder.tcpTransports(functions -> functions.add(TcpTransport::new));
            builder.secureChannels(inputSecureChannels -> inputSecureChannels.add(SecIoSecureChannel::new));
            builder.resourceConfig(rc -> rc.maxInBoundConnection(1024 * 8).maxInBoundConnectionPerPeer(8).maxOutBoundConnection(1024 * 8).maxOutBoundConnectionPerPeer(32));
            builder.network(networkConfigBuilder -> networkConfigBuilder.listen("/ip4/" + config.bindIp() + "/tcp/" + config.listenRpcPort()));
            builder.protocols(protocolBindings -> protocolBindings.add(new NTree(this.gossip, this.peerFilter)));
            builder.enableNat(false);
            builder.ping(false);
        });

        this.config = config;
        this.ivyProtocolRpc = new IvyProtocolRpc();
        this.peerToStreamsMap = new ConcurrentSegmentMap<>(256);
    }

    public void start() {
        try {
            this.host.start().get();
            this.dht = buildDHT();
            //waitAnyOne();
            logger.info("p2p[{}] network manager start success!", this.host.getPeerId());
        } catch (Exception e) {
            logger.error("network start error!", e);
            throw new RuntimeException(e);
        }
    }

    public void stop() {
        try {
            this.host.stop().get();
            this.dht.stop();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private DHT buildDHT() {
        var dhtConfig = new DHTConfig();
        dhtConfig.mode(DHTConfig.ModeOpt.ModeServer);
        dhtConfig.disableProviders();
        dhtConfig.disableValues();
        bootAddrInfos = this.config.getBootNodes()
                .stream()
                .map(b -> new AddrInfo(PeerId.fromHex(b.getPeerId()), List.of(toMultiaddr(new InetSocketAddress(b.getHost(), b.getPort())))))
                .toList();

        dhtConfig.bootstrapPeers(bootAddrInfos);

        //for check the response of getting closet peers
        dhtConfig.queryPeerFilter(this.peerFilter);

        //for schedule check all routing table peer
        dhtConfig.routingTablePingAndEvictFilter(this.peerFilter);

        //for check found peer
        dhtConfig.routingTableFilter((o, peerId) -> doFilter(peerId));
        dhtConfig.lookupCheckConcurrency(128);
        dhtConfig.bucketSize(16);
        return new DHT(this.host, dhtConfig);
    }

    private boolean doFilter(PeerId peerId) {
        return false;
    }

    public void directSend(List<PeerId> receiveNodes, Message msg) {
        msg.setRemoteType(RemotingMessageType.DIRECT_REQUEST_MESSAGE.getType());
        receiveNodes.forEach(pid -> this.ivyProtocolRpc.invokeOneway(pid, BytesValue.newBuilder().setValue(IvySettlementMsgCodec.encode(msg)).build()));
    }

    public void broadcast(Message msg) {
        msg.setRemoteType(RemotingMessageType.DIRECT_REQUEST_MESSAGE.getType());
        this.gossip.broadcast(IvySettlementMsgCodec.encode(msg));
    }

    public Message rpcRequest(PeerId to, Message msg, long timeout) {
        msg.setRpcId();
        msg.setRemoteType(RemotingMessageType.RPC_REQUEST_MESSAGE.getType());
        var bv = BytesValue.newBuilder().setValue(IvySettlementMsgCodec.encode(msg)).build();
        try {
            var resp = this.ivyProtocolRpc.invokeAsync(to, bv).get(timeout, TimeUnit.MILLISECONDS);
            return IvySettlementMsgCodec.decode(to, resp);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public void rpcResponse(PeerId to, Message msg) {
        msg.setRemoteType(RemotingMessageType.RPC_RESPONSE_MESSAGE.getType());
        var bv = BytesValue.newBuilder().setValue(IvySettlementMsgCodec.encode(msg)).build();
        var stream = this.peerToStreamsMap.get(Pair.of(to, msg.getRpcId()));
        if (stream == null) throw new RuntimeException("un valid response msg:" + msg);
        stream.writeAndFlush(bv);
    }

    private void processMessage(Message msg) {
        switch (msg.getMessageType()) {
            case CONSENSUS -> MessageDuplexDispatcher.putConsensusMsg(msg);
            case LAYER_2_STATE_SYNC -> MessageDuplexDispatcher.putLayer2StateSyncMsg(msg);
            case FAST_PATH -> MessageDuplexDispatcher.putFastPathMsg(msg);
            case SLOW_PATH -> MessageDuplexDispatcher.putSlowPathMsg(msg);
            default -> {}
        }
    }

    public void updateEligibleNodes(Set<PeerId> validatorNodes) {
        if (this.currentValidatorNodes == null) {
            this.currentValidatorNodes = validatorNodes;
            this.preValidatorNodes = validatorNodes;
        } else {
            this.preValidatorNodes = this.currentValidatorNodes;
            this.currentValidatorNodes = validatorNodes;
        }
    }

    private void waitAnyOne() {
        while (true) {
            for (var ai : this.bootAddrInfos) {
                if (this.dht.rt.find(ai.id) != null) {
                    return;
                }
            }
            try {
                Thread.sleep(1000);
                this.dht.fixLowPeers();
            } catch (Exception e) {
            }
        }
    }

    class IvyProtocolRpc extends ProtobufRpcProtocolSender<BytesValue> {

        public IvyProtocolRpc() {
            super(BytesValue.getDefaultInstance(), P2pNetworkManger.this.host, ProtocolBinding.BindingDirection.DUPLEX, List.of("/ivy_protocol_rpc"), 3);
        }

        @Override
        protected void responderProcessReq(Stream stream, BytesValue req) {
            var from = stream.remotePeerId();
            var msg = IvySettlementMsgCodec.decode(from, req);
            try {
                P2pNetworkManger.this.peerToStreamsMap.put(Pair.of(from, msg.getRpcId()), stream);
                P2pNetworkManger.this.processMessage(msg);
            } catch (Exception e) {
                P2pNetworkManger.this.peerToStreamsMap.remove(Pair.of(from, msg.getRpcId()));
            }
        }
    }

    static Multiaddr toMultiaddr(InetSocketAddress addr) {
        Protocol protocol;
        if (addr.getAddress() instanceof Inet4Address) {
            protocol = Protocol.IP4;
        } else if (addr.getAddress() instanceof Inet6Address) {
            protocol = Protocol.IP6;
        } else {
            throw new Libp2pExceptions.InternalErrorException("Unknown address type " + addr);
        }

        return new Multiaddr(new ArrayList<>())
                .withComponent(protocol, addr.getAddress().getHostAddress())
                .withComponent(Protocol.TCP, addr.getPort() + "");
    }
}
