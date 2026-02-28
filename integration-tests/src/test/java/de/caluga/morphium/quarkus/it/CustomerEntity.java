package de.caluga.morphium.quarkus.it;

import de.caluga.morphium.annotations.*;

/**
 * Test entity that contains an {@link AddressEmbedded} sub-document.
 */
@Entity(collectionName = "it_customers")
public class CustomerEntity {

    @Id
    private String id;

    @Property(fieldName = "name")
    private String name;

    @Property(fieldName = "address")
    private AddressEmbedded address;

    public String          getId()                  { return id; }
    public void            setId(String id)         { this.id = id; }
    public String          getName()                { return name; }
    public void            setName(String name)     { this.name = name; }
    public AddressEmbedded getAddress()             { return address; }
    public void            setAddress(AddressEmbedded a) { this.address = a; }
}
