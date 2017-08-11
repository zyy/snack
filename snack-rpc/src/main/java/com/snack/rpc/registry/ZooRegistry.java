package com.snack.rpc.registry;

import com.snack.rpc.RpcServer;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.retry.ExponentialBackoffRetry;
import org.apache.curator.x.discovery.ServiceDiscovery;
import org.apache.curator.x.discovery.ServiceDiscoveryBuilder;
import org.apache.curator.x.discovery.ServiceInstance;
import org.apache.curator.x.discovery.ServiceType;
import org.apache.curator.x.discovery.details.JsonInstanceSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Collection;
import java.util.Enumeration;
import java.util.regex.Pattern;

/**
 * Created by yangyang.zhao on 2017/8/8.
 */
public class ZooRegistry {
    private static final Logger logger = LoggerFactory.getLogger(ZooRegistry.class);
    private CuratorFramework client = null;
    private ServiceDiscovery<InstanceDetails> serviceDiscovery = null;
    private static ZooRegistry instance = new ZooRegistry();
    private static String innerHostIp = null;
    private static Pattern ipPattern = Pattern.compile("^([0-9]{1,3}\\.){3}[0-9]{1,3}$");
    private static Pattern privateIpPattern = Pattern.compile("(^127\\.0\\.0\\.1)|(^10(\\.[0-9]{1,3}){3}$)|(^172\\.1[6-9](\\.[0-9]{1,3}){2}$)|(^172\\.2[0-9](\\.[0-9]{1,3}){2}$)|(^172\\.3[0-1](\\.[0-9]{1,3}){2}$)|(^192\\.168(\\.[0-9]{1,3}){2}$)");

    public static ZooRegistry getInstance(){
        return instance;
    }

    private ZooRegistry() {
        String connectString = RpcServer.getConfig().getString("zookeeper.connectString");
        String basePath = RpcServer.getConfig().getString("zookeeper.basePath");

        client = CuratorFrameworkFactory.newClient(connectString, new ExponentialBackoffRetry(1000, 3));
        client.start();

        JsonInstanceSerializer<InstanceDetails> serializer = new JsonInstanceSerializer<>(InstanceDetails.class);
        serviceDiscovery = ServiceDiscoveryBuilder.builder(InstanceDetails.class).client(client).basePath(basePath).serializer(serializer).build();
        try {
            serviceDiscovery.start();
        } catch (Exception e) {
            logger.error("Failed to create ZooRegistry! msg=" + e.getMessage(), e);
        }
    }

    public void registerService(String serviceName, int port) throws Exception {
        String localIp = getInnerHostIp();
        String id = localIp + ":" + port;
        ServiceInstance<InstanceDetails> service = ServiceInstance.<InstanceDetails>builder()
                .name(serviceName)
                .address(getInnerHostIp())
                .port(port)
                .id(id)
                .serviceType(ServiceType.DYNAMIC)
                .payload(new InstanceDetails(id, localIp, port, serviceName)).build();

        serviceDiscovery.registerService(service);
        logger.info("registerService, serviceName = {}, port = {}", serviceName, port);
    }

    public void unregisterService(String serviceName, int port) throws Exception {
        String localIp = getInnerHostIp();
        String id = serviceName.substring(serviceName.lastIndexOf('.') + 1) + ":" + localIp + ":" + port;
        ServiceInstance<InstanceDetails> service = ServiceInstance.<InstanceDetails>builder()
                .name(serviceName)
                .address(getInnerHostIp())
                .port(port)
                .id(id)
                .serviceType(ServiceType.DYNAMIC)
                .payload(new InstanceDetails(id, localIp, port, serviceName)).build();

        serviceDiscovery.unregisterService(service);
        logger.info("unregisterService, serviceName = {}, port = {}", serviceName, port);
    }

    public Collection<ServiceInstance<InstanceDetails>> queryForInstances(String serviceName) throws Exception{
        return serviceDiscovery.queryForInstances(serviceName);
    }

    public Collection<String> queryForNames() throws Exception{
        return serviceDiscovery.queryForNames();
    }

    private static String getInnerHostIp() {
        if (innerHostIp == null) {
            try {
                Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
                while (interfaces.hasMoreElements()) {
                    NetworkInterface networkInterface = interfaces.nextElement();
                    // filters out 127.0.0.1 and inactive if
                    if (networkInterface.isLoopback() || !networkInterface.isUp())
                        continue;

                    Enumeration<InetAddress> addresses = networkInterface.getInetAddresses();
                    while (addresses.hasMoreElements()) {
                        InetAddress addr = addresses.nextElement();
                        String tempIp = addr.getHostAddress();
                        // find private ip.
                        if (ZooRegistry.ipPattern.matcher(tempIp).matches()
                                && ZooRegistry.privateIpPattern.matcher(tempIp).matches()) {
                            innerHostIp = tempIp;
                            break;
                        }
                    }
                }
            } catch (SocketException e) {
                throw new RuntimeException(e);
            }
        }
        return innerHostIp;
    }
}
