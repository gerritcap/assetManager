package be.marble.sling.assetmanager;

public enum Operation {
    LIST_UNREFERENCED("listunreferenced"),
    REMOVE_UNREFERENCED("removeunreferenced"),
    EXPORT_REFERENCED("exportreferenced"),
    EXPORT_UNREFERENCED("exportunreferenced");

    private static final Operation DEFAULT = LIST_UNREFERENCED;

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
