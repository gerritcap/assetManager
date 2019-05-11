package be.marble.sling.assetmanager;

import javax.management.StandardMBean;

import org.osgi.framework.Constants;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Modified;
import org.osgi.service.metatype.annotations.Designate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component(name = "marble.be Asset Manager Service", service = AssetManager.class,
        property = { "jmx.objectname=be.marble.sling:type=assets,name=AssetManager",
                     Constants.SERVICE_VENDOR + "=Marble Consulting (marble.be)" })
@Designate(ocd = AssetManagerConfiguration.class)
public class AssetManagerImpl extends StandardMBean implements AssetManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(AssetManagerImpl.class);

    private String              username;

    private String              password;

    private String[]            assetPrefixes;

    private String[]            contentPaths;

    private String              directoryName;

    private Operation           operation;

    public AssetManagerImpl() {
        super(AssetManager.class, true);
    }

    @Activate
    @Modified
    protected void activate(final AssetManagerConfiguration config) {
        this.username = config.getUsername();
        this.password = config.getPassword();
        this.assetPrefixes = config.getAssetPrefixPaths();
        this.contentPaths = config.getContentPaths();
        this.directoryName = config.getDirectoryName();
        this.operation = Operation.fromOptionValue(config.getOperation());
    }

    @Override
    public void run() {
        this.run(this.operation);
    }

    @Override
    public void run(final String operation) {
        final Operation op = Operation.fromOptionValue(operation);
        this.run(op);
    }

    private void run(final Operation op) {
        LOGGER.info("Running operation {}", op);
    }

}
