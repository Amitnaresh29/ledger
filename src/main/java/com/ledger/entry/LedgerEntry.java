 package com.ledger.entry;
  import jakarta.persistence.*;   // note: jakarta, not javax (Boot 3 moved namespaces)
  import org.hibernate.annotations.JdbcTypeCode;
  import org.hibernate.type.SqlTypes;
  import java.time.Instant;
import java.util.UUID;


@Entity()
@Table(name="ledger_entries")
public class LedgerEntry {
    @Id
    private UUID id;

    @Column(name = "transaction_id" , nullable = false)
    private UUID transactionId;
    
    @Column(name = "account_id" ,nullable = false)
    private UUID accountId;

    private Long amount;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(nullable = false, length = 3)
    private String currency;

    @Column(name = "created_at",nullable = false , insertable = false,updatable = false)
    private Instant createdAt;

    protected LedgerEntry(){}

    public LedgerEntry(UUID id,UUID transactionId ,UUID accountId,Long amount,String currency){
        this.id = id;
        this.transactionId = transactionId;
        this.accountId = accountId;
        this.amount = amount;
        this.currency = currency;
    }

    public UUID getId() { return id; }
    public UUID getTransactionId(){ return transactionId;}
    public UUID getAccountId() {return accountId;}
    public Long getAmount() {return amount;}
    public String getCurrency() {return currency;}
    public Instant getCreatedAt() { return createdAt; }

}
