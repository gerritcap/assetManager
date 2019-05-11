package be.marble.sling.assetmanager;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

import javax.management.StandardMBean;

import org.apache.sling.api.resource.LoginException;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ResourceResolverFactory;
import org.osgi.framework.Constants;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Modified;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.metatype.annotations.Designate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component(name = "be.marble Asset Manager Service", service = AssetManager.class,
        property = { "jmx.objectname=be.marble.sling:type=assets,name=AssetManager",
                     Constants.SERVICE_VENDOR + "=Marble Consulting (marble.be)" })
@Designate(ocd = AssetManagerConfiguration.class)
public class AssetManagerImpl extends StandardMBean implements AssetManager {

    private static final Logger     LOGGER = LoggerFactory.getLogger(AssetManagerImpl.class);

    @Reference
    private ResourceResolverFactory resourceResolverFactory;

    private String                  service;

    private String[]                assetPaths;

    private String[]                assetSuffixes;

    private String[]                contentPaths;

    private String                  directoryName;

    private Operation               operation;

    public AssetManagerImpl() {
        super(AssetManager.class, true);
    }

    @Activate
    @Modified
    protected void activate(final AssetManagerConfiguration config) {
        this.service = config.getService();
        this.assetPaths = config.getAssetPaths();
        this.assetSuffixes = config.getAssetPrefixSuffixes();
        this.contentPaths = config.getContentPaths();
        this.directoryName = config.getDirectoryName();
        this.operation = Operation.fromOptionValue(config.getOperation());
        LOGGER.info("Configured {}", this);
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
        final long start = System.currentTimeMillis();
        ResourceResolver resourceResolver = null;
        try {
            resourceResolver = this.authenticate();
            final Map<String, Boolean> assets = this.findAssets(resourceResolver);
            this.logAssets(assets);
            final long elapsed = System.currentTimeMillis() - start;
            LOGGER.info("Finished running operation {}, elapsed time in milliseconds {}", op, elapsed);
        } catch (final Exception e) {
            final long elapsed = System.currentTimeMillis() - start;
            LOGGER.error("Error in run: (elapsed time: " + elapsed + ")", e);
        } finally {
            if (resourceResolver != null) {
                resourceResolver.close();
            }
        }
    }

    private void logAssets(final Map<String, Boolean> assets) {
        for (final Map.Entry<String, Boolean> assetEntry : assets.entrySet()) {
            LOGGER.info("Asset {} referenced {}", assetEntry.getKey(), assetEntry.getValue());
        }
    }

    private Map<String, Boolean> findAssets(final ResourceResolver resourceResolver) {
        final Map<String, Boolean> assets = new TreeMap<>();
        for (final String assetPath : this.assetPaths) {
            final Resource assetResource = resourceResolver.getResource(assetPath);
            if (assetResource == null) {
                LOGGER.error("Asset path {} not found", assetPath);
            }
            this.addAssetsRecursively(assets, assetResource);
        }
        return assets;
    }

    private void addAssetsRecursively(final Map<String, Boolean> assets, final Resource resource) {
        final String resourceName = resource.getName().toLowerCase();
        for (final String suffix : this.assetSuffixes) {
            if (resourceName.endsWith(suffix)) {
                assets.put(resource.getPath(), Boolean.FALSE);
            }
        }
        for (final Resource child : resource.getChildren()) {
            this.addAssetsRecursively(assets, child);
        }
    }

    private ResourceResolver authenticate() throws LoginException {
        ResourceResolver resourceResolver;
        final Map<String, Object> authenticationInfo = new HashMap<>();
        authenticationInfo.put(ResourceResolverFactory.SUBSERVICE, this.service);
        resourceResolver = this.resourceResolverFactory.getServiceResourceResolver(authenticationInfo);
        return resourceResolver;
    }

    @Override
    public String toString() {
        final StringBuilder builder = new StringBuilder();
        builder.append("AssetManagerImpl [resourceResolverFactory=");
        builder.append(this.resourceResolverFactory);
        builder.append(", service=");
        builder.append(this.service);
        builder.append(", assetPaths=");
        builder.append(Arrays.toString(this.assetPaths));
        builder.append(", assetSuffixes=");
        builder.append(Arrays.toString(this.assetSuffixes));
        builder.append(", contentPaths=");
        builder.append(Arrays.toString(this.contentPaths));
        builder.append(", directoryName=");
        builder.append(this.directoryName);
        builder.append(", operation=");
        builder.append(this.operation);
        builder.append("]");
        return builder.toString();
    }

}
