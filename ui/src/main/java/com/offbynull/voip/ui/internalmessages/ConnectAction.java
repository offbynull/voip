package com.offbynull.voip.ui.internalmessages;

import org.apache.commons.lang3.Validate;

public final class ConnectAction {
    private final String username;
    private final String bootstrap;

    public ConnectAction(String username, String bootstrap) {
        Validate.notNull(username);
        Validate.notNull(bootstrap);
        this.username = username;
        this.bootstrap = bootstrap;
    }

    public String getUsername() {
        return username;
    }

    public String getBoostrap() {
        return bootstrap;
    }
    
}
