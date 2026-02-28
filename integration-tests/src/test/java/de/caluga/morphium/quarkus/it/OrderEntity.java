package de.caluga.morphium.quarkus.it;

import de.caluga.morphium.annotations.*;
import de.caluga.morphium.annotations.lifecycle.*;
import java.time.LocalDateTime;

/**
 * Test entity used in query and LocalDateTime integration tests.
 */
@Entity(collectionName = "it_orders")
@Lifecycle
public class OrderEntity {

    @Id
    private String id;

    @Property(fieldName = "customer_id")
    private String customerId;

    @Property(fieldName = "amount")
    private double amount;

    @Property(fieldName = "status")
    private String status;

    @Property(fieldName = "created_at")
    private LocalDateTime createdAt;

    @Version
    @Property(fieldName = "version")
    private long version;

    @PreStore
    public void onStore() {
        if (createdAt == null) createdAt = LocalDateTime.now();
    }

    public String getId()                        { return id; }
    public void   setId(String id)               { this.id = id; }
    public String getCustomerId()                { return customerId; }
    public void   setCustomerId(String c)        { this.customerId = c; }
    public double getAmount()                    { return amount; }
    public void   setAmount(double a)            { this.amount = a; }
    public String getStatus()                    { return status; }
    public void   setStatus(String s)            { this.status = s; }
    public LocalDateTime getCreatedAt()          { return createdAt; }
    public void   setCreatedAt(LocalDateTime d)  { this.createdAt = d; }
    public long   getVersion()                   { return version; }
    public void   setVersion(long v)             { this.version = v; }
}
