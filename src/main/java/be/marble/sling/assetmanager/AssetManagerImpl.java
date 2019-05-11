package be.marble.sling.assetmanager;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import javax.management.StandardMBean;

import org.apache.commons.io.IOUtils;
import org.apache.sling.api.resource.LoginException;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ResourceResolverFactory;
import org.apache.sling.api.resource.ValueMap;
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

    private static final Logger     LOGGER   = LoggerFactory.getLogger(AssetManagerImpl.class);

    private static final String     JCR_DATA = "jcr:data";

    private static final Charset    UTF_8    = Charset.forName("UTF-8");

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
            final Set<String> unreferencedAssets = this.findAssets(resourceResolver);
            final Set<String> referencedAssets = new TreeSet<>();
            this.findReferences(resourceResolver, unreferencedAssets, referencedAssets);
            this.logAssets(referencedAssets, unreferencedAssets);
            switch (op) {
                case LIST:
                    break;
                case EXPORT_ALL:
                    this.export(resourceResolver, referencedAssets);
                    this.export(resourceResolver, unreferencedAssets);
                    break;
                case EXPORT_REFERENCED:
                    this.export(resourceResolver, referencedAssets);
                    break;
                case EXPORT_UNREFERENCED:
                    this.export(resourceResolver, unreferencedAssets);
                    break;
                case REMOVE_UNREFERENCED:
            }
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

    private void export(final ResourceResolver resourceResolver, final Set<String> assetPaths) {
        final File directory = new File(this.directoryName);
        for (final String assetPath : assetPaths) {
            final Resource assetResource = resourceResolver.getResource(assetPath + "/jcr:content");
            if (assetResource == null) {
                LOGGER.error("Asset {} has no jcr:content", assetPath);
                continue;
            }
            final ValueMap valueMap = assetResource.getValueMap();
            final Object jcrData = valueMap.get(JCR_DATA);
            if ((jcrData == null) || !(jcrData instanceof InputStream)) {
                LOGGER.error("Asset {}/jcr:content has no inputstream property {}", assetPath, JCR_DATA);
            } else {
                String subPath = null;
                for (final String rootPath : this.assetPaths) {
                    if (assetPath.startsWith(rootPath)) {
                        subPath = assetPath.substring(rootPath.length() + 1);
                        break;
                    }
                }
                if (subPath == null) {
                    LOGGER.error("Could not obtain sub path of {}", assetPath);
                    break;
                }
                final File assetFile = new File(directory, subPath);
                final File assetDirectory = assetFile.getParentFile();
                assetDirectory.mkdirs();
                try (final BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(assetFile))) {
                    IOUtils.copy((InputStream) jcrData, bos);
                } catch (final IOException e) {
                    LOGGER.error("IOException in export:", e);
                }
            }
        }
    }

    private void findReferences(final ResourceResolver resourceResolver, final Set<String> unreferencedAssets,
                                final Set<String> referencedAssets) {
        for (final String contentRootPath : this.contentPaths) {
            final Resource contentRoot = resourceResolver.getResource(contentRootPath);
            if (contentRoot == null) {
                LOGGER.error("Content path {} not found", contentRootPath);
            } else {
                this.findReferencesRecursively(unreferencedAssets, referencedAssets, contentRoot);
            }
        }
    }

    /**
     * Finds assets in resource properties and recursively in resource children
     *
     * @param assets
     * @param resource
     * @return true only if all assets are found
     */
    private boolean findReferencesRecursively(final Set<String> unreferencedAssets, final Set<String> referencedAssets,
                                              final Resource resource) {
        if (this.checkReferencesInResource(unreferencedAssets, referencedAssets, resource)) {
            return true;
        }
        for (final Resource child : resource.getChildren()) {
            if (this.findReferencesRecursively(unreferencedAssets, referencedAssets, child)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Checks if asset contains a property that references the asset
     *
     * @param assets
     * @param resource
     * @return true only if all assets are found.
     */
    private boolean checkReferencesInResource(final Set<String> unreferencedAssets, final Set<String> referencedAssets,
                                              final Resource resource) {
        final ValueMap valueMap = resource.getValueMap();
        final String resourcePath = resource.getPath();
        if (unreferencedAssets.contains(resourcePath) || referencedAssets.contains(resourcePath)) {
            return unreferencedAssets.isEmpty();
        }
        for (final Map.Entry<String, Object> valueEntry : valueMap.entrySet()) {
            if (unreferencedAssets.isEmpty()) {
                return true;
            }
            final Collection<String> foundAssets = new ArrayList<>();
            String value = null;
            if (valueEntry.getValue() instanceof InputStream) {
                try {
                    value = IOUtils.toString((InputStream) valueEntry.getValue(), UTF_8);
                } catch (final IOException e) {
                    LOGGER.error("Error in checkReferencesInResource:", e);
                    continue;
                }
            } else {
                value = valueEntry.getValue().toString();
            }
            for (final String assetPath : unreferencedAssets) {
                if (value.contains(assetPath)) {
                    foundAssets.add(assetPath);
                }
            }
            referencedAssets.addAll(foundAssets);
            unreferencedAssets.removeAll(foundAssets);
            for (final String assetPath : foundAssets) {
                LOGGER.info("Asset {} referenced by {}", assetPath, resource.getPath());
            }
        }
        return unreferencedAssets.isEmpty();
    }

    private void logAssets(final Set<String> referencedAssets, final Set<String> unreferencedAssets) {
        LOGGER.info("Referenced assets: {}", referencedAssets.size());
        for (final String path : referencedAssets) {
            LOGGER.info("Referenced asset {}", path);
        }
        LOGGER.info("Unreferenced assets: {}", unreferencedAssets.size());
        for (final String path : unreferencedAssets) {
            LOGGER.info("Unreferenced asset {}", path);
        }
        LOGGER.info("Referenced/unreferenced assets: {}/{}", referencedAssets.size(), unreferencedAssets.size());
    }

    private Set<String> findAssets(final ResourceResolver resourceResolver) {
        final Set<String> unreferencedAssets = new TreeSet<>();
        for (final String assetPath : this.assetPaths) {
            final Resource assetResource = resourceResolver.getResource(assetPath);
            if (assetResource == null) {
                LOGGER.error("Asset path {} not found", assetPath);
            } else {
                this.addAssetsRecursively(unreferencedAssets, assetResource);
            }
        }
        return unreferencedAssets;
    }

    private void addAssetsRecursively(final Set<String> unreferencedAssets, final Resource resource) {
        final String resourceName = resource.getName().toLowerCase();
        for (final String suffix : this.assetSuffixes) {
            if (resourceName.endsWith(suffix)) {
                unreferencedAssets.add(resource.getPath());
            }
        }
        for (final Resource child : resource.getChildren()) {
            this.addAssetsRecursively(unreferencedAssets, child);
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
