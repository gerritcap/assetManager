package be.marble.sling.assetmanager;

public enum Operation {
    LIST("list"),
    REMOVE_UNREFERENCED("removeunreferenced"),
    EXPORT_ALL("exportall"),
    EXPORT_REFERENCED("exportreferenced"),
    EXPORT_UNREFERENCED("exportunreferenced");

    private static final Operation DEFAULT = LIST;

    private String                 optionValue;

    private Operation(final String optionValue) {
        this.optionValue = optionValue;
    }

    public static Operation fromOptionValue(final String optionValue) {
        Operation result = DEFAULT;
        for (final Operation op : Operation.values()) {
            if (op.optionValue.equalsIgnoreCase(optionValue)) {
                result = op;
                break;
            }
        }
        return result;
    }

}
