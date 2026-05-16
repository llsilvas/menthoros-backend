package br.com.menthoros.backend.routing;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class ModelRouterTest {

    private ChatClient gpt4oMiniClient;
    private ChatClient claudeHaikuClient;
    private ChatClient claudeSonnetClient;
    private ChatClient gpt4oClient;
    private ModelRouter router;

    @BeforeEach
    void setUp() {
        gpt4oMiniClient = mock(ChatClient.class);
        claudeHaikuClient = mock(ChatClient.class);
        claudeSonnetClient = mock(ChatClient.class);
        gpt4oClient = mock(ChatClient.class);
        router = new ModelRouter(gpt4oMiniClient, claudeHaikuClient, claudeSonnetClient, gpt4oClient);
    }

    @Test
    void simple_routes_to_gpt4oMini() {
        assertThat(router.route(TaskComplexity.SIMPLE)).isSameAs(gpt4oMiniClient);
    }

    @Test
    void standard_routes_to_claudeHaiku() {
        assertThat(router.route(TaskComplexity.STANDARD)).isSameAs(claudeHaikuClient);
    }

    @Test
    void complex_routes_to_claudeSonnet() {
        assertThat(router.route(TaskComplexity.COMPLEX)).isSameAs(claudeSonnetClient);
    }

    @Test
    void expert_routes_to_gpt4o() {
        assertThat(router.route(TaskComplexity.EXPERT)).isSameAs(gpt4oClient);
    }

    @Test
    void null_complexity_throws_illegal_argument() {
        assertThatThrownBy(() -> router.route(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("null");
    }
}
