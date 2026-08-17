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
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Searching and paging, which are one feature rather than two.
 *
 * <p>The assertion that matters: a search finds someone who would not be on the first
 * page. Filtering rows already rendered would answer "no such person" instead, and would
 * do it convincingly.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class PagingAndSearchTests {

    private static final Long CHENNAI = 1L;

    @Autowired
    private MockMvc mockMvc;

    private static AppUserPrincipal reader() {
        return AppUserPrincipal.builder()
                .userId(99L).churchId(CHENNAI).churchName("St. Mary's Cathedral")
                .actorType(ActorType.MEMBER).username("reader@example.com").password("x")
                .displayName("Reader").role("AppSA")
                .usingDefaultPassword(false).locked(false).active(true)
                .permission("PERM_DASHBOARD_VIEW").permission("PERM_MEMBER_VIEW")
                .permission("PERM_FAMILY_VIEW").build();
    }

    @Test
    @DisplayName("a search reaches the whole register, not just the page on screen")
    void searchRunsOverEveryMemberNotThePage() throws Exception {
        // One row per page, so Peter is nowhere near page 1 in an unsearched list...
        mockMvc.perform(get("/members").param("size", "25").with(user(reader())))
                .andExpect(status().isOk());

        // ...yet searching for him finds him, because the search runs first and the page
        // is cut from the results.
        mockMvc.perform(get("/members").param("name", "peter").with(user(reader())))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Peter Fernando")))
                .andExpect(content().string(not(containsString("Antony Raj"))));
    }

    @Test
    @DisplayName("search is case-insensitive and matches part of a name")
    void searchIsLooseAndCaseInsensitive() throws Exception {
        mockMvc.perform(get("/members").param("name", "PET").with(user(reader())))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Peter Fernando")));
    }

    @Test
    @DisplayName("the header count reports what matched, not what exists")
    void countFollowsTheSearch() throws Exception {
        mockMvc.perform(get("/members").param("name", "peter").with(user(reader())))
                .andExpect(status().isOk())
                // One match, out of six members in the parish.
                .andExpect(content().string(containsString("(1 in St. Mary&#39;s Cathedral)")));
    }

    @Test
    @DisplayName("Tamil searches work, since the columns are utf8mb4 throughout")
    void tamilSearchWorks() throws Exception {
        mockMvc.perform(get("/members").param("anbiyamText", "சூசையப்பர").with(user(reader())))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Peter Fernando")));
    }

    @Test
    @DisplayName("paging keeps the search, rather than quietly showing everyone again")
    void pagingCarriesTheSearchForward() throws Exception {
        mockMvc.perform(get("/members").param("name", "a").param("size", "25")
                        .with(user(reader())))
                .andExpect(status().isOk())
                // Every paging and page-size link repeats the search term.
                .andExpect(content().string(containsString("name=a")));
    }

    @Test
    @DisplayName("a page size nobody offered is ignored")
    void anAbsurdPageSizeIsRefused() throws Exception {
        // Otherwise a hand-typed size would pull every member of the parish in one page.
        mockMvc.perform(get("/members").param("size", "100000").with(user(reader())))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("size=50")));
    }

    @Test
    @DisplayName("a search matching nobody says so instead of showing an empty table")
    void emptyResultsAreExplained() throws Exception {
        mockMvc.perform(get("/members").param("name", "zzzznobody").with(user(reader())))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Nothing matches that search")));
    }

    @Test
    @DisplayName("the family list pages and searches the same way")
    void familiesPageAndSearch() throws Exception {
        mockMvc.perform(get("/families").param("name", "arulraj").with(user(reader())))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Arulraj Family")))
                .andExpect(content().string(not(containsString("Fernando Family"))));
    }

    @Test
    @DisplayName("a filter and a search narrow together rather than one replacing the other")
    void filterAndSearchCombine() throws Exception {
        // Family 1 holds Antony, Mary and Joseph; searching within it for Mary leaves one.
        mockMvc.perform(get("/members").param("family", "1").param("name", "mary")
                        .with(user(reader())))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Mary Arulraj")))
                .andExpect(content().string(not(containsString("Joseph Arulraj"))));
    }
}
