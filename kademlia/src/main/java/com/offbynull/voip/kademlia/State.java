
package com.offbynull.voip.kademlia;

import com.offbynull.peernetic.core.shuttle.Address;
import com.offbynull.peernetic.core.actor.helpers.AddressTransformer;
import com.offbynull.peernetic.core.actor.helpers.IdGenerator;
import com.offbynull.voip.kademlia.internalmessages.Start.KademliaParameters;
import com.offbynull.voip.kademlia.model.Id;
import com.offbynull.voip.kademlia.model.Router;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.SecureRandom;
import org.apache.commons.lang3.Validate;

final class State {

    private final Address timerAddress;
    private final Address graphAddress;
    private final Address logAddress;
    
    private final IdGenerator idGenerator;
    private final SecureRandom secureRandom;
    
    private final Id baseId;
    private final Router router;
    private final int maxConcurrentRequestsPerFind;
    
    private final GraphHelper graphHelper;
    
    private final AddressTransformer addressTransformer;

    public State(
            Address timerAddress,
            Address graphAddress,
            Address logAddress,
            byte[] seed1,
            byte[] seed2,
            Id baseId,
            KademliaParameters kademliaParameters,
            AddressTransformer addressTransformer) {
        Validate.notNull(timerAddress);
        Validate.notNull(graphAddress);
        Validate.notNull(logAddress);
        Validate.notNull(seed1);
        Validate.notNull(baseId);
        Validate.notNull(kademliaParameters);
        Validate.notNull(addressTransformer);
        Validate.isTrue(seed1.length >= IdGenerator.MIN_SEED_SIZE);
        Validate.isTrue(seed2.length >= IdGenerator.MIN_SEED_SIZE);
        this.timerAddress = timerAddress;
        this.graphAddress = graphAddress;
        this.logAddress = logAddress;
        idGenerator = new IdGenerator(seed1);
        
        try {
            secureRandom = SecureRandom.getInstance("SHA1PRNG", "SUN");
        } catch (NoSuchAlgorithmException | NoSuchProviderException ex) {
            throw new IllegalStateException(ex);
        }
        secureRandom.setSeed(seed2);
        
        this.baseId = baseId;
        this.router = new Router(baseId,
                kademliaParameters.getBranchSpecSupplier().get(),
                kademliaParameters.getBucketSpecSupplier().get(),
                kademliaParameters.getNearBucketSize());
        this.maxConcurrentRequestsPerFind = kademliaParameters.getMaxConcurrentRequestsPerFind();
        
        this.graphHelper = new GraphHelper(baseId, graphAddress, router);
        
        this.addressTransformer = addressTransformer;
    }

    public Address getTimerAddress() {
        return timerAddress;
    }

    public Address getGraphAddress() {
        return graphAddress;
    }

    public Address getLogAddress() {
        return logAddress;
    }

    public IdGenerator getIdGenerator() {
        return idGenerator;
    }

    public SecureRandom getSecureRandom() {
        return secureRandom;
    }

    public Id getBaseId() {
        return baseId;
    }

    public Router getRouter() {
        return router;
    }

    public int getMaxConcurrentRequestsPerFind() {
        return maxConcurrentRequestsPerFind;
    }

    public GraphHelper getGraphHelper() {
        return graphHelper;
    }

    public AddressTransformer getAddressTransformer() {
        return addressTransformer;
    }
}