package io.stargate.cql;

import java.net.InetAddress;
import java.net.UnknownHostException;

import org.apache.cassandra.config.Config;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.framework.ServiceEvent;
import org.osgi.framework.ServiceListener;
import org.osgi.framework.ServiceReference;

import io.stargate.cql.impl.CqlImpl;
import io.stargate.coordinator.Coordinator;

public class CqlActivator implements BundleActivator, ServiceListener {
    private BundleContext context;
    private final CqlImpl cql = new CqlImpl(makeConfig());
    private ServiceReference reference;

    private static Config makeConfig()
    {
        try
        {
            String listenAddress = System.getProperty("stargate.listen_address", InetAddress.getLocalHost().getHostAddress());
            Integer cqlPort = Integer.getInteger("stargate.cql_port", 9042);

            Config c = new Config();

            c.rpc_address = listenAddress;
            c.native_transport_port = cqlPort;

            return c;
        } catch (UnknownHostException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void start(BundleContext context) throws Exception {
        this.context = context;
        System.out.println("Starting CQL....");
        synchronized (cql) {
            try {
                context.addServiceListener(this, "(objectClass=io.stargate.coordinator.Coordinator)");
            } catch (InvalidSyntaxException ise) {
                throw new RuntimeException(ise);
            }
            reference = context.getServiceReference(Coordinator.class.getName());
            if (reference != null) {
                Object service = context.getService(reference);
                if (service != null) {
                    this.cql.start(((Coordinator) service).getPersistence());
                    System.out.println("Started CQL....");
                }
            }
        }
    }

    @Override
    public void stop(BundleContext context) {
        context.ungetService(reference);
    }

    @Override
    public void serviceChanged(ServiceEvent serviceEvent) {
        int type = serviceEvent.getType();
        String[] objectClass = (String[]) serviceEvent.getServiceReference().getProperty("objectClass");
        synchronized (cql) {
            switch (type) {
                case (ServiceEvent.REGISTERED):
                    System.out.println("Service of type " + objectClass[0] + " registered.");
                    reference = serviceEvent.getServiceReference();
                    Object service = context.getService(reference);

                    System.out.println("Setting coordinator in CqlActivator");
                    this.cql.start(((Coordinator)service).getPersistence());
                    System.out.println("Started CQL....");
                    break;
                case (ServiceEvent.UNREGISTERING):
                    System.out.println("Service of type " + objectClass[0] + " unregistered.");
                    context.ungetService(serviceEvent.getServiceReference());
                    break;
                case (ServiceEvent.MODIFIED):
                    // TODO: [doug] 2020-06-15, Mon, 12:58 do something here...
                    System.out.println("Service of type " + objectClass[0] + " modified.");
                    break;
                default:
                    break;
            }
        }
    }
}
