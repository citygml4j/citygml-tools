module org.citygml4j.tools {
    requires org.citygml4j.core;
    requires org.citygml4j.xml;
    requires org.citygml4j.cityjson;
    requires info.picocli;

    opens org.citygml4j.tools to info.picocli;
    opens org.citygml4j.tools.cli to info.picocli;
    opens org.citygml4j.tools.upgrade to info.picocli;
}