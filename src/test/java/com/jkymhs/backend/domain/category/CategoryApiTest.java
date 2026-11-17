package com.jkymhs.backend.domain.category;

import com.jkymhs.backend.domain.category.service.CategorySeeder;
import com.jkymhs.backend.domain.category.service.CategoryService;
import com.jkymhs.backend.support.IntegrationTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CategoryApiTest extends IntegrationTestSupport {

    @Autowired
    private CategorySeeder categorySeeder;

    @Autowired
    private CategoryService categoryService;

    @BeforeEach
    void setUp() {
        categorySeeder.seed();
    }

    @Test
    @DisplayName("tab이 없으면 장소 6개 → 축제 3개를 주고, 칩은 넣지 않는다")
    void allTabs() throws Exception {
        mockMvc.perform(get("/api/categories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.categories.length()").value(9))
                .andExpect(jsonPath("$.categories[0].code").value("history"))
                .andExpect(jsonPath("$.categories[0].name").value("역사·문화재"))
                .andExpect(jsonPath("$.categories[0].tab").value("place"))
                .andExpect(jsonPath("$.categories[6].code").value("festival"))
                .andExpect(jsonPath("$.categories[6].tab").value("festival"))
                .andExpect(jsonPath("$.categories[*].code", not(hasItem("with_kids"))));
    }

    @Test
    @DisplayName("tab=festival이면 축제 카테고리만 준다")
    void festivalTab() throws Exception {
        mockMvc.perform(get("/api/categories").param("tab", "festival"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.categories.length()").value(3))
                .andExpect(jsonPath("$.categories[*].tab", not(hasItem("place"))));
    }

    @Test
    @DisplayName("모르는 tab 값은 400이다")
    void invalidTab() throws Exception {
        mockMvc.perform(get("/api/categories").param("tab", "food"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("입력값이 올바르지 않습니다."));
    }

    @Test
    @DisplayName("시드를 다시 돌려도 카테고리·매핑이 늘지 않고, 칩 코드로 분류 코드를 찾을 수 있다")
    void seedIsIdempotent() throws Exception {
        categorySeeder.seed();

        mockMvc.perform(get("/api/categories")).andExpect(jsonPath("$.categories.length()").value(9));
        assertThat(categoryService.lclsCodesOf("with_kids")).hasSize(10).contains("VE070100", "EX01");
        assertThat(categoryService.lclsCodesOf("nature")).containsExactlyInAnyOrder("NA", "VE03");
    }
}
