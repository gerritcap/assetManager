package be.marble.sling.assetmanager;

import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;
import org.osgi.service.metatype.annotations.Option;

@ObjectClassDefinition(name = "be.marble Asset Manager Configuration",
        description = "Configure the marble.be Asset Manager Service")
public @interface AssetManagerConfiguration {

    @AttributeDefinition(name = "asset.mgr.assetprefix", description = "Service Name")
    String getService() default "assetmgr";

    @AttributeDefinition(name = "asset.mgr.assetpaths", description = "Repository paths where assets can be found")
    String[] getAssetPaths() default { "/content" };

    @AttributeDefinition(name = "asset.mgr.assetsuffixes", description = "Suffixes of assets (use lowercase suffixes!)")
    String[] getAssetPrefixSuffixes() default { "jpg", "png", "gif", "svg", "pdf", "doc", "docx", "xls", "xlsx" };

    @AttributeDefinition(name = "asset.mgr.content",
            description = "Repository paths where content can be found that reference assets")
    String[] getContentPaths() default { "/content" };

    @AttributeDefinition(name = "asset.mgr.directory", description = "Directory where assets are exported")
    String getDirectoryName() default "/tmp";

    @AttributeDefinition(name = "asset.mgr.operation", description = "Operation asset manager executes on run",
            options = { @Option(label = "List unreferenced assets", value = "listunreferenced"),
                        @Option(label = "Remove unreferenced assets", value = "removeunreferenced"),
                        @Option(label = "Export referenced assets", value = "exportreferenced"),
                        @Option(label = "Export unreferenced assets", value = "exportunreferenced") })
    String getOperation() default "listunreferenced";
}
