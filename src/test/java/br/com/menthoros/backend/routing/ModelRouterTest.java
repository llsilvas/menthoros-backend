package br.com.menthoros.backend.routing;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class ModelRouterTest {

    private ChatClient simpleClient;
    private ChatClient claudeHaikuClient;
    private ChatClient claudeSonnetClient;
    private ChatClient expertClient;
    private ChatClient planoClient;
    private ModelRouter router;

    @BeforeEach
    void setUp() {
        simpleClient = mock(ChatClient.class);
        claudeHaikuClient = mock(ChatClient.class);
        claudeSonnetClient = mock(ChatClient.class);
        expertClient = mock(ChatClient.class);
        planoClient = mock(ChatClient.class);
        router = new ModelRouter(simpleClient, claudeHaikuClient, claudeSonnetClient, expertClient, planoClient);
    }

    @Test
    void simple_routes_to_simpleClient() {
        assertThat(router.route(TaskComplexity.SIMPLE)).isSameAs(simpleClient);
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
    void expert_routes_to_expertClient() {
        assertThat(router.route(TaskComplexity.EXPERT)).isSameAs(expertClient);
    }

    @Test
    void plano_routes_to_planoClient() {
        assertThat(router.route(TaskComplexity.PLANO)).isSameAs(planoClient);
    }

    @Test
    void null_complexity_throws_illegal_argument() {
        assertThatThrownBy(() -> router.route(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("null");
    }
}
