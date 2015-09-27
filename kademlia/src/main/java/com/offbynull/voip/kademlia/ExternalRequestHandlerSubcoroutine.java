package com.offbynull.voip.kademlia;

import com.offbynull.coroutines.user.Continuation;
import com.offbynull.peernetic.core.actor.Context;
import com.offbynull.peernetic.core.actor.helpers.AddressTransformer;
import com.offbynull.peernetic.core.actor.helpers.Subcoroutine;
import static com.offbynull.peernetic.core.gateways.log.LogMessage.info;
import com.offbynull.peernetic.core.shuttle.Address;
import com.offbynull.voip.kademlia.externalmessages.FindRequest;
import com.offbynull.voip.kademlia.externalmessages.FindResponse;
import com.offbynull.voip.kademlia.externalmessages.KademliaRequest;
import com.offbynull.voip.kademlia.externalmessages.PingRequest;
import com.offbynull.voip.kademlia.externalmessages.PingResponse;
import com.offbynull.voip.kademlia.model.Id;
import com.offbynull.voip.kademlia.model.Node;
import com.offbynull.voip.kademlia.model.Router;
import com.offbynull.voip.kademlia.model.RouterChangeSet;
import java.util.List;
import org.apache.commons.lang3.Validate;

final class ExternalRequestHandlerSubcoroutine implements Subcoroutine<Void> {

    private final Address subAddress;
    private final Address logAddress;
    private final AddressTransformer addressTransformer;
    
    private final Id baseId;
    private final Router router;
    
    private final GraphHelper graphHelper;

    public ExternalRequestHandlerSubcoroutine(Address subAddress, State state) {
        Validate.notNull(subAddress);
        Validate.notNull(state);
        this.subAddress = subAddress;
        this.logAddress = state.getLogAddress();
        this.addressTransformer = state.getAddressTransformer();
        
        this.baseId = state.getBaseId();
        this.router = state.getRouter();
        
        this.graphHelper = state.getGraphHelper();
    }

    @Override
    public Address getAddress() {
        return subAddress;
    }

    @Override
    public Void run(Continuation cnt) throws Exception {
        Context ctx = (Context) cnt.getContext();

        while (true) {
            cnt.suspend();
            
            Object msg = ctx.getIncomingMessage();

            if (!(msg instanceof KademliaRequest)) {
                ctx.addOutgoingMessage(subAddress, logAddress, info("Incorrect message type ignored: {}", msg));
                continue;
            }
            
            KademliaRequest baseReq = (KademliaRequest) msg;
            Id srcId = baseReq.getFromId();
            if (srcId != null && !srcId.equals(baseId)) { // If sending node has provided their ID + sending node is not ourself
                // Message is from an established node that can be routed to, so touch our routing table with it...
                String srcLink = addressTransformer.toLinkId(ctx.getSource());
                Node srcNode = new Node(srcId, srcLink);

                RouterChangeSet routerChangeSet = router.touch(ctx.getTime(), srcNode);
                graphHelper.applyRouterChanges(ctx, routerChangeSet);
            }
            
            if (msg instanceof PingRequest) {
                ctx.addOutgoingMessage(subAddress, logAddress, info("Incoming ping request from {}", ctx.getSource()));
                
                PingResponse resp = new PingResponse();
                ctx.addOutgoingMessage(subAddress, ctx.getSource(), resp);
                ctx.addOutgoingMessage(subAddress, logAddress, info("Responding with: {}", resp));
            } else if (msg instanceof FindRequest) {
                ctx.addOutgoingMessage(subAddress, logAddress, info("Incoming find request from {}", ctx.getSource()));

                FindRequest req = (FindRequest) msg;
                Id findId = req.getFindId();
                List<Node> foundNodes = router.find(findId, 20);

                FindResponse resp = new FindResponse(foundNodes.toArray(new Node[foundNodes.size()]));
                ctx.addOutgoingMessage(subAddress, ctx.getSource(), resp);
                ctx.addOutgoingMessage(subAddress, logAddress, info("Responding with closest nodes: {}", foundNodes));
            } else {
                ctx.addOutgoingMessage(subAddress, logAddress, info("Unknown request from {}: {}", ctx.getSource(), msg));
            }
        }
    }

}