/** The Vitral desktop client. */
module com.genosha.vitral.client {
    requires javafx.controls;
    requires java.net.http;
    requires java.prefs;
    requires atlantafx.base;
    requires org.kordamp.ikonli.javafx;
    requires org.kordamp.ikonli.feather;
    requires com.fasterxml.jackson.databind;
    requires com.fasterxml.jackson.datatype.jsr310;

    // Jackson builds the API's records by reflection.
    opens com.genosha.vitral.client.api to com.fasterxml.jackson.databind;

    exports com.genosha.vitral.client to javafx.graphics;
}
