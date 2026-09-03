  package com.ledger.transaction;
  import jakarta.persistence.*;   // note: jakarta, not javax (Boot 3 moved namespaces)
  import java.time.Instant;
  import java.util.UUID;

@Entity
@Table(name = "transactions")
public class LedgerTransaction {
    @Id
    private UUID id;

    @Column(name = "idempotency_key" , nullable = false ,unique = true)
    private String idempotencyKey;

    private String description;

    @Column( name = "created_at",nullable = false , insertable = false,updatable = false)
    private Instant createdAt;

    protected LedgerTransaction(){
    }

    public LedgerTransaction(UUID id,String idempotencyKey,String description){
        this.id =id;
        this.idempotencyKey =idempotencyKey;
        this.description =description;
    }

    public UUID getId() { return id; }
      public String getIdempotencyKey() { return idempotencyKey; }
      public String getDescription() { return description; }
      public Instant getCreatedAt() { return createdAt; }

}
