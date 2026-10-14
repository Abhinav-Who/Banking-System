package bank.service;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BankRulesTest {

    @Test
    void parsesValidAmountsToScaleTwo() {
        assertEquals(new BigDecimal("1500.00"), BankRules.parseAmount("1500"));
        assertEquals(new BigDecimal("1500.50"), BankRules.parseAmount("1500.5"));
        assertEquals(new BigDecimal("0.01"), BankRules.parseAmount("0.01"));
        assertEquals(new BigDecimal("10.00"), BankRules.parseAmount("  10  "));
        assertEquals(2, BankRules.parseAmount("7").scale());
    }

    @Test
    void rejectsInvalidAmounts() {
        for (String bad : new String[]{"", "   ", "0", "0.00", "-5", "1.005", "1e3", "1,000", "abc", "10.", ".5", "₹10"}) {
            assertThrows(BankException.class, () -> BankRules.parseAmount(bad), "should reject '" + bad + "'");
        }
        assertThrows(BankException.class, () -> BankRules.parseAmount(null));
    }

    @Test
    void trailingZerosBeyondTwoDecimalsAreAllowed() {
        // 2 meaningful decimals, just written with more digits.
        assertEquals(new BigDecimal("12.30"), BankRules.parseAmount("12.300"));
    }

    @Test
    void validateAmountRejectsNonPositiveAndTooPrecise() {
        assertThrows(BankException.class, () -> BankRules.validateAmount(BigDecimal.ZERO));
        assertThrows(BankException.class, () -> BankRules.validateAmount(new BigDecimal("-0.01")));
        assertThrows(BankException.class, () -> BankRules.validateAmount(new BigDecimal("0.001")));
        assertThrows(BankException.class, () -> BankRules.validateAmount(null));
        assertThrows(BankException.class, () -> BankRules.validateAmount(new BigDecimal("1000000000000.00")));
    }

    @Test
    void accountNumberMustBeTenDigits() {
        assertEquals("1234567890", BankRules.validateAccountNumber(" 1234567890 "));
        assertThrows(BankException.class, () -> BankRules.validateAccountNumber("123456789"));
        assertThrows(BankException.class, () -> BankRules.validateAccountNumber("12345678901"));
        assertThrows(BankException.class, () -> BankRules.validateAccountNumber("12345abcde"));
        assertThrows(BankException.class, () -> BankRules.validateAccountNumber(""));
    }

    @Test
    void phoneIsNormalised() {
        assertEquals("+919876543210", BankRules.validatePhone("+91 98765-43210"));
        assertThrows(BankException.class, () -> BankRules.validatePhone("12345"));
        assertThrows(BankException.class, () -> BankRules.validatePhone("phone"));
    }

    @Test
    void formatsMoneyIndependentlyOfLocale() {
        assertEquals("1,234,567.50", BankRules.formatMoney(new BigDecimal("1234567.50")));
        assertEquals("-5,000.00", BankRules.formatMoney(new BigDecimal("-5000.00")));
    }
}
