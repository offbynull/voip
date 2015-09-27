package com.offbynull.voip.kademlia;

import com.offbynull.peernetic.core.actor.ActorRunner;
import static com.offbynull.peernetic.core.actor.helpers.IdGenerator.MIN_SEED_SIZE;
import com.offbynull.peernetic.core.gateways.log.LogGateway;
import com.offbynull.peernetic.core.gateways.timer.TimerGateway;
import com.offbynull.peernetic.core.shuttle.Address;
import com.offbynull.peernetic.core.actor.helpers.SimpleAddressTransformer;
import com.offbynull.peernetic.core.gateways.direct.DirectGateway;
import com.offbynull.peernetic.visualizer.gateways.graph.GraphGateway;
import com.offbynull.voip.kademlia.internalmessages.SearchRequest;
import com.offbynull.voip.kademlia.internalmessages.SearchResponse;
import com.offbynull.voip.kademlia.internalmessages.Start;
import com.offbynull.voip.kademlia.model.BitString;
import com.offbynull.voip.kademlia.model.Id;
import com.offbynull.voip.kademlia.model.Node;
import com.offbynull.voip.kademlia.model.RouteTreeBranchSpecificationSupplier;
import com.offbynull.voip.kademlia.model.RouteTreeBucketSpecificationSupplier;
import com.offbynull.voip.kademlia.model.RouteTreeBucketSpecificationSupplier.BucketParameters;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import org.apache.commons.io.Charsets;

public final class ManualTest {

    private static final String BASE_ACTOR_ADDRESS_STRING = "actor";
    private static final String BASE_GRAPH_ADDRESS_STRING = "graph";
    private static final String BASE_TIMER_ADDRESS_STRING = "timer";
    private static final String BASE_DIRECT_ADDRESS_STRING = "direct";
    private static final String BASE_LOG_ADDRESS_STRING = "log";

    private static final Address BASE_ACTOR_ADDRESS = Address.of(BASE_ACTOR_ADDRESS_STRING);
    private static final Address BASE_GRAPH_ADDRESS = Address.of(BASE_GRAPH_ADDRESS_STRING);
    private static final Address BASE_TIMER_ADDRESS = Address.of(BASE_TIMER_ADDRESS_STRING);
    private static final Address BASE_DIRECT_ADDRESS = Address.of(BASE_DIRECT_ADDRESS_STRING);
    private static final Address BASE_LOG_ADDRESS = Address.of(BASE_LOG_ADDRESS_STRING);

    public static void main(String[] args) throws Exception {
        GraphGateway.startApplication();
        
        GraphGateway graphGateway = new GraphGateway(BASE_GRAPH_ADDRESS_STRING);
        TimerGateway timerGateway = new TimerGateway(BASE_TIMER_ADDRESS_STRING);
        DirectGateway directGateway = new DirectGateway(BASE_DIRECT_ADDRESS_STRING);
        LogGateway logGateway = new LogGateway(BASE_LOG_ADDRESS_STRING);
        ActorRunner actorRunner = new ActorRunner(BASE_ACTOR_ADDRESS_STRING);

        
        graphGateway.addStage(() -> new ConsoleStage());
        ConsoleStage consoleStage = ConsoleStage.getInstance();
        
        
        timerGateway.addOutgoingShuttle(actorRunner.getIncomingShuttle());
        directGateway.addOutgoingShuttle(actorRunner.getIncomingShuttle());

        actorRunner.addOutgoingShuttle(timerGateway.getIncomingShuttle());
        actorRunner.addOutgoingShuttle(directGateway.getIncomingShuttle());
        actorRunner.addOutgoingShuttle(graphGateway.getIncomingShuttle());
        actorRunner.addOutgoingShuttle(logGateway.getIncomingShuttle());

        // Seed node
        addNode("1111", null, actorRunner);

        // Connecting nodes
        addNode("0000", "1111", actorRunner);
        addNode("0001", "1111", actorRunner);
        addNode("0010", "1111", actorRunner);
        addNode("0011", "1111", actorRunner);
        addNode("0100", "1111", actorRunner);
        addNode("0101", "1111", actorRunner);
        addNode("0110", "1111", actorRunner);
        addNode("0111", "1111", actorRunner);
        addNode("1000", "1111", actorRunner);
        addNode("1001", "1111", actorRunner);
        addNode("1010", "1111", actorRunner);
        addNode("1011", "1111", actorRunner);
        addNode("1100", "1111", actorRunner);
        addNode("1101", "1111", actorRunner);
        addNode("1110", "1111", actorRunner);
//        addNode("1111", "1111", actorRunner);

        

        // Search for nodes based on input
        consoleStage.outputLine("Wait until 1111 has loaded before querying...");
        while (true) {
            ArrayBlockingQueue<String> outputQueue = new ArrayBlockingQueue<>(1);
            consoleStage.outputLine("Enter node to search for");
            consoleStage.setCommandProcessor((input) -> {
                outputQueue.add(input);
                return "Querying " + input + " nodes";
            });
            String searchId = outputQueue.take();
            
            
            SearchRequest req = new SearchRequest(Id.create(searchId), 1);
            directGateway.writeMessage(Address.of("actor", "1111", "router", "internalhandler"), req);
            
            SearchResponse resp = (SearchResponse) directGateway.readMessages().get(0).getMessage();
            List<Node> foundNodes = Arrays.asList(resp.getNodes());
            
            consoleStage.outputLine(foundNodes.toString());
        }
    }

    private static void addNode(String idStr, String bootstrapIdStr, ActorRunner actorRunner) {
        Id id = Id.create(idStr);
        Node bootstrapNode = bootstrapIdStr == null ? null : new Node(Id.create(bootstrapIdStr), bootstrapIdStr);

        byte[] seed1 = Arrays.copyOf(idStr.getBytes(Charsets.US_ASCII), MIN_SEED_SIZE);
        byte[] seed2 = Arrays.copyOf(idStr.getBytes(Charsets.US_ASCII), MIN_SEED_SIZE);

        actorRunner.addActor(
                idStr,
                new KademliaCoroutine(),
                new Start(
                        new SimpleAddressTransformer(BASE_ACTOR_ADDRESS),
                        id,
                        bootstrapNode,
//                        new KademliaParameters(id, 2, 20, 20, 20, 3),
                        new Start.KademliaParameters(
                                // only 2 branches in routing tree 1xx and 0xx
                                () -> new RouteTreeBranchSpecificationSupplier() {

                                    @Override
                                    public int getBranchCount(BitString prefix) {
                                        if (prefix.getBitLength() == 0) {
                                            return 2;
                                        } else {
                                            return 0;
                                        }
                                    }
                                },
                                // only 1 node per bucket, 0 cache per bucket
                                () -> new RouteTreeBucketSpecificationSupplier() {
                                    @Override
                                    public RouteTreeBucketSpecificationSupplier.BucketParameters getBucketParameters(BitString prefix) {
                                        if (prefix.getBitLength() == 1) {
                                            return new BucketParameters(1, 0);
                                        } else {
                                            throw new IllegalArgumentException();
                                        }
                                    }
                                },
                                1, // 1 node in near bucket
                                1), // 1 find concurrency request
                        seed1,
                        seed2,
                        BASE_TIMER_ADDRESS,
                        BASE_GRAPH_ADDRESS,
                        BASE_LOG_ADDRESS
                )
        );
    }
}