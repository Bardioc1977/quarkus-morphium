package de.caluga.morphium.quarkus.it;

import de.caluga.morphium.annotations.Embedded;
import de.caluga.morphium.annotations.Property;

/**
 * Embedded address document used in embedded-document integration tests.
 */
@Embedded
public class AddressEmbedded {

    @Property(fieldName = "street")
    private String street;

    @Property(fieldName = "city")
    private String city;

    @Property(fieldName = "zip")
    private String zip;

    public String getStreet()              { return street; }
    public void   setStreet(String street) { this.street = street; }
    public String getCity()                { return city; }
    public void   setCity(String city)     { this.city = city; }
    public String getZip()                 { return zip; }
    public void   setZip(String zip)       { this.zip = zip; }
}
