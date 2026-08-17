package com.church.app.dto;

import com.church.app.entity.PaymentMode;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import org.springframework.format.annotation.DateTimeFormat;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Money received from one family.
 *
 * <p>No church field and no receipt number: the parish comes from the tenant scope and
 * the number is issued by the sequence. Neither is something a browser gets to suggest.
 */
@Getter
@Setter
public class PaymentForm {

    @NotNull(message = "Choose a family")
    private Long familyId;

    @NotNull(message = "Enter the amount received")
    @DecimalMin(value = "0.01", message = "The amount must be more than zero")
    private BigDecimal amount;

    @NotNull(message = "Choose how it was paid")
    private PaymentMode paymentMode = PaymentMode.CASH;

    /** Cheque number, UPI reference, and so on. */
    @Size(max = 50, message = "Reference may be at most 50 characters")
    private String referenceNo;

    /**
     * Left empty this is today. Only an administrator may set it to a past date, and
     * nobody may set it to a future one -- a receipt dated next month is how cash gets
     * moved out of a period about to be counted.
     */
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate receiptDate;

    @Size(max = 500, message = "Remarks may be at most 500 characters")
    private String remarks;
}
