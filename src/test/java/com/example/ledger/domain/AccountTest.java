package com.example.ledger.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.ledger.exception.BusinessRuleException;
import com.example.ledger.exception.InsufficientFundsException;
import org.junit.jupiter.api.Test;

class AccountTest {

    @Test
    void creditIncreasesAndDebitDecreasesBalance() {
        Account account = new Account("Ann", "USD");

        account.apply(Direction.CREDIT, 1_000);
        account.apply(Direction.DEBIT, 300);

        assertThat(account.getBalance()).isEqualTo(700);
    }

    @Test
    void customerAccountCannotBeOverdrawn() {
        Account account = new Account("Ann", "USD");
        account.apply(Direction.CREDIT, 100);

        assertThatThrownBy(() -> account.apply(Direction.DEBIT, 101))
                .isInstanceOf(InsufficientFundsException.class);
        assertThat(account.getBalance()).isEqualTo(100);
    }

    @Test
    void clearingAccountMayGoNegative() {
        Account clearing = Account.clearing("USD");

        clearing.apply(Direction.DEBIT, 5_000);

        assertThat(clearing.getBalance()).isEqualTo(-5_000);
        assertThat(clearing.isSystemAccount()).isTrue();
    }

    @Test
    void frozenAccountBlocksDebitsButAcceptsCredits() {
        Account account = new Account("Ann", "USD");
        account.apply(Direction.CREDIT, 100);
        account.freeze();

        assertThatThrownBy(() -> account.apply(Direction.DEBIT, 10))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("frozen");
        account.apply(Direction.CREDIT, 10);

        assertThat(account.getBalance()).isEqualTo(110);
    }

    @Test
    void closingRequiresZeroBalanceAndBlocksAllPostings() {
        Account account = new Account("Ann", "USD");
        account.apply(Direction.CREDIT, 100);

        assertThatThrownBy(account::close).isInstanceOf(BusinessRuleException.class);

        account.apply(Direction.DEBIT, 100);
        account.close();

        assertThat(account.getStatus()).isEqualTo(AccountStatus.CLOSED);
        assertThatThrownBy(() -> account.apply(Direction.CREDIT, 1))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("closed");
    }

    @Test
    void systemAccountsCannotBeFrozenOrClosed() {
        Account clearing = Account.clearing("USD");

        assertThatThrownBy(clearing::freeze).isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(clearing::close).isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void balanceOverflowIsRejected() {
        Account account = new Account("Ann", "USD");
        account.apply(Direction.CREDIT, Long.MAX_VALUE);

        assertThatThrownBy(() -> account.apply(Direction.CREDIT, 1))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("overflow");
    }
}
