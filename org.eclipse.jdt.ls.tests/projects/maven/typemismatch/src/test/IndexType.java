package test;

public enum IndexType {
    INDEX1("Index1"),
    INDEX2("Index2");

    private String name;

    private IndexType(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

}