package com.offbynull.voip.kademlia;

import com.offbynull.peernetic.core.actor.Context;
import com.offbynull.peernetic.core.shuttle.Address;
import com.offbynull.peernetic.visualizer.gateways.graph.AddEdge;
import com.offbynull.peernetic.visualizer.gateways.graph.AddNode;
import com.offbynull.peernetic.visualizer.gateways.graph.LabelNode;
import com.offbynull.peernetic.visualizer.gateways.graph.MoveNode;
import com.offbynull.peernetic.visualizer.gateways.graph.StyleNode;
import com.offbynull.voip.kademlia.model.Activity;
import com.offbynull.voip.kademlia.model.BitString;
import com.offbynull.voip.kademlia.model.Id;
import com.offbynull.voip.kademlia.model.IdClosenessComparator;
import com.offbynull.voip.kademlia.model.NearBucketChangeSet;
import com.offbynull.voip.kademlia.model.Node;
import com.offbynull.voip.kademlia.model.RouteTreeChangeSet;
import com.offbynull.voip.kademlia.model.Router;
import com.offbynull.voip.kademlia.model.RouterChangeSet;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.StringJoiner;
import java.util.TreeSet;
import org.apache.commons.lang3.Validate;

final class GraphHelper {
    
    private static final String CLOSEST_NODE_ID = "CLOSEST_NODE_SPECIAL_ID";
    private static final double Y_SPREAD = 50.0;
    private static final double X_SPREAD = 50.0;
    
    
    private final Address graphAddress;
    
    private final Id baseId;
    private final LinkedHashSet<BitString> routerBucketPrefixes;
    private final LinkedHashMap<BitString, SortedSet<Id>> routeTreePrefixToIds; // ids assigned to each tree prefix
    private final TreeSet<Id> nearBucketIds;

    public GraphHelper(Id baseId, Address graphAddress, Router router) {
        Validate.notNull(baseId);
        Validate.notNull(graphAddress);
        Validate.notNull(router);
        this.baseId = baseId;
        this.graphAddress = graphAddress.appendSuffix(baseId.getBitString().toString());
        
        List<BitString> prefixes = router.dumpBucketPrefixes();
        Collections.sort(prefixes, (x, y) -> Integer.compare(x.getBitLength(), y.getBitLength()));
                
        this.routerBucketPrefixes = new LinkedHashSet<>(); // order matters, smallest prefixes first
        this.routerBucketPrefixes.addAll(prefixes);
        
        this.routeTreePrefixToIds = new LinkedHashMap<>(); // order matters, smallest prefixes first
        
        IdClosenessComparator idComparator = new IdClosenessComparator(baseId);
        this.nearBucketIds = new TreeSet<>(idComparator); // order matters, based on closeness of ids
    }

    public void createGraphs(Context ctx) {
        setupNearBucketGraph(ctx);
        setupRoutingTreeGraph(ctx);
    }
    
    public void applyRouterChanges(Context ctx, RouterChangeSet changeSet) {
        applyNearBucketChanges(ctx, changeSet.getNearBucketChangeSet());
        applyRoutingTreeChanges(ctx, changeSet.getRouteTreeChangeSet());
    }
    
    @SuppressWarnings("unchecked")
    private void applyRoutingTreeChanges(Context ctx, RouteTreeChangeSet changeSet) {
        Set<BitString> updatedPrefixes = new HashSet<>();
        
        // Remove nodes from graph and set
        for (Activity removedNode : changeSet.getKBucketChangeSet().getBucketChangeSet().viewRemoved()) {
            Id id = removedNode.getNode().getId();
            BitString prefix = findPrefixInRouteTreeIds(id);
            routeTreePrefixToIds.get(prefix).remove(id);
            updatedPrefixes.add(prefix);
        }

        // Add nodes to graph and set
        for (Activity addedNode : changeSet.getKBucketChangeSet().getBucketChangeSet().viewAdded()) {
            Id id = addedNode.getNode().getId();
            BitString prefix = findPrefixInRouteTreeIds(id);
            routeTreePrefixToIds.get(prefix).add(id);
            updatedPrefixes.add(prefix);
        }
        
        for (BitString updatedPrefix : updatedPrefixes) {
            // Concatenate to string and set as label
            StringJoiner joiner = new StringJoiner("\n");
            
            // Find parent
            int prefixBitLength = updatedPrefix.getBitLength();
            BitString parentPrefix = getParentPrefix(routeTreePrefixToIds.keySet(), updatedPrefix);
            
            // Calculate number of bits after parent prefix (the bits to display)
            int newBitsOffset = parentPrefix.getBitLength();
            int newBitsLength = prefixBitLength - parentPrefix.getBitLength();
            BitString prefixDisplayBits = updatedPrefix.getBits(newBitsOffset, newBitsLength);
            
            // Add 
            joiner.add(prefixDisplayBits.toString());
            joiner.add("");
            
            routeTreePrefixToIds.get(updatedPrefix).forEach(id -> {
//                BitString suffixIdBits = id.getBitString().getBits(prefixBitLength, id.getBitLength() - prefixBitLength);
//                joiner.add(suffixIdBits.toString());
                joiner.add(id.getBitString().toString());
            });

            ctx.addOutgoingMessage(graphAddress, new LabelNode(updatedPrefix.toString(), joiner.toString()));
        }
    }
    
    private BitString findPrefixInRouteTreeIds(Id id) {
        int len = id.getBitLength();
        BitString routeTreeNode = id.getBitString().getBits(0, len);

        while (!routeTreePrefixToIds.containsKey(routeTreeNode)) {
            len--;
            routeTreeNode = id.getBitString().getBits(0, len);
        }
        
        return routeTreeNode;
    }
    
    private void applyNearBucketChanges(Context ctx, NearBucketChangeSet changeSet) {
        // Remove nodes from set
        for (Node removedNode : changeSet.getBucketChangeSet().viewRemoved()) {
            Id id = removedNode.getId();
            nearBucketIds.remove(id);
        }

        // Add nodes to set
        for (Node addedNode : changeSet.getBucketChangeSet().viewAdded()) {
            Id id = addedNode.getId();
            nearBucketIds.add(id);
        }
        
        // Concatenate set to string and set as label
        StringJoiner joiner = new StringJoiner("\n");
        nearBucketIds.forEach(id -> joiner.add(id.getBitString().toString()));
        
        ctx.addOutgoingMessage(graphAddress, new LabelNode(CLOSEST_NODE_ID, joiner.toString()));
    }
    
    private void setupNearBucketGraph(Context ctx) {        
        ctx.addOutgoingMessage(graphAddress, new AddNode(CLOSEST_NODE_ID));
        ctx.addOutgoingMessage(graphAddress, new LabelNode(CLOSEST_NODE_ID, ""));
        ctx.addOutgoingMessage(graphAddress, new StyleNode(CLOSEST_NODE_ID, 0xFF00FF));
        ctx.addOutgoingMessage(graphAddress, new MoveNode(CLOSEST_NODE_ID, X_SPREAD, -Y_SPREAD)); // move it up and to the right a bit
    }
    
    private void setupRoutingTreeGraph(Context ctx) {        
        IdClosenessComparator idComparator = new IdClosenessComparator(baseId);
        
        Map<BitString, Point> processedPrefixes = new HashMap<>(); // prefix -> position on graph
        addRootToGraph(ctx, processedPrefixes);
        routeTreePrefixToIds.put(BitString.createFromString(""), new TreeSet<>(idComparator)); // special case for empty prefix
     
        LinkedList<BitString> tempPrefixes = new LinkedList<>(routerBucketPrefixes);

        double maxYPosition = Double.MIN_VALUE;
        while (true) {
            // Get next prefixes
            ArrayList<BitString> nextLevelPrefixes = removePrefixesForNextLevel(tempPrefixes);
            if (nextLevelPrefixes.isEmpty()) {
                break;
            }
            
            // Find parent
            int bitLengthOfNextLevelPrefixes = nextLevelPrefixes.get(0).getBitLength();
            BitString parentPrefix = getParentPrefix(processedPrefixes.keySet(), nextLevelPrefixes.get(0));
            Point parentPoint = processedPrefixes.get(parentPrefix);
            
            // Calculate number of bits after parent prefix (the bits to display)
            int newBitsOffset = parentPrefix.getBitLength();
            int newBitsLength = bitLengthOfNextLevelPrefixes - parentPrefix.getBitLength();
            
            // Calculate starting x and y positions
            double numBranches = 1 << newBitsLength;
            double missingBitLength = baseId.getBitLength() - bitLengthOfNextLevelPrefixes;
            double ySpreadAtLevel = Y_SPREAD * (missingBitLength + 1.0);
            double xSpreadAtLevel = X_SPREAD * (missingBitLength + 1.0);
            double yPosition = parentPoint.y + ySpreadAtLevel; // nodes right below the parent
            double xPosition = parentPoint.x - xSpreadAtLevel * (numBranches - 1.0) / 2.0; // nodes x-centered on parent

            // special-case for netLevelPrefixes where prefix for our baseId doesn't exist... this is the branch that falls further down
            //
            // e.g. If you're 000, prefixes will be 1, 01, 001, 000... but for each of those you'll want to show the falling-thru prefix as
            // well.. for example...
            //
            //                                    /\
            //        (NOT STATED IN PREFIXES) 0 /  \ 1
            //                                  /\
            //     (NOT STATED IN PREFIXES) 00 /  \ 01
            //                                /\
            //      (EXISTS IN PREFIXES) 000 /  \ 001
            //
            // Note that 000 exists, but 00 and 0 don't.
            BitString baseIdPortion = baseId.getBitString().getBits(0, bitLengthOfNextLevelPrefixes);
            if (!nextLevelPrefixes.contains(baseIdPortion)) {
                nextLevelPrefixes.add(baseIdPortion);
            }
            
            // Make sure smallest branch is always to the left-most by sorting
            Collections.sort(nextLevelPrefixes,
                    (x, y) -> Long.compare(
                            x.getBitsAsLong(newBitsOffset, newBitsLength),
                            y.getBitsAsLong(newBitsOffset, newBitsLength)
                    )
            );
            
            // Add prefixes from routing tree
            for (BitString nextPrefix : nextLevelPrefixes) {
                routeTreePrefixToIds.put(nextPrefix, new TreeSet<>(idComparator));
                
                addPrefixToGraph(nextPrefix, newBitsOffset, newBitsLength, xPosition, yPosition, ctx, parentPrefix, processedPrefixes);
                xPosition += xSpreadAtLevel;
            }
            
            // Update max Y position
            maxYPosition = Math.max(maxYPosition, yPosition);
        }
    }
    
    private void addRootToGraph(Context ctx, Map<BitString, Point> processedPrefixes) {
        BitString id = BitString.createFromString("");
        ctx.addOutgoingMessage(graphAddress, new AddNode(id.toString()));
        ctx.addOutgoingMessage(graphAddress, new MoveNode(id.toString(), 0.0, 0.0));
        ctx.addOutgoingMessage(graphAddress, new StyleNode(id.toString(), 0x7F7F7F));
        processedPrefixes.put(id, new Point(0.0, 0.0));
    }

    private void addPrefixToGraph(BitString nextPrefix, int newBitsOffset, int newBitsLength, double xPosition, double yPosition,
            Context ctx, BitString parentId, Map<BitString, Point> processedPrefixes) {
        BitString displayBits = nextPrefix.getBits(newBitsOffset, newBitsLength);
        Point displayPoint = new Point(xPosition, yPosition);
        ctx.addOutgoingMessage(graphAddress, new AddNode(nextPrefix.toString()));
        ctx.addOutgoingMessage(graphAddress, new LabelNode(nextPrefix.toString(), displayBits.toString()));
        ctx.addOutgoingMessage(graphAddress, new MoveNode(nextPrefix.toString(), displayPoint.x, displayPoint.y));
        ctx.addOutgoingMessage(graphAddress, new StyleNode(nextPrefix.toString(), 0x7F7F7F));
        ctx.addOutgoingMessage(graphAddress, new AddEdge(parentId.toString(), nextPrefix.toString()));
        processedPrefixes.put(nextPrefix, displayPoint);
    }
    
    private BitString getParentPrefix(Set<BitString> treePrefixes, BitString checkBitString) {
        do {
            checkBitString = checkBitString.getBits(0, checkBitString.getBitLength() - 1);
            if (treePrefixes.contains(checkBitString)) {
                return checkBitString;
            }
        } while (checkBitString.getBitLength() >= 0);
        
        throw new IllegalStateException(); // should never happen
    }
    
    private ArrayList<BitString> removePrefixesForNextLevel(LinkedList<BitString> sortedPrefixes) {
        ArrayList<BitString> ret = new ArrayList<>();
        
        if (sortedPrefixes.isEmpty()) {
            return ret;
        }
        
        int hitCount = sortedPrefixes.peekFirst().getBitLength();
        
        while (!sortedPrefixes.isEmpty()) {
            if (sortedPrefixes.peekFirst().getBitLength() == hitCount) {
                ret.add(sortedPrefixes.removeFirst());
            } else {
                break;
            }
        }
        
        return ret;
    }
    
    private static final class Point {
        private final double x;
        private final double y;

        public Point(double x, double y) {
            this.x = x;
            this.y = y;
        }
        
    }
}
