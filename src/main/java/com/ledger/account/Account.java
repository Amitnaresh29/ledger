  package com.ledger.account;
  import jakarta.persistence.*;   // note: jakarta, not javax (Boot 3 moved namespaces)
  import org.hibernate.annotations.JdbcTypeCode;
  import org.hibernate.type.SqlTypes;
  import java.time.Instant;
  import java.util.UUID;

  @Entity                          // "this class maps to a table"
  @Table(name = "accounts")        // the table name. Without this, Hibernate guesses "account"
  public class Account {

      @Id                          // marks the primary key
      private UUID id;             // NO @GeneratedValue - we generate ids in Java, by design

      @Column(name = "account_type", nullable = false)
      private String accountType;  // camelCase field -> snake_case column, stated explicitly

      @Column(name = "owner_id", nullable = false)
      private UUID ownerId;

      // The DB column is CHAR(3) (Postgres "bpchar"), but Hibernate maps String to
      // VARCHAR by default. This tells it the truth so ddl-auto=validate passes.
      @JdbcTypeCode(SqlTypes.CHAR)
      @Column(nullable = false, length = 3)
      private String currency;     // name omitted: field and column are both "currency"

      // The DB sets this via DEFAULT now(). insertable=false tells Hibernate to leave it
      // out of the INSERT so the default applies; updatable=false because it never changes.
      @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
      private Instant createdAt;

      // JPA REQUIRES a no-arg constructor to build objects from query results.
      // protected, not public: nobody should create a blank Account by accident.
      protected Account() {}

      public Account(UUID id, String accountType, UUID ownerId, String currency) {
          this.id = id;
          this.accountType = accountType;
          this.ownerId = ownerId;
          this.currency = currency;
      }

      // Getters only. No setters: an account's currency and owner must never change
      // after creation - the composite FK we just added enforces that in the DB too.
      public UUID getId() { return id; }
      public String getAccountType() { return accountType; }
      public UUID getOwnerId() { return ownerId; }
      public String getCurrency() { return currency; }
      public Instant getCreatedAt() { return createdAt; }
  }
