package com.church.app.controller;

import com.church.app.entity.ActorType;
import com.church.app.security.AppUserPrincipal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The screens a volunteer and an administrator actually use.
 *
 * <p>Transactional: receipts written here roll back, so the sample register and its
 * sequence are left where the other tests expect them.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class PaymentScreenTests {

    private static final Long CHENNAI = 1L;
    private static final Long FAM_TWO = 2L;

    @Autowired
    private MockMvc mockMvc;

    private static AppUserPrincipal principal(String role, String... permissions) {
        AppUserPrincipal.Builder builder = AppUserPrincipal.builder()
                .userId(4L).churchId(CHENNAI).churchName("St. Mary's Cathedral")
                .actorType(ActorType.MEMBER).username(role.toLowerCase() + "@example.com")
                .password("x").displayName(role + " Person").role(role)
                .usingDefaultPassword(false).locked(false).active(true);
        for (String permission : permissions) {
            builder.permission(permission);
        }
        return builder.build();
    }

    /** A volunteer: collects and prints, nothing else. */
    private static AppUserPrincipal collector() {
        return principal("AppUser", "PERM_DASHBOARD_VIEW", "PERM_PAYMENT_VIEW", "PERM_PAYMENT_ADD");
    }

    private static AppUserPrincipal superAdmin() {
        return principal("AppSA", "PERM_DASHBOARD_VIEW", "PERM_PAYMENT_VIEW", "PERM_PAYMENT_ADD",
                "PERM_PAYMENT_EDIT", "PERM_PAYMENT_DELETE");
    }

    // ----------------------------------------------------------------- collect

    @Test
    @DisplayName("the collection screen shows what the family owes, oldest first")
    void collectionScreenShowsArrears() throws Exception {
        // FAM-002 owes January, February and March at 300 each.
        mockMvc.perform(get("/payments/collect/{id}", FAM_TWO).with(user(collector())))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Devasagayam Family")))
                .andExpect(content().string(containsString("January 2026")))
                .andExpect(content().string(containsString("900")));
    }

    @Test
    @DisplayName("collecting issues a receipt and lands on it")
    void collectingProducesAReceipt() throws Exception {
        mockMvc.perform(post("/payments/collect").with(user(collector())).with(csrf())
                        .param("familyId", String.valueOf(FAM_TWO))
                        .param("amount", "500.00")
                        .param("paymentMode", "CASH"))
                .andExpect(status().is3xxRedirection())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .redirectedUrlPattern("/payments/*"));
    }

    @Test
    @DisplayName("a receipt says which months the money settled")
    void receiptShowsWhatItSettled() throws Exception {
        String location = mockMvc.perform(post("/payments/collect").with(user(collector())).with(csrf())
                        .param("familyId", String.valueOf(FAM_TWO))
                        .param("amount", "500.00")
                        .param("paymentMode", "CASH"))
                .andReturn().getResponse().getRedirectedUrl();

        mockMvc.perform(get(location).with(user(collector())))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("January 2026")))
                .andExpect(content().string(containsString("February 2026")))
                // Written out in the Indian system, for the slip.
                .andExpect(content().string(containsString("Rupees Five Hundred Only")));
    }

    // ------------------------------------------------------------------- print

    @Test
    @DisplayName("the printable slip carries no navigation and both languages")
    void printSlipIsBareAndBilingual() throws Exception {
        String location = mockMvc.perform(post("/payments/collect").with(user(collector())).with(csrf())
                        .param("familyId", String.valueOf(FAM_TWO))
                        .param("amount", "300.00")
                        .param("paymentMode", "CASH"))
                .andReturn().getResponse().getRedirectedUrl();

        mockMvc.perform(get(location + "/print").with(user(collector())))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("receipt.css")))
                .andExpect(content().string(containsString("ரசீது")))
                .andExpect(content().string(containsString("RECEIPT")))
                .andExpect(content().string(containsString("Rupees Three Hundred Only")))
                // The menu must never reach the printer.
                .andExpect(content().string(not(containsString("topbar"))));
    }

    // -------------------------------------------------------------------- void

    @Test
    @DisplayName("a volunteer cannot void what they collected")
    void collectorsCannotVoid() throws Exception {
        String location = mockMvc.perform(post("/payments/collect").with(user(collector())).with(csrf())
                        .param("familyId", String.valueOf(FAM_TWO))
                        .param("amount", "100.00")
                        .param("paymentMode", "CASH"))
                .andReturn().getResponse().getRedirectedUrl();
        Long id = Long.valueOf(location.substring(location.lastIndexOf('/') + 1));

        mockMvc.perform(post("/payments/{id}/void", id).with(user(collector())).with(csrf())
                        .param("reason", "changed my mind"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("the super admin voids, and the receipt says so afterwards")
    void superAdminCanVoid() throws Exception {
        String location = mockMvc.perform(post("/payments/collect").with(user(superAdmin())).with(csrf())
                        .param("familyId", String.valueOf(FAM_TWO))
                        .param("amount", "100.00")
                        .param("paymentMode", "CASH"))
                .andReturn().getResponse().getRedirectedUrl();
        Long id = Long.valueOf(location.substring(location.lastIndexOf('/') + 1));

        mockMvc.perform(post("/payments/{id}/void", id).with(user(superAdmin())).with(csrf())
                        .param("reason", "Wrong amount entered"))
                .andExpect(status().is3xxRedirection());

        mockMvc.perform(get("/payments/{id}", id).with(user(superAdmin())))
                .andExpect(content().string(containsString("cancelled")))
                .andExpect(content().string(containsString("Wrong amount entered")));
    }

    @Test
    @DisplayName("reissuing cancels the old receipt and cross-references the new one")
    void reissueLinksBothReceipts() throws Exception {
        String location = mockMvc.perform(post("/payments/collect").with(user(superAdmin())).with(csrf())
                        .param("familyId", String.valueOf(FAM_TWO))
                        .param("amount", "500.00")
                        .param("paymentMode", "CASH"))
                .andReturn().getResponse().getRedirectedUrl();
        Long wrongId = Long.valueOf(location.substring(location.lastIndexOf('/') + 1));

        mockMvc.perform(post("/payments/{id}/reissue", wrongId).with(user(superAdmin())).with(csrf())
                        .param("reason", "Wrong amount entered")
                        .param("familyId", String.valueOf(FAM_TWO))
                        .param("amount", "50.00")
                        .param("paymentMode", "CASH"))
                .andExpect(status().is3xxRedirection());

        mockMvc.perform(get("/payments/{id}", wrongId).with(user(superAdmin())))
                .andExpect(content().string(containsString("replaced by")));
    }

    // -------------------------------------------------------------- due runs

    @Test
    @DisplayName("generating dues reports what it did, including what it skipped")
    void dueGenerationReportsItself() throws Exception {
        mockMvc.perform(post("/payments/dues").with(user(collector())).with(csrf())
                        .param("period", "2026-05"))
                .andExpect(status().is3xxRedirection())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .flash().attributeExists("flash"));
    }

    @Test
    @DisplayName("the payments list is refused without permission")
    void listNeedsPermission() throws Exception {
        mockMvc.perform(get("/payments")
                        .with(user(principal("AppUser", "PERM_DASHBOARD_VIEW"))))
                .andExpect(status().isForbidden());
    }
}
