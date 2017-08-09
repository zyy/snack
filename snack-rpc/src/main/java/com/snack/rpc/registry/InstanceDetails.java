package com.snack.rpc.registry;

import org.codehaus.jackson.map.annotate.JsonRootName;
import lombok.Getter;
import lombok.Setter;

/**
 * Created by yangyang.zhao on 2017/8/3.
 */
@JsonRootName("details")
@Getter
@Setter
public class InstanceDetails {
    private String id;
    private String listenAddress;
    private int listenPort;
    private String interfaceName;

    public InstanceDetails(String id, String listenAddress, int listenPort, String interfaceName) {
        this.id = id;
        this.listenAddress = listenAddress;
        this.listenPort = listenPort;
        this.interfaceName = interfaceName;
    }

    public InstanceDetails() {
    }
}
